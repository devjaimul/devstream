package com.devsstream.cafepos.summary

import com.devsstream.cafepos.MemoryCatalogCache
import com.devsstream.cafepos.MemoryServerStore
import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.data.NewPayment
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.inMemoryDb
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import com.devsstream.cafepos.data.sync.OrderSync
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.data.tc06Sale
import com.devsstream.cafepos.payment.CardResult
import com.devsstream.cafepos.time.ServerNow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class DaySummaryTest {

    private val db = inMemoryDb()
    private val repo = OrderRepository(db)
    private val server = FakePosServer(
        MemoryServerStore(),
        elapsedMs = { 10_000_000L },
        wallMs = { dhakaTime(2026, 11, 20, 20, 0).toEpochMilli() },
        latencyMs = 0L..0L,
    ).apply { failures = Failures.OFF }
    private val catalogs = CatalogRepository(server, MemoryCatalogCache())
    private val syncRequests = AtomicInteger()
    private val seed = SeedLoader(repo, catalogs, object : SyncScheduler {
        override fun requestSync() {
            syncRequests.incrementAndGet()
        }
    })

    private val nov20 = LocalDate.of(2026, 11, 20)

    @Before
    fun setUp() = runBlocking {
        assertTrue(catalogs.refresh())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun summary(day: LocalDate = nov20) = summarize(day, repo.observeDay(day).first())

    private fun OrderWithDetails.seedRef() = order.clientOrderId.removePrefix("appendix-b-")

    @Test
    fun `TC-18 summary for 20 Nov with Appendix B loaded`() = runBlocking {
        assertEquals(SeedResult.Loaded(nov20, added = 6), seed.loadAppendixB())

        // worked out by hand, same numbers as ANSWERS.md Q1 to Q3
        assertEquals(
            DaySummary(
                day = nov20,
                orders = 6,
                payable = bdt(4914),
                cash = bdt(2325),
                card = bdt(2589),
                topItems = listOf("croissant"),
                topUnits = 9,
                notSynced = 6,
            ),
            summary(),
        )
        assertEquals(1, syncRequests.get())
    }

    @Test
    fun `each Appendix B order has the expected payable and receipt numbers follow server time`() = runBlocking {
        seed.loadAppendixB()

        val orders = repo.observeDay(nov20).first()
        assertEquals(
            listOf("O1" to bdt(347), "O5" to bdt(520), "O2" to bdt(876), "O6" to bdt(970), "O3" to bdt(988), "O4" to bdt(1213)),
            orders.map { it.seedRef() to it.order.payable },
        )
        assertEquals((1..6).map { "T1-20261120-000$it" }, orders.map { it.order.receiptNo })

        val o3 = orders.single { it.seedRef() == "O3" }
        assertEquals(listOf(bdt(500), bdt(488)), o3.payments.sortedBy { it.id }.map { it.amount })
    }

    @Test
    fun `loading the seed twice adds nothing`() = runBlocking {
        seed.loadAppendixB()
        assertEquals(SeedResult.Loaded(nov20, added = 0), seed.loadAppendixB())

        assertEquals(6, summary().orders)
        assertEquals(1, syncRequests.get())
    }

    @Test
    fun `seed is refused on a different catalog version`() = runBlocking {
        server.bumpCatalog()
        catalogs.refresh()

        assertEquals(SeedResult.WrongCatalog(needed = 1, current = 2), seed.loadAppendixB())
        assertEquals(0, summary().orders)
    }

    @Test
    fun `summary Flow updates as soon as a sale is saved`() = runBlocking {
        val live = repo.observeDay(nov20).map { summarize(nov20, it) }
        assertEquals(0, live.first().orders)

        repo.tc06Sale()

        val after = withTimeout(5_000) { live.first { it.orders == 1 } }
        assertEquals(bdt(1094), after.payable)
        assertEquals(bdt(1094), after.cash) // 1,100 handed over, 6 given back
    }

    @Test
    fun `not synced goes to 0 once the orders are uploaded`() = runBlocking {
        seed.loadAppendixB()
        OrderSync(repo, server).run()

        assertEquals(0, summary().notSynced)
        assertEquals(6, server.orders().size)
        assertEquals(bdt(4914), server.orders().sumOf { it.payablePoisha })
    }

    @Test
    fun `unpaid and cancelled orders and declined cards don't count`() = runBlocking {
        repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(600)))) // card still pending
        val cancelled = repo.tc06Sale(payments = emptyList()).order.id
        repo.cancel(cancelled)

        val declinedThenCash = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(1094))))
        repo.finishCardPayment(declinedThenCash.payments.single().cardRef!!, CardResult.Declined("INSUFFICIENT_FUNDS"))
        repo.addPayment(declinedThenCash.order.id, NewPayment.Cash(bdt(1094), bdt(1094)))

        val s = summary()
        assertEquals(1, s.orders)
        assertEquals(bdt(1094), s.payable)
        assertEquals(bdt(1094), s.cash)
        assertEquals(0L, s.card)
    }

    @Test
    fun `other business days are not included`() = runBlocking {
        seed.loadAppendixB()
        // midnight in Dhaka already belongs to the 21st. No happy hour then, so it's 1,176
        repo.tc06Sale(
            ServerNow(dhakaTime(2026, 11, 21, 0, 0), confirmed = true),
            payments = listOf(NewPayment.Cash(bdt(1176), bdt(1200))),
        )

        assertEquals(6, summary().orders)
        assertEquals(1, summary(nov20.plusDays(1)).orders)
    }

    @Test
    fun `an empty day has no top item`() = runBlocking {
        val s = summary(LocalDate.of(2026, 11, 19))

        assertEquals(0, s.orders)
        assertEquals(emptyList<String>(), s.topItems)
        assertEquals(0, s.topUnits)
    }

    @Test
    fun `a tie for top item shows every tied product`() = runBlocking {
        repo.tc06Sale() // latte x2, croissant x2, brownie x2

        val s = summary()
        assertEquals(listOf("brownie", "croissant", "latte"), s.topItems)
        assertEquals(2, s.topUnits)
    }
}
