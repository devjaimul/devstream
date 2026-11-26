package com.devsstream.cafepos.data

import androidx.room.withTransaction
import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.order.receiptNumber
import com.devsstream.cafepos.core.pricing.OrderLine
import com.devsstream.cafepos.core.pricing.PriceBreakdown
import com.devsstream.cafepos.core.time.businessDay
import com.devsstream.cafepos.data.local.OrderEntity
import com.devsstream.cafepos.data.local.OrderLineEntity
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OPEN_CARD
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentEntity
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.local.PosDatabase
import com.devsstream.cafepos.data.remote.PaymentType
import com.devsstream.cafepos.payment.CardResult
import com.devsstream.cafepos.time.ServerNow
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// this till's id in receipt numbers, nothing to do with the T1 discount
const val TILL_ID = "T1"
const val MAX_SYNC_ATTEMPTS = 5

sealed interface NewPayment {
    val amount: Long

    data class Cash(override val amount: Long, val tendered: Long) : NewPayment

    // gets a fresh ref and starts PENDING, the terminal is only called after this is saved (D3)
    data class Card(override val amount: Long) : NewPayment
}

@Singleton
class OrderRepository @Inject constructor(private val db: PosDatabase) {

    private val dao = db.orderDao()

    fun observeOrders(): Flow<List<OrderWithDetails>> = dao.observeAll()

    fun observeOrder(id: Long): Flow<OrderWithDetails?> = dao.observe(id)

    suspend fun order(id: Long): OrderWithDetails? = dao.details(id)

    // Order, lines, first payments and the receipt number in one transaction.
    // `now` must be the same server time the order was priced at.
    suspend fun createOrder(
        lines: List<OrderLine>,
        price: PriceBreakdown,
        coupon: String?,
        catalog: Catalog,
        now: ServerNow,
        payments: List<NewPayment> = emptyList(),
    ): OrderWithDetails = db.withTransaction {
        require(lines.size == price.lines.size) { "lines and priced lines don't match" }
        require(payments.sumOf { it.amount } <= price.payable) { "payments are more than the payable" }

        val day = now.time.businessDay(catalog.timeZone)
        val seq = (dao.lastReceiptSeq(day.toString()) ?: 0) + 1
        val orderId = dao.insert(
            OrderEntity(
                clientOrderId = UUID.randomUUID().toString(),
                receiptNo = receiptNumber(TILL_ID, day, seq),
                businessDay = day.toString(),
                receiptSeq = seq,
                createdAt = now.time.toEpochMilli(),
                timeConfirmed = now.confirmed,
                catalogVersion = catalog.version,
                coupon = coupon?.trim()?.takeIf { it.isNotEmpty() },
                couponRejection = price.couponRejection,
                subtotal = price.subtotal,
                promotions = price.promotions.associate { it.id to it.amount },
                discountId = price.discount?.id,
                discountAmount = price.discount?.amount ?: 0,
                vat = price.vat,
                total = price.total,
                payable = price.payable,
                status = OrderStatus.PAYING,
            )
        )
        dao.insertLines(
            lines.zip(price.lines) { line, priced ->
                OrderLineEntity(
                    orderId = orderId,
                    productId = priced.productId,
                    sizeId = line.selection.sizeId,
                    options = line.selection.options.filterValues { it > 0 },
                    label = priced.label,
                    category = priced.category,
                    quantity = priced.quantity,
                    unitPrice = priced.unitPrice,
                    total = priced.total,
                )
            }
        )
        payments.forEach { dao.insert(it.toEntity(orderId)) }
        settle(orderId)
        checkNotNull(dao.details(orderId))
    }

    // Checked inside the transaction, so two taps at once can't both get through (D4).
    suspend fun addPayment(orderId: Long, payment: NewPayment): PaymentEntity = db.withTransaction {
        val order = checkNotNull(dao.details(orderId)) { "no order $orderId" }
        check(order.order.status == OrderStatus.PAYING) { "order $orderId is ${order.order.status}, not taking payments" }
        check(order.openCard == null) { "order $orderId still has a card result open" }
        require(payment.amount <= order.due) { "${payment.amount} is more than the ${order.due} still due" }
        val id = dao.insert(payment.toEntity(orderId))
        settle(orderId)
        checkNotNull(dao.payment(id))
    }

