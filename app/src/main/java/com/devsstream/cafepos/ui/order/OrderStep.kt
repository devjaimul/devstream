package com.devsstream.cafepos.ui.order

import com.devsstream.cafepos.data.MAX_SYNC_ATTEMPTS
import com.devsstream.cafepos.data.local.OrderEntity
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentEntity
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.remote.PaymentType

// What the cashier sees on the order screen (D2), worked out only from Room
// plus which cards this process is still waiting on.
sealed interface PaymentStep {
    data object WaitingForCard : PaymentStep

    // PENDING but nobody is waiting: the app restarted and is asking the terminal
    data class CheckingCard(val ref: String) : PaymentStep

    data class CardUnknown(val ref: String) : PaymentStep

    // nothing open, money still due. lastCard is the last card attempt, if there was one
    data class Due(val amount: Long, val lastCard: PaymentEntity?) : PaymentStep

    data object Paid : PaymentStep

    data object Cancelled : PaymentStep
}

fun paymentStep(details: OrderWithDetails, waiting: Set<String>): PaymentStep {
    when (details.order.status) {
        OrderStatus.CANCELLED -> return PaymentStep.Cancelled
        OrderStatus.PENDING, OrderStatus.SYNCED, OrderStatus.FAILED -> return PaymentStep.Paid
        OrderStatus.PAYING -> Unit
    }
    val open = details.openCard
    val ref = open?.cardRef
    return when {
        open == null || ref == null -> PaymentStep.Due(details.due, details.payments.lastOrNull { it.type == PaymentType.CARD })
        open.status == PaymentStatus.UNKNOWN -> PaymentStep.CardUnknown(ref)
        ref in waiting -> PaymentStep.WaitingForCard
        else -> PaymentStep.CheckingCard(ref)
    }
}

fun syncText(order: OrderEntity): String = when (order.status) {
    OrderStatus.PAYING -> "Not paid yet"
    OrderStatus.PENDING ->
        if (order.syncAttempts == 0) "Paid, not synced yet"
        else "Paid, not synced: ${order.syncAttempts} of $MAX_SYNC_ATTEMPTS tries failed (${order.lastError})"
    OrderStatus.SYNCED -> "Synced, server order #${order.serverOrderNo}"
    OrderStatus.FAILED -> "Sync failed after $MAX_SYNC_ATTEMPTS tries (${order.lastError}). Nothing is lost, tap Retry."
    OrderStatus.CANCELLED -> "Cancelled, never sent"
}
