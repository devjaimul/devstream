package com.devsstream.cafepos.core.catalog

import java.time.Instant
import java.time.LocalTime

// Percentages are basis points: 1000 = 10%, 500 = 5%.

sealed interface Promotion {
    val id: String

    // for every `every` units of the category, the cheapest unit is free
    data class CheapestFree(
        override val id: String,
        val category: String,
        val every: Int,
    ) : Promotion

    // percent off each line of the category, server local time in [from, until)
    data class HappyHour(
        override val id: String,
        val category: String,
        val percentBps: Int,
        val from: LocalTime,
        val until: LocalTime,
    ) : Promotion
}

sealed interface OrderDiscount {
    val id: String

    data class Threshold(
        override val id: String,
        val minSubtotal: Long,
        val amountOff: Long,
    ) : OrderDiscount

    data class Coupon(
        override val id: String,
        val code: String,
        val percentBps: Int,
        val cap: Long?,
        val validUntil: Instant?,
    ) : OrderDiscount
}
