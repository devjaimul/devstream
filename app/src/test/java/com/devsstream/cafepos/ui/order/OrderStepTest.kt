package com.devsstream.cafepos.ui.order

import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.data.local.OrderEntity
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentEntity
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.remote.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// D2: what the cashier sees for each state of the order and its payments.
class OrderStepTest {

    private fun order(status: OrderStatus) = OrderEntity(
        id = 1, clientOrderId = "c-1", receiptNo = "T1-20261120-0001", businessDay = "2026-11-20", receiptSeq = 1,
        createdAt = 0, timeConfirmed = true, catalogVersion = 1, coupon = null, couponRejection = null,
        subtotal = bdt(1042), promotions = emptyMap(), discountId = null, discountAmount = 0,
        vat = 5_210, total = 109_410, payable = bdt(1094), status = status,
    )

    private fun card(id: Long, status: PaymentStatus, amount: Long = bdt(1094), reason: String? = null) =
        PaymentEntity(id, orderId = 1, type = PaymentType.CARD, status = status, amount = amount, cardRef = "ref-$id", declineReason = reason)

    private fun details(status: OrderStatus, vararg payments: PaymentEntity) =
        OrderWithDetails(order(status), emptyList(), payments.toList())

    @Test
    fun `card this process is waiting on shows the spinner`() {
        val step = paymentStep(details(OrderStatus.PAYING, card(1, PaymentStatus.PENDING)), waiting = setOf("ref-1"))
        assertEquals(PaymentStep.WaitingForCard, step)
    }

    @Test
    fun `PENDING card nobody waits on after a restart is checked, never offered as due`() {
        val step = paymentStep(details(OrderStatus.PAYING, card(1, PaymentStatus.PENDING)), waiting = emptySet())
        assertEquals(PaymentStep.CheckingCard("ref-1"), step)
    }

    @Test
    fun `unknown card result asks the cashier`() {
        val step = paymentStep(details(OrderStatus.PAYING, card(1, PaymentStatus.UNKNOWN)), waiting = emptySet())
        assertEquals(PaymentStep.CardUnknown("ref-1"), step)
    }

    @Test
    fun `declined card leaves the full amount due`() {
        val declined = card(1, PaymentStatus.DECLINED, bdt(1013), "INSUFFICIENT_FUNDS")
        val step = paymentStep(details(OrderStatus.PAYING, declined), waiting = emptySet())
        assertEquals(PaymentStep.Due(bdt(1094), declined), step)
    }

    @Test
    fun `split with the card part approved leaves the rest due`() {
        val approved = card(1, PaymentStatus.APPROVED, bdt(600))
        val step = paymentStep(details(OrderStatus.PAYING, approved), waiting = emptySet())
        assertEquals(PaymentStep.Due(bdt(494), approved), step)
    }

    @Test
    fun `paid, synced and failed orders all show as paid, cancelled as cancelled`() {
        for (status in listOf(OrderStatus.PENDING, OrderStatus.SYNCED, OrderStatus.FAILED)) {
            assertEquals(PaymentStep.Paid, paymentStep(details(status), waiting = emptySet()))
        }
        assertEquals(PaymentStep.Cancelled, paymentStep(details(OrderStatus.CANCELLED), waiting = emptySet()))
    }

    @Test
    fun `sync text shows the server number, the tries and Retry`() {
        assertEquals("Synced, server order #7", syncText(order(OrderStatus.SYNCED).copy(serverOrderNo = 7)))
        val failed = syncText(order(OrderStatus.FAILED).copy(syncAttempts = 5, lastError = "no network"))
        assertTrue(failed, "5 tries" in failed && "Retry" in failed)
        assertEquals(
            "Paid, not synced: 2 of 5 tries failed (HTTP 500)",
            syncText(order(OrderStatus.PENDING).copy(syncAttempts = 2, lastError = "HTTP 500")),
        )
    }
}
