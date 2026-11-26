package com.devsstream.cafepos.ui

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.catalog.OrderDiscount
import com.devsstream.cafepos.core.catalog.Promotion
import com.devsstream.cafepos.core.money.formatBdt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun taka(poisha: Long): String = "৳" + formatBdt(poisha)

private val timeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")

fun formatTime(time: Instant, zone: ZoneId): String = time.atZone(zone).format(timeFormat)

// the cashier types whole taka, e.g. "500"
fun parseTaka(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it >= 0 }?.times(100)

fun percent(bps: Int): String = if (bps % 100 == 0) "${bps / 100}%" else "${bps / 100.0}%"

// Built from the catalog entry, so a new catalog gets sensible names without code changes.
fun adjustmentName(id: String, catalog: Catalog?): String {
    catalog?.promotions?.find { it.id == id }?.let { p ->
        return when (p) {
            is Promotion.CheapestFree -> "$id: every ${p.every} ${p.category.lowercase()}, cheapest free"
            is Promotion.HappyHour -> "$id: happy hour, ${percent(p.percentBps)} off ${p.category.lowercase()}"
        }
    }
    catalog?.orderDiscounts?.find { it.id == id }?.let { d ->
        return when (d) {
            is OrderDiscount.Threshold -> "$id: ${taka(d.amountOff)} off over ${taka(d.minSubtotal)}"
            is OrderDiscount.Coupon -> "$id: coupon ${d.code}, ${percent(d.percentBps)} off"
        }
    }
    return id
}
