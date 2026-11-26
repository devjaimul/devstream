package com.devsstream.cafepos.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.devsstream.cafepos.core.pricing.CouponRejection
import com.devsstream.cafepos.data.remote.PaymentType

// D2. PAYING -> PENDING -> SYNCED, PENDING -> FAILED -> (Retry) PENDING, PAYING -> CANCELLED
enum class OrderStatus { PAYING, PENDING, SYNCED, FAILED, CANCELLED }

enum class PaymentStatus { PENDING, APPROVED, DECLINED, UNKNOWN, VOIDED }

// Money in poisha, createdAt in server epoch ms, businessDay as yyyy-MM-dd (D6).
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["clientOrderId"], unique = true),
        Index(value = ["businessDay", "receiptSeq"], unique = true),
    ],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientOrderId: String,
    val receiptNo: String,
    val businessDay: String,
    val receiptSeq: Int,
    val createdAt: Long,
    val timeConfirmed: Boolean,
    val catalogVersion: Int,
    val coupon: String?,
    val couponRejection: CouponRejection?,
    val subtotal: Long,
    val promotions: Map<String, Long>,
    val discountId: String?,
    val discountAmount: Long,
    val vat: Long,
    val total: Long,
    val payable: Long,
    val status: OrderStatus,
    val syncAttempts: Int = 0,
    val lastError: String? = null,
    val serverOrderNo: Long? = null,
)

@Entity(
    tableName = "order_lines",
    foreignKeys = [ForeignKey(entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"])],
    indices = [Index("orderId")],
)
data class OrderLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: String,
    val sizeId: String?,
    val options: Map<String, Int>,
    val label: String,
    val category: String,
    val quantity: Int,
    val unitPrice: Long,
    val total: Long,
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"])],
    indices = [Index("orderId"), Index(value = ["cardRef"], unique = true)],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val type: PaymentType,
    val status: PaymentStatus,
    val amount: Long,
    val tendered: Long? = null,
    val change: Long? = null,
    val cardRef: String? = null,
    val authCode: String? = null,
    val declineReason: String? = null,
)

data class OrderWithDetails(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val lines: List<OrderLineEntity>,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val payments: List<PaymentEntity>,
) {
    val paid: Long get() = payments.filter { it.status == PaymentStatus.APPROVED }.sumOf { it.amount }

    val due: Long get() = (order.payable - paid).coerceAtLeast(0)

    // a card attempt still waiting for its result, or with a result nobody knows
    val openCard: PaymentEntity? get() = payments.find { it.status in OPEN_CARD }
}

val OPEN_CARD = setOf(PaymentStatus.PENDING, PaymentStatus.UNKNOWN)
