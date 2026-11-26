package com.devsstream.cafepos.core.pricing

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.catalog.OrderDiscount
import com.devsstream.cafepos.core.catalog.Promotion
import com.devsstream.cafepos.core.money.percentOf
import com.devsstream.cafepos.core.money.roundToWholeBdt
import com.devsstream.cafepos.core.order.ItemSelection
import java.time.Instant
import java.time.LocalTime

data class OrderLine(val selection: ItemSelection, val quantity: Int)

data class PricedLine(
    val productId: String,
    val label: String,
    val category: String,
    val unitPrice: Long,
    val quantity: Int,
    val total: Long,
)

// a promotion or discount that took money off, e.g. P1 12000
data class Adjustment(val id: String, val amount: Long)

enum class CouponRejection { UNKNOWN, EXPIRED }

data class PriceBreakdown(
    val lines: List<PricedLine>,
    val promotions: List<Adjustment>,
    val subtotal: Long,
    val discount: Adjustment?,
    val couponRejection: CouponRejection?,
    val vat: Long,
    val total: Long,
    val payable: Long,
) {
    val discountedSubtotal: Long get() = subtotal - (discount?.amount ?: 0)

    fun promotion(id: String): Long = promotions.find { it.id == id }?.amount ?: 0
}

fun priceOrder(
    lines: List<OrderLine>,
    catalog: Catalog,
    coupon: String?,
    serverTime: Instant,
): PriceBreakdown {
    val priced = lines.map { priceLine(it, catalog) }
    val localTime = serverTime.atZone(catalog.timeZone).toLocalTime()

    // the brief fixes the order: multi-buy first, then happy hour
    val promotions = mutableListOf<Adjustment>()
    for (p in catalog.promotions.filterIsInstance<Promotion.CheapestFree>()) {
        val amount = cheapestFree(p, priced)
        if (amount > 0) promotions += Adjustment(p.id, amount)
    }
    for (p in catalog.promotions.filterIsInstance<Promotion.HappyHour>()) {
        if (!p.isOn(localTime)) continue
        // rounded per line, the brief says "off every line total"
        val amount = priced.filter { it.category == p.category }.sumOf { percentOf(it.total, p.percentBps) }
        if (amount > 0) promotions += Adjustment(p.id, amount)
    }

    val subtotal = (priced.sumOf { it.total } - promotions.sumOf { it.amount }).coerceAtLeast(0)

    val code = coupon?.trim()?.takeIf { it.isNotEmpty() }
    var couponFound = false
    var rejection: CouponRejection? = null
    val candidates = mutableListOf<Adjustment>()
    for (d in catalog.orderDiscounts) {
        when (d) {
            is OrderDiscount.Threshold ->
                if (subtotal >= d.minSubtotal) candidates += Adjustment(d.id, minOf(d.amountOff, subtotal))

            is OrderDiscount.Coupon -> {
                if (code == null || !d.code.equals(code, ignoreCase = true)) continue
                couponFound = true
                if (d.validUntil != null && !serverTime.isBefore(d.validUntil)) {
                    rejection = CouponRejection.EXPIRED
                    continue
                }
                val amount = percentOf(subtotal, d.percentBps)
                candidates += Adjustment(d.id, if (d.cap != null) minOf(amount, d.cap) else amount)
            }
        }
    }
    if (code != null && !couponFound) rejection = CouponRejection.UNKNOWN

    // only one discount applies. maxByOrNull keeps the first one on a tie,
    // so catalog order decides ties (T1 is listed before C1)
    val discount = candidates.filter { it.amount > 0 }.maxByOrNull { it.amount }

    val discounted = subtotal - (discount?.amount ?: 0)
    val vat = percentOf(discounted, catalog.vatBps)
    val total = discounted + vat

    return PriceBreakdown(
        lines = priced,
        promotions = promotions,
        subtotal = subtotal,
        discount = discount,
        couponRejection = rejection,
        vat = vat,
        total = total,
        payable = roundToWholeBdt(total),
    )
}

private fun priceLine(line: OrderLine, catalog: Catalog): PricedLine {
    require(line.quantity > 0) { "line quantity must be positive" }
    val s = line.selection
    val product = requireNotNull(catalog.product(s.productId)) { "unknown product ${s.productId}" }
    val size = s.sizeId?.let { id ->
        requireNotNull(product.sizes.find { it.id == id }) { "unknown size $id for ${product.id}" }
    }
    val options = s.options.filterValues { it > 0 }.map { (id, qty) ->
        requireNotNull(catalog.option(id)) { "unknown option $id" } to qty
    }

    val unitPrice = product.basePrice + (size?.extraPrice ?: 0) + options.sumOf { (o, qty) -> o.price * qty }

    val label = buildList {
        add(if (size != null) "${product.name} ${size.name}" else product.name)
        options.forEach { (o, qty) -> add(if (qty > 1) "${o.name} x$qty" else o.name) }
    }.joinToString(", ")

    return PricedLine(product.id, label, product.category, unitPrice, line.quantity, unitPrice * line.quantity)
}

// every unit of the category as its own price, cheapest first; floor(n / every) of them are free
private fun cheapestFree(p: Promotion.CheapestFree, lines: List<PricedLine>): Long {
    if (p.every <= 0) return 0
    val units = lines
        .filter { it.category == p.category }
        .flatMap { line -> List(line.quantity) { line.unitPrice } }
        .sorted()
    return units.take(units.size / p.every).sum()
}

private fun Promotion.HappyHour.isOn(time: LocalTime): Boolean =
    if (from <= until) time >= from && time < until
    else time >= from || time < until // window crosses midnight
