package com.devsstream.cafepos.data

import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.payment.CardResult
import com.devsstream.cafepos.time.ServerNow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class OrderRepositoryTest {

    private val db = inMemoryDb()
    private val repo = OrderRepository(db)

    @After
    fun tearDown() = db.close()

    @Test
    fun `cash sale is saved with its lines and payment and is PENDING`() = runBlocking {
        val saved = repo.tc06Sale()

        val order = saved.order
        assertEquals(OrderStatus.PENDING, order.status)
        assertEquals("T1-20261120-0001", order.receiptNo)
        assertEquals("2026-11-20", order.businessDay)
        assertTrue(order.timeConfirmed)
        assertEquals(1, order.catalogVersion)
        assertEquals(mapOf("P1" to bdt(120), "P2" to 8_200L), order.promotions)
        assertEquals("C1", order.discountId)
        assertEquals(11_580L, order.discountAmount)
        assertEquals(5_211L, order.vat)
        assertEquals(bdt(1094), order.payable)

        assertEquals(listOf("latte", "croissant", "brownie"), saved.lines.map { it.productId })
        assertEquals(mapOf("oat" to 1, "extra_shot" to 1, "vanilla" to 1), saved.lines[0].options)
        assertEquals("large", saved.lines[0].sizeId)

        val cash = saved.payments.single()
        assertEquals(PaymentStatus.APPROVED, cash.status)
        assertEquals(bdt(6), cash.change)
    }

    @Test
    fun `receipt numbers count up per day and start again the next day`() = runBlocking {
        val first = repo.tc06Sale().order.receiptNo
        val second = repo.tc06Sale().order.receiptNo
        val nextDay = repo.tc06Sale(ServerNow(dhakaTime(2026, 11, 21, 9, 0), confirmed = true)).order.receiptNo

        assertEquals("T1-20261120-0001", first)
        assertEquals("T1-20261120-0002", second)
        assertEquals("T1-20261121-0001", nextDay)
    }

    @Test
    fun `business day is the Dhaka date, not the UTC one`() = runBlocking {
        // 18:30 UTC on the 20th is 00:30 on the 21st in Dhaka
        val order = repo.tc06Sale(ServerNow(Instant.parse("2026-11-20T18:30:00Z"), confirmed = true)).order

        assertEquals("2026-11-21", order.businessDay)
        assertEquals("T1-20261121-0001", order.receiptNo)
    }

    @Test
    fun `unconfirmed server time is kept on the order`() = runBlocking {
        val order = repo.tc06Sale(ServerNow(dhakaTime(2026, 11, 20, 15, 30), confirmed = false)).order

        assertFalse(order.timeConfirmed)
    }

    @Test
    fun `card sale stays PAYING until the card is approved`() = runBlocking {
        val saved = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(1094))))
        assertEquals(OrderStatus.PAYING, saved.order.status)

        val card = saved.payments.single()
        assertEquals(PaymentStatus.PENDING, card.status)
        assertNotNull("ref is saved before the terminal is called", card.cardRef)

        repo.finishCardPayment(card.cardRef!!, CardResult.Approved("AUTH-1"))

        val after = repo.order(saved.order.id)!!
        assertEquals(OrderStatus.PENDING, after.order.status)
        assertEquals("AUTH-1", after.payments.single().authCode)
    }

    @Test
    fun `declined card leaves the order unpaid and cash can finish it`() = runBlocking {
        val saved = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(1094))))
        repo.finishCardPayment(saved.payments.single().cardRef!!, CardResult.Declined("INSUFFICIENT_FUNDS"))

        val declined = repo.order(saved.order.id)!!
        assertEquals(OrderStatus.PAYING, declined.order.status)
        assertEquals("INSUFFICIENT_FUNDS", declined.payments.single().declineReason)

        repo.addPayment(saved.order.id, NewPayment.Cash(bdt(1094), bdt(1100)))

        val paid = repo.order(saved.order.id)!!
        assertEquals(OrderStatus.PENDING, paid.order.status)
        assertEquals(listOf(PaymentStatus.DECLINED, PaymentStatus.APPROVED), paid.payments.map { it.status })
    }

    @Test
    fun `card plus cash pays the order only when both are in`() = runBlocking {
        val saved = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(600))))
        repo.finishCardPayment(saved.payments.single().cardRef!!, CardResult.Approved("AUTH-2"))
        assertEquals(OrderStatus.PAYING, repo.order(saved.order.id)!!.order.status)

        val cash = repo.addPayment(saved.order.id, NewPayment.Cash(bdt(494), tendered = bdt(500)))

        assertEquals(bdt(6), cash.change)
        val paid = repo.order(saved.order.id)!!
        assertEquals(OrderStatus.PENDING, paid.order.status)
        assertEquals(2, paid.payments.size)
    }

    @Test
    fun `cancelled sale stays visible and its receipt number is not reused`() = runBlocking {
        val unpaid = repo.tc06Sale(payments = emptyList())
        assertTrue(repo.cancel(unpaid.order.id))

        val next = repo.tc06Sale()

        assertEquals("T1-20261120-0002", next.order.receiptNo)
        val all = repo.observeOrders().first()
        assertEquals(2, all.size)
        assertEquals(OrderStatus.CANCELLED, all.single { it.order.id == unpaid.order.id }.order.status)
    }

    @Test
    fun `cancel is refused while a card result is open or money was taken`() = runBlocking {
        val pending = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(1094))))
        assertFalse(repo.cancel(pending.order.id))

        repo.finishCardPayment(pending.payments.single().cardRef!!, CardResult.Unknown)
        assertFalse(repo.cancel(pending.order.id))

        val partlyPaid = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(600))))
        repo.finishCardPayment(partlyPaid.payments.single().cardRef!!, CardResult.Approved("AUTH-3"))
        assertFalse(repo.cancel(partlyPaid.order.id))

        val paid = repo.tc06Sale()
        assertFalse(repo.cancel(paid.order.id))

        assertEquals(OrderStatus.PAYING, repo.order(pending.order.id)!!.order.status)
        assertEquals(OrderStatus.PAYING, repo.order(partlyPaid.order.id)!!.order.status)
        assertEquals(OrderStatus.PENDING, repo.order(paid.order.id)!!.order.status)
    }

    @Test
    fun `5th failed upload makes it FAILED, Retry puts it back with nothing lost`() = runBlocking {
        val id = repo.tc06Sale().order.id

        repeat(4) { assertEquals(OrderStatus.PENDING, repo.syncFailed(id, "no network")) }
        assertEquals(OrderStatus.FAILED, repo.syncFailed(id, "HTTP 500"))

        val failed = repo.order(id)!!
        assertEquals(5, failed.order.syncAttempts)
        assertEquals("HTTP 500", failed.order.lastError)
        assertTrue(repo.ordersToSync().isEmpty())

        repo.retry(id)

        val retried = repo.order(id)!!
        assertEquals(OrderStatus.PENDING, retried.order.status)
        assertEquals(0, retried.order.syncAttempts)
        assertEquals(3, retried.lines.size)
        assertEquals(1, retried.payments.size)
        assertEquals(listOf(id), repo.ordersToSync().map { it.order.id })
    }

    @Test
    fun `orders to sync are the paid ones, oldest first`() = runBlocking {
        val a = repo.tc06Sale().order.id
        repo.tc06Sale(payments = emptyList()) // still paying
        val b = repo.tc06Sale().order.id
        val c = repo.tc06Sale().order.id
        repo.markSynced(b, serverOrderNo = 1)

        assertEquals(listOf(a, c), repo.ordersToSync().map { it.order.id })
        assertEquals(1L, repo.order(b)!!.order.serverOrderNo)
        assertEquals(OrderStatus.SYNCED, repo.order(b)!!.order.status)
    }

    @Test
    fun `a write that fails halfway leaves nothing behind`() = runBlocking {
        // order and lines are inserted, then the cash payment is rejected
        val error = runCatching { repo.tc06Sale(payments = listOf(NewPayment.Cash(bdt(1094), tendered = bdt(1000)))) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)

        assertTrue(repo.observeOrders().first().isEmpty())
        assertEquals("T1-20261120-0001", repo.tc06Sale().order.receiptNo)
    }
}
