package com.devsstream.cafepos.checkout

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.pricing.OrderLine
import com.devsstream.cafepos.core.pricing.PriceBreakdown
import com.devsstream.cafepos.core.pricing.priceOrder
import com.devsstream.cafepos.core.validation.validate
import com.devsstream.cafepos.data.NewPayment
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.payment.CardPayments
import com.devsstream.cafepos.time.ServerClock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CheckoutResult {
    data class Saved(val orderId: Long) : CheckoutResult

    // Nothing was saved. Show these numbers and let the cashier tap again.
    data class PriceChanged(val price: PriceBreakdown) : CheckoutResult

    // Nothing was saved: under the current catalog some line breaks a rule or is gone.
    data class NotValid(val problems: List<String>) : CheckoutResult
}

// D4: card first, then cash. Cash only ever pays everything that's left.
@Singleton
class Checkout @Inject constructor(
    private val orders: OrderRepository,
    private val cards: CardPayments,
    private val clock: ServerClock,
    private val sync: SyncScheduler,
) {

    // Whole order in cash: order, lines and payment in one write, paid at once.
    suspend fun payCash(
        lines: List<OrderLine>,
        catalog: Catalog,
        coupon: String?,
        shown: PriceBreakdown,
        tendered: Long,
    ): CheckoutResult {
        val result = save(lines, catalog, coupon, shown) { price -> NewPayment.Cash(price.payable, tendered) }
        if (result is CheckoutResult.Saved) sync.requestSync()
        return result
    }

    // The whole payable, or the card part of a split. The card row is saved before
    // the terminal is touched (D3), then the charge runs in the background.
    suspend fun payCard(
        lines: List<OrderLine>,
        catalog: Catalog,
        coupon: String?,
        shown: PriceBreakdown,
        amount: Long,
    ): CheckoutResult {
        val result = save(lines, catalog, coupon, shown) { NewPayment.Card(amount) }
        if (result is CheckoutResult.Saved) {
            cards.start(checkNotNull(orders.order(result.orderId)).payments.single())
        }
        return result
    }

    // After a decline or "not charged": a new attempt with a new ref.
    suspend fun anotherCard(orderId: Long, amount: Long) {
        cards.start(orders.addPayment(orderId, NewPayment.Card(amount)))
    }

    // The rest in cash, once the card part is settled.
    suspend fun payRestInCash(orderId: Long, tendered: Long) {
        val order = checkNotNull(orders.order(orderId)) { "no order $orderId" }
        orders.addPayment(orderId, NewPayment.Cash(order.due, tendered))
        if (orders.order(orderId)?.order?.status == OrderStatus.PENDING) sync.requestSync()
    }

    suspend fun cancel(orderId: Long): Boolean = orders.cancel(orderId)

    // Checked and priced again at the server time of the tap, against the catalog we
    // have now. If a line no longer fits, or the price isn't what the cashier was looking
    // at (happy hour just ended, catalog changed...), nothing is saved.
    private suspend fun save(
        lines: List<OrderLine>,
        catalog: Catalog,
        coupon: String?,
        shown: PriceBreakdown,
        firstPayment: (PriceBreakdown) -> NewPayment,
    ): CheckoutResult {
        val problems = lines.flatMap { line ->
            val product = catalog.product(line.selection.productId)
                ?: return@flatMap listOf("${line.selection.productId} is no longer on the menu")
            validate(line.selection, product, catalog).map { "${product.name}: ${it.message}" }
        }
        if (problems.isNotEmpty()) return CheckoutResult.NotValid(problems)

        val now = clock.now()
        val price = priceOrder(lines, catalog, coupon, now.time)
        if (price != shown) return CheckoutResult.PriceChanged(price)

        val saved = orders.createOrder(lines, price, coupon, catalog, now, listOf(firstPayment(price)))
        return CheckoutResult.Saved(saved.order.id)
    }
}