    // Returns the order's status after it. A result for a card that is already approved,
    // declined or voided changes nothing, so a late or repeated answer can't undo it.
    suspend fun finishCardPayment(ref: String, result: CardResult): OrderStatus = db.withTransaction {
        val payment = checkNotNull(dao.paymentByRef(ref)) { "no payment with ref $ref" }
        if (payment.status in OPEN_CARD) {
            dao.update(
                when (result) {
                    is CardResult.Approved -> payment.copy(status = PaymentStatus.APPROVED, authCode = result.authCode)
                    is CardResult.Declined -> payment.copy(status = PaymentStatus.DECLINED, declineReason = result.reason)
                    CardResult.Unknown -> payment.copy(status = PaymentStatus.UNKNOWN)
                }
            )
        }
        settle(payment.orderId)
    }

    // "Not charged, try again", after the terminal said it doesn't know the ref
    suspend fun voidCardPayment(ref: String) = db.withTransaction {
        val payment = checkNotNull(dao.paymentByRef(ref)) { "no payment with ref $ref" }
        check(payment.status in OPEN_CARD) { "card $ref is ${payment.status}, can't void it" }
        dao.update(payment.copy(status = PaymentStatus.VOIDED))
    }

    suspend fun openCardPayments(): List<PaymentEntity> = dao.paymentsWithStatus(OPEN_CARD)

    // Only an unpaid order with nothing taken and no card result still open.
    suspend fun cancel(orderId: Long): Boolean = db.withTransaction {
        val order = dao.order(orderId)
        val open = setOf(PaymentStatus.APPROVED, PaymentStatus.PENDING, PaymentStatus.UNKNOWN)
        if (order == null || order.status != OrderStatus.PAYING || dao.payments(orderId).any { it.status in open }) {
            false
        } else {
            dao.update(order.copy(status = OrderStatus.CANCELLED))
            true
        }
    }

    // paid but not on the server yet, oldest first
    suspend fun ordersToSync(): List<OrderWithDetails> = dao.withStatus(OrderStatus.PENDING)

    suspend fun nextToSync(): OrderWithDetails? = dao.firstWithStatus(OrderStatus.PENDING)

    suspend fun markSynced(orderId: Long, serverOrderNo: Long) = db.withTransaction {
        val order = checkNotNull(dao.order(orderId)) { "no order $orderId" }
        dao.update(order.copy(status = OrderStatus.SYNCED, serverOrderNo = serverOrderNo, lastError = null))
    }

    // Returns the new status, FAILED on the 5th failure.
    suspend fun syncFailed(orderId: Long, error: String): OrderStatus = db.withTransaction {
        val order = checkNotNull(dao.order(orderId)) { "no order $orderId" }
        val attempts = order.syncAttempts + 1
        val status = if (attempts >= MAX_SYNC_ATTEMPTS) OrderStatus.FAILED else order.status
        dao.update(order.copy(syncAttempts = attempts, lastError = error, status = status))
        status
    }

    suspend fun retry(orderId: Long) = db.withTransaction {
        val order = checkNotNull(dao.order(orderId)) { "no order $orderId" }
        if (order.status == OrderStatus.FAILED) {
            dao.update(order.copy(status = OrderStatus.PENDING, syncAttempts = 0))
        }
    }

    // PAYING becomes PENDING (paid) once approved payments cover the payable
    private suspend fun settle(orderId: Long): OrderStatus {
        val order = checkNotNull(dao.order(orderId))
        if (order.status != OrderStatus.PAYING) return order.status
        val paid = dao.payments(orderId).filter { it.status == PaymentStatus.APPROVED }.sumOf { it.amount }
        if (paid < order.payable) return order.status
        dao.update(order.copy(status = OrderStatus.PENDING))
        return OrderStatus.PENDING
    }
}

private fun NewPayment.toEntity(orderId: Long): PaymentEntity {
    require(amount > 0) { "payment amount must be positive" }
    return when (this) {
        is NewPayment.Cash -> {
            require(tendered >= amount) { "tendered $tendered doesn't cover $amount" }
            PaymentEntity(
                orderId = orderId,
                type = PaymentType.CASH,
                status = PaymentStatus.APPROVED,
                amount = amount,
                tendered = tendered,
                change = tendered - amount,
            )
        }

        is NewPayment.Card -> PaymentEntity(
            orderId = orderId,
            type = PaymentType.CARD,
            status = PaymentStatus.PENDING,
            amount = amount,
            cardRef = UUID.randomUUID().toString(),
        )
    }
}
