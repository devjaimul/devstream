package com.devsstream.cafepos.summary

import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.remote.PaymentType
import java.time.LocalDate

data class DaySummary(
    val day: LocalDate,
    val orders: Int,
    val payable: Long,
    val cash: Long,
    val card: Long,
    // every product with the most units, so a tie shows all of them
    val topItems: List<String>,
    val topUnits: Int,
    val notSynced: Int,
)

private val PAID = setOf(OrderStatus.PENDING, OrderStatus.SYNCED, OrderStatus.FAILED)

// Only paid orders count: one still paying or cancelled isn't a sale.
fun summarize(day: LocalDate, orders: List<OrderWithDetails>): DaySummary {
    val paid = orders.filter { it.order.businessDay == day.toString() && it.order.status in PAID }
    val payments = paid.flatMap { it.payments }.filter { it.status == PaymentStatus.APPROVED }

    // a free bakery unit is still a line quantity, so it counts as sold
    val units = paid.flatMap { it.lines }
        .groupBy { it.productId }
        .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
    val top = units.values.maxOrNull() ?: 0

    return DaySummary(
        day = day,
        orders = paid.size,
        payable = paid.sumOf { it.order.payable },
        // split payments count by their parts; cash is what was kept, not what was handed over
        cash = payments.filter { it.type == PaymentType.CASH }.sumOf { it.amount },
        card = payments.filter { it.type == PaymentType.CARD }.sumOf { it.amount },
        topItems = units.filterValues { it == top && top > 0 }.keys.sorted(),
        topUnits = top,
        notSynced = paid.count { it.order.status != OrderStatus.SYNCED },
    )
}
