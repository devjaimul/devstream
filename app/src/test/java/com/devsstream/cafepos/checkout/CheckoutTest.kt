package com.devsstream.cafepos.checkout

import com.devsstream.cafepos.MemoryAnchorStore
import com.devsstream.cafepos.core.appendixA
import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.core.pricing.priceOrder
import com.devsstream.cafepos.core.tc06Lines
import com.devsstream.cafepos.data.NewPayment
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.inMemoryDb
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.remote.PaymentType
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.data.tc06Sale
import com.devsstream.cafepos.payment.CardPayments
import com.devsstream.cafepos.payment.CardTerminal
import com.devsstream.cafepos.time.ServerClock
import com.devsstream.terminal.LegacyCardTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// The real LegacyCardTerminal, so callbacks come from its own thread in real time.
@RunWith(RobolectricTestRunner::class)
class CheckoutTest {

    private val db = inMemoryDb()
    private val repo = OrderRepository(db)
    private val sdk = LegacyCardTerminal()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val syncRequests = AtomicInteger()
    private val sync = object : SyncScheduler {
        override fun requestSync() {
            syncRequests.incrementAndGet()
        }
    }

    private fun clockAt(time: Instant) =
        ServerClock(MemoryAnchorStore(), { 10_000_000L }, { 0L }, { 1 }).apply { onServerTime(time, 10_000_000L, 10_000_000L) }

    private fun cards(terminal: LegacyCardTerminal = sdk, scope: CoroutineScope = appScope, timeout: Duration = 15.seconds) =
        CardPayments(repo, CardTerminal(terminal, timeout), sync, scope)

    private val at1530 = dhakaTime(2026, 11, 20, 15, 30)
    private val cards = cards()
    private val checkout = Checkout(repo, cards, clockAt(at1530), sync)

    // what the cart showed at 15:30, payable 1,094
    private val shown = priceOrder(tc06Lines, appendixA, "DEVS10", at1530)

    private suspend fun Checkout.card(amount: Long): Long =
        (payCard(tc06Lines, appendixA, "DEVS10", shown, amount) as CheckoutResult.Saved).orderId

    private suspend fun cardAnswered(orderId: Long): OrderWithDetails = withTimeout(20_000) {
        repo.observeOrder(orderId).first { order -> order!!.payments.none { it.status == PaymentStatus.PENDING } }!!
    }

