package com.devsstream.cafepos.data.remote

import kotlinx.serialization.Serializable

// Sent exactly as it was priced at sale time (D5). Money is poisha, times are UTC ISO-8601.
@Serializable
data class OrderDto(
    val clientOrderId: String,
    val receiptNo: String,
    val createdAt: String,
    val businessDay: String,
    // false if the till had to guess the time after a reboot (D1)
    val timeConfirmed: Boolean = true,
    val catalogVersion: Int,
    val coupon: String? = null,
    val lines: List<OrderLineDto>,
    val promotions: List<AmountDto>,
    val subtotalPoisha: Long,
    val discount: AmountDto? = null,
    val vatPoisha: Long,
    val totalPoisha: Long,
    val payablePoisha: Long,
    val payments: List<PaymentDto>,
)

@Serializable
data class OrderLineDto(
    val productId: String,
    val sizeId: String? = null,
    val options: List<OptionQuantityDto> = emptyList(),
    val label: String,
    val category: String,
    val quantity: Int,
    val unitPricePoisha: Long,
    val totalPoisha: Long,
)

@Serializable
data class OptionQuantityDto(val id: String, val quantity: Int)

@Serializable
data class AmountDto(val id: String, val amountPoisha: Long)

@Serializable
enum class PaymentType { CASH, CARD }

@Serializable
data class PaymentDto(
    val type: PaymentType,
    val amountPoisha: Long,
    val tenderedPoisha: Long? = null,
    val changePoisha: Long? = null,
    val cardRef: String? = null,
    val authCode: String? = null,
)
