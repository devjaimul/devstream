package com.devsstream.cafepos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.pricing.CouponRejection
import com.devsstream.cafepos.core.pricing.PriceBreakdown
import com.devsstream.cafepos.data.local.OrderWithDetails

data class BreakdownRow(
    val label: String,
    val amount: String = "",
    val strong: Boolean = false,
    val lineAbove: Boolean = false,
)

fun priceRows(price: PriceBreakdown, catalog: Catalog?, coupon: String?): List<BreakdownRow> = buildList {
    price.lines.forEach { add(BreakdownRow("${it.quantity} × ${it.label}", taka(it.total))) }
    price.promotions.forEach { add(BreakdownRow(adjustmentName(it.id, catalog), "−" + taka(it.amount))) }
    add(BreakdownRow("Subtotal", taka(price.subtotal), strong = true, lineAbove = true))
    price.discount?.let { add(BreakdownRow(adjustmentName(it.id, catalog), "−" + taka(it.amount))) }
    rejectionText(price.couponRejection, coupon)?.let { add(BreakdownRow(it)) }
    add(BreakdownRow("VAT", taka(price.vat)))
    add(BreakdownRow("Total", taka(price.total)))
    add(BreakdownRow("Payable", taka(price.payable), strong = true, lineAbove = true))
}

// the receipt: always the numbers saved at sale time (D5)
fun orderRows(details: OrderWithDetails, catalog: Catalog?): List<BreakdownRow> = buildList {
    val order = details.order
    details.lines.sortedBy { it.id }.forEach { add(BreakdownRow("${it.quantity} × ${it.label}", taka(it.total))) }
    order.promotions.forEach { (id, amount) -> add(BreakdownRow(adjustmentName(id, catalog), "−" + taka(amount))) }
    add(BreakdownRow("Subtotal", taka(order.subtotal), strong = true, lineAbove = true))
    order.discountId?.let { add(BreakdownRow(adjustmentName(it, catalog), "−" + taka(order.discountAmount))) }
    rejectionText(order.couponRejection, order.coupon)?.let { add(BreakdownRow(it)) }
    add(BreakdownRow("VAT", taka(order.vat)))
    add(BreakdownRow("Total", taka(order.total)))
    add(BreakdownRow("Payable", taka(order.payable), strong = true, lineAbove = true))
}

private fun rejectionText(rejection: CouponRejection?, coupon: String?): String? = when (rejection) {
    CouponRejection.EXPIRED -> "Coupon ${coupon.orEmpty().trim()} has expired"
    CouponRejection.UNKNOWN -> "Coupon ${coupon.orEmpty().trim()} doesn't exist"
    null -> null
}

@Composable
fun Breakdown(rows: List<BreakdownRow>, modifier: Modifier = Modifier) {
    Column(modifier) {
        rows.forEach { row ->
            if (row.lineAbove) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                val weight = if (row.strong) FontWeight.Bold else FontWeight.Normal
                Text(row.label, Modifier.weight(1f), fontWeight = weight, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(row.amount, fontWeight = weight, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