    private suspend fun eventually(check: () -> Boolean) = withTimeout(5_000) {
        while (!check()) delay(10)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    @Test
    fun `TC-10 payable 1094, card 600 approved, cash tendered 500 gives change 6`() = runBlocking {
        val id = checkout.card(bdt(600))

        val afterCard = cardAnswered(id)
        assertEquals(PaymentStatus.APPROVED, afterCard.payments.single().status)
        assertEquals(OrderStatus.PAYING, afterCard.order.status)
        assertEquals(bdt(494), afterCard.due)

        checkout.payRestInCash(id, tendered = bdt(500))

        val paid = repo.order(id)!!
        assertEquals(OrderStatus.PENDING, paid.order.status)
        assertEquals(listOf(PaymentType.CARD, PaymentType.CASH), paid.payments.map { it.type })
        assertEquals(bdt(494), paid.payments[1].amount)
        assertEquals(bdt(6), paid.payments[1].change)
        assertEquals(1, repo.observeOrders().first().size)
        assertTrue(syncRequests.get() > 0)
    }

    @Test
    fun `TC-11 card 1013 is declined, order not paid, cash can still be used`() = runBlocking {
        val id = checkout.card(bdt(1013))

        val declined = cardAnswered(id)
        assertEquals(PaymentStatus.DECLINED, declined.payments.single().status)
        assertEquals("INSUFFICIENT_FUNDS", declined.payments.single().declineReason)
        assertEquals(OrderStatus.PAYING, declined.order.status)
        assertEquals(bdt(1094), declined.due)

        checkout.payRestInCash(id, tendered = bdt(1100))

        val paid = repo.order(id)!!
        assertEquals(OrderStatus.PENDING, paid.order.status)
        assertEquals(bdt(1094), paid.paid)
    }

    @Test
    fun `TC-12 card 1077 never calls back, status says APPROVED, order paid, charged once`() = runBlocking {
        // the real 15 s wait is tested in CardTerminalTest on a virtual clock, 1 s keeps this one quick
        val quick = Checkout(repo, cards(timeout = 1.seconds), clockAt(at1530), sync)
        val id = quick.card(bdt(1077))

        val card = cardAnswered(id).payments.single()
        assertEquals(PaymentStatus.APPROVED, card.status)

        quick.payRestInCash(id, tendered = bdt(20))

        assertEquals(OrderStatus.PENDING, repo.order(id)!!.order.status)
        assertEquals(1, sdk.attempts(card.cardRef))
    }

    @Test
    fun `TC-13 card 1055 calls back twice, still one payment and one order`() = runBlocking {
        val id = checkout.card(bdt(1055))
        cardAnswered(id)
        delay(2_000) // the second callback comes 1 s after the first

        val order = repo.order(id)!!
        assertEquals(1, order.payments.size)
        assertEquals(PaymentStatus.APPROVED, order.payments.single().status)
        assertEquals(1, repo.observeOrders().first().size)
        assertEquals(1, sdk.attempts(order.payments.single().cardRef))
    }

    @Test
    fun `card for the whole payable pays the order and asks for a sync`() = runBlocking {
        val id = checkout.card(bdt(1094))

        val paid = cardAnswered(id)
        assertEquals(OrderStatus.PENDING, paid.order.status)
        assertNotNull(paid.payments.single().authCode)
        eventually { syncRequests.get() > 0 }
    }

    @Test
    fun `cash for everything is one write and the order is paid straight away`() = runBlocking {
        val result = checkout.payCash(tc06Lines, appendixA, "DEVS10", shown, tendered = bdt(1100))

        val order = repo.order((result as CheckoutResult.Saved).orderId)!!
        assertEquals(OrderStatus.PENDING, order.order.status)
        assertEquals(bdt(6), order.payments.single().change)
        assertEquals(1, syncRequests.get())
    }

    @Test
    fun `price changed between the screen and the tap, nothing is saved`() = runBlocking {
        val shownAt1659 = priceOrder(tc06Lines, appendixA, "DEVS10", dhakaTime(2026, 11, 20, 16, 59, 59))
        val at1700 = Checkout(repo, cards, clockAt(dhakaTime(2026, 11, 20, 17, 0)), sync)

        val result = at1700.payCash(tc06Lines, appendixA, "DEVS10", shownAt1659, tendered = bdt(1100))

        assertEquals(bdt(1176), (result as CheckoutResult.PriceChanged).price.payable)
        assertTrue(repo.observeOrders().first().isEmpty())
        assertEquals(0, syncRequests.get())
    }

    @Test
    fun `a cart that no longer fits the catalog is not saved`() = runBlocking {
        // the catalog changed while the cart was open: no extra shots allowed any more
        val tighter = appendixA.copy(groups = appendixA.groups.map { if (it.id == "shots") it.copy(max = 0) else it })
        val shownWithTighter = priceOrder(tc06Lines, tighter, "DEVS10", at1530)

        val result = checkout.payCash(tc06Lines, tighter, "DEVS10", shownWithTighter, tendered = bdt(1100))

        assertTrue(result is CheckoutResult.NotValid)
        assertTrue(repo.observeOrders().first().isEmpty())
    }

    @Test
    fun `no cash, no second card and no cancel while a card result is open`() = runBlocking {
        val id = checkout.card(bdt(1077)) // waits up to 15 s

        assertTrue(runCatching { checkout.payRestInCash(id, bdt(1100)) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { checkout.anotherCard(id, bdt(17)) }.exceptionOrNull() is IllegalStateException)
        assertFalse(checkout.cancel(id))
        assertEquals(1, repo.order(id)!!.payments.size)
    }

    @Test
    fun `TC-17 the screen going away (rotation) doesn't stop the card`() = runBlocking {
        val screen = CoroutineScope(Dispatchers.Default)
        var id = 0L
        screen.launch { id = checkout.card(bdt(600)) }.join()
        screen.cancel() // activity destroyed

        val card = cardAnswered(id).payments.single()
        assertEquals(PaymentStatus.APPROVED, card.status)
        assertEquals(1, sdk.attempts(card.cardRef))
    }

    @Test
    fun `TC-17 process dies while waiting, on restart the card is asked about, not charged again`() = runBlocking {
        val dying = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val id = Checkout(repo, cards(scope = dying), clockAt(at1530), sync).card(bdt(1077))
        val ref = repo.order(id)!!.payments.single().cardRef!!
        eventually { sdk.attempts(ref) == 1 }
        dying.cancel() // the process is gone and the wait with it

        assertEquals(PaymentStatus.PENDING, repo.order(id)!!.payments.single().status)

        // next start: new objects, same database. `sdk` stands for the physical terminal, which still knows the ref
        cards().recover()

        assertEquals(PaymentStatus.APPROVED, repo.order(id)!!.payments.single().status)
        assertEquals(1, sdk.attempts(ref))
    }

    @Test
    fun `card saved but never sent to the terminal comes back unknown, not charged, then a new card`() = runBlocking {
        // the process died right after the PENDING row was saved, before charge() (D3)
        val saved = repo.tc06Sale(payments = listOf(NewPayment.Card(bdt(1094))))
        val oldRef = saved.payments.single().cardRef!!

        cards.recover()
        assertEquals(PaymentStatus.UNKNOWN, repo.order(saved.order.id)!!.payments.single().status)
        assertFalse(checkout.cancel(saved.order.id))

        cards.markNotCharged(oldRef)
        assertEquals(PaymentStatus.VOIDED, repo.order(saved.order.id)!!.payments.single().status)

        checkout.anotherCard(saved.order.id, bdt(1094))

        val paid = cardAnswered(saved.order.id)
        assertEquals(OrderStatus.PENDING, paid.order.status)
        assertEquals(0, sdk.attempts(oldRef))
        assertEquals(1, sdk.attempts(paid.payments.last().cardRef))
    }

    @Test
    fun `not charged asks the terminal first and keeps a charge that went through`() = runBlocking {
        val dying = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val id = Checkout(repo, cards(scope = dying), clockAt(at1530), sync).card(bdt(1077))
        val ref = repo.order(id)!!.payments.single().cardRef!!
        eventually { sdk.attempts(ref) == 1 }
        dying.cancel()

        cards().markNotCharged(ref)

        assertEquals(PaymentStatus.APPROVED, repo.order(id)!!.payments.single().status)
    }
}
