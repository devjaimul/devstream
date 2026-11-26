package com.devsstream.cafepos.data.sync

import com.devsstream.cafepos.MemoryServerStore
import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.data.NewPayment
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.inMemoryDb
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.remote.AmountDto
import com.devsstream.cafepos.data.remote.OptionQuantityDto
import com.devsstream.cafepos.data.remote.OrderDto
import com.devsstream.cafepos.data.remote.PaymentDto
import com.devsstream.cafepos.data.remote.PaymentType
import com.devsstream.cafepos.data.remote.PosApi
import com.devsstream.cafepos.data.remote.PostOrderResponse
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import com.devsstream.cafepos.data.tc06Sale
import com.devsstream.cafepos.payment.CardResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class OrderSyncTest {

    private val db = inMemoryDb()
    private val repo = OrderRepository(db)

    // no latency here, the 300-2000 ms is tested in FakePosServerTest
    private val server = FakePosServer(
        MemoryServerStore(),
        elapsedMs = { 10_000_000L },
        wallMs = { dhakaTime(2026, 11, 20, 15, 27, 30).toEpochMilli() },
        random = Random(7),
        latencyMs = 0L..0L,
    ).apply { failures = Failures.OFF }

    @After
    fun tearDown() = db.close()

    @Test
    fun `TC-14 three sales with no network are saved at once and sent in creation order`() = runBlocking {
        // no network: WorkManager doesn't run the sync at all (see SyncWorkerTest)
        val ids = List(3) { repo.tc06Sale().order.id }
        assertEquals(ids, repo.ordersToSync().map { it.order.id })
        assertTrue(server.orders().isEmpty())

        // network is back
        assertEquals(SyncOutcome.DONE, OrderSync(repo, server).run())

        val onServer = server.orders()
        assertEquals(listOf("T1-20261120-0001", "T1-20261120-0002", "T1-20261120-0003"), onServer.map { it.receiptNo })
        ids.forEachIndexed { i, id ->
            val order = repo.order(id)!!.order
            assertEquals(OrderStatus.SYNCED, order.status)
            assertEquals(i + 1L, order.serverOrderNo)
        }
    }

    @Test
    fun `TC-15 server saved the order, app killed before the response, retry is a duplicate`() = runBlocking {
        val saved = repo.tc06Sale().order
        val killedAfterSend = object : PosApi by server {
            override suspend fun postOrder(order: OrderDto): PostOrderResponse {
                server.postOrder(order)
                throw CancellationException("app killed before reading the response")
            }
        }
        val killed = runCatching { OrderSync(repo, killedAfterSend).run() }.exceptionOrNull()
        assertTrue(killed is CancellationException)

        // nothing after the POST ran: still PENDING, no failure counted
        assertEquals(OrderStatus.PENDING, repo.order(saved.id)!!.order.status)
        assertEquals(0, repo.order(saved.id)!!.order.syncAttempts)
        assertEquals(1, server.orders().size)

        // relaunch: same database, fresh objects
        val responses = mutableListOf<PostOrderResponse>()
        val recording = object : PosApi by server {
            override suspend fun postOrder(order: OrderDto) = server.postOrder(order).also { responses += it }
        }
        assertEquals(SyncOutcome.DONE, OrderSync(OrderRepository(db), recording).run())

        assertTrue(responses.single().duplicate)
        assertEquals(1, server.orders().size)
        val synced = repo.order(saved.id)!!.order
        assertEquals(OrderStatus.SYNCED, synced.status)
        assertEquals(1L, synced.serverOrderNo)
    }

    @Test
    fun `TC-16 upload fails 5 times, order is FAILED and visible, Retry works, nothing lost`() = runBlocking {
        val id = repo.tc06Sale().order.id
        val sync = OrderSync(repo, server)
        server.failures = Failures.NETWORK_DOWN

        repeat(4) { assertEquals(SyncOutcome.RETRY_LATER, sync.run()) }
        assertEquals(OrderStatus.PENDING, repo.order(id)!!.order.status)

        // 5th failure: the order gives up, nothing else is queued so the run is done
        assertEquals(SyncOutcome.DONE, sync.run())
        val failed = repo.order(id)!!
        assertEquals(OrderStatus.FAILED, failed.order.status)
        assertEquals(5, failed.order.syncAttempts)
        assertTrue(repo.observeOrders().first().any { it.order.id == id })

        server.failures = Failures.OFF
        repo.retry(id)
        assertEquals(SyncOutcome.DONE, sync.run())

        val synced = repo.order(id)!!
        assertEquals(OrderStatus.SYNCED, synced.order.status)
        assertEquals(3, synced.lines.size)
        assertEquals(1, synced.payments.size)
        assertEquals(bdt(1094), server.orders().single().payablePoisha)
    }

    @Test
    fun `a failing order holds back the ones behind it until it is FAILED`() = runBlocking {
        val stuck = repo.tc06Sale().order
        val next = repo.tc06Sale().order
        val api = object : PosApi by server {
            override suspend fun postOrder(order: OrderDto) =
                if (order.clientOrderId == stuck.clientOrderId) throw IOException("connection reset")
                else server.postOrder(order)
        }
        val sync = OrderSync(repo, api)

        repeat(4) {
            assertEquals(SyncOutcome.RETRY_LATER, sync.run())
            assertTrue("nothing goes out of order", server.orders().isEmpty())
        }
        assertEquals(SyncOutcome.DONE, sync.run())

        assertEquals(OrderStatus.FAILED, repo.order(stuck.id)!!.order.status)
        assertEquals(OrderStatus.SYNCED, repo.order(next.id)!!.order.status)
        assertEquals(listOf(next.receiptNo), server.orders().map { it.receiptNo })
    }

    @Test
    fun `500 after the save counts as a failure and the next try is a duplicate`() = runBlocking {
        val id = repo.tc06Sale().order.id
        val sync = OrderSync(repo, server)

        server.failures = Failures.ERROR_AFTER_SAVE
        assertEquals(SyncOutcome.RETRY_LATER, sync.run())
        assertEquals(1, repo.order(id)!!.order.syncAttempts)
        assertTrue(repo.order(id)!!.order.lastError!!.startsWith("HTTP 500"))

        server.failures = Failures.OFF
        assertEquals(SyncOutcome.DONE, sync.run())

        assertEquals(OrderStatus.SYNCED, repo.order(id)!!.order.status)
        assertEquals(1, server.orders().size)
    }

    @Test
    fun `unpaid and cancelled orders are never sent`() = runBlocking {
        repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(1094)))) // waiting for the card
        val cancelled = repo.tc06Sale(payments = emptyList()).order.id
        repo.cancel(cancelled)
        val paid = repo.tc06Sale().order

        assertEquals(SyncOutcome.DONE, OrderSync(repo, server).run())

        assertEquals(listOf(paid.receiptNo), server.orders().map { it.receiptNo })
    }

    @Test
    fun `server gets the order as it was saved, with only the payments that paid it`() = runBlocking {
        val saved = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(1094))))
        repo.finishCardPayment(saved.payments.single().cardRef!!, CardResult.Declined("INSUFFICIENT_FUNDS"))
        repo.addPayment(saved.order.id, NewPayment.Cash(bdt(1094), bdt(1100)))

        OrderSync(repo, server).run()

        val sent = server.orders().single()
        assertEquals(saved.order.clientOrderId, sent.clientOrderId)
        assertEquals("2026-11-20T09:30:00Z", sent.createdAt)
        assertEquals("2026-11-20", sent.businessDay)
        assertTrue(sent.timeConfirmed)
        assertEquals(1, sent.catalogVersion)
        assertEquals("DEVS10", sent.coupon)
        assertEquals(listOf("latte", "croissant", "brownie"), sent.lines.map { it.productId })
        assertEquals(
            listOf(OptionQuantityDto("oat", 1), OptionQuantityDto("extra_shot", 1), OptionQuantityDto("vanilla", 1)),
            sent.lines[0].options,
        )
        assertEquals(listOf(AmountDto("P1", bdt(120)), AmountDto("P2", 8_200)), sent.promotions)
        assertEquals(115_800L, sent.subtotalPoisha)
        assertEquals(AmountDto("C1", 11_580), sent.discount)
        assertEquals(5_211L, sent.vatPoisha)
        assertEquals(bdt(1094), sent.payablePoisha)
        assertEquals(
            listOf(PaymentDto(PaymentType.CASH, bdt(1094), tenderedPoisha = bdt(1100), changePoisha = bdt(6))),
            sent.payments,
        )
    }
}
