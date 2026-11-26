package com.devsstream.cafepos.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsstream.cafepos.checkout.Checkout
import com.devsstream.cafepos.checkout.CheckoutResult
import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.pricing.OrderLine
import com.devsstream.cafepos.core.pricing.PriceBreakdown
import com.devsstream.cafepos.core.pricing.priceOrder
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.time.ServerClock
import com.devsstream.cafepos.time.ticks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Cart(
    val lines: List<OrderLine> = emptyList(),
    val coupon: String = "",
    val busy: Boolean = false,
    val priceChanged: Boolean = false,
    val error: String? = null,
    // just saved, the screen should open it
    val openOrderId: Long? = null,
)

data class CartUi(
    val cart: Cart = Cart(),
    val catalog: Catalog? = null,
    // null when the cart is empty, or doesn't fit the current catalog any more
    val price: PriceBreakdown? = null,
)

// One per activity, shared by the menu, item, cart and pay screens.
@HiltViewModel
class CartViewModel @Inject constructor(
    catalogs: CatalogRepository,
    clock: ServerClock,
    private val checkout: Checkout,
) : ViewModel() {

    private val cart = MutableStateFlow(Cart())

    // priced again every second, so the screen follows happy hour and coupon expiry
    val state: StateFlow<CartUi> = combine(cart, catalogs.catalog, clock.ticks()) { cart, catalog, now ->
        val price = if (catalog == null || cart.lines.isEmpty()) {
            null
        } else {
            runCatching { priceOrder(cart.lines, catalog, cart.coupon.ifBlank { null }, now.time) }.getOrNull()
        }
        CartUi(cart, catalog, price)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUi())

    fun add(line: OrderLine) = cart.update { c ->
        val same = c.lines.indexOfFirst { it.selection == line.selection }
        val lines = if (same < 0) {
            c.lines + line
        } else {
            c.lines.toMutableList().also { it[same] = it[same].copy(quantity = it[same].quantity + line.quantity) }
        }
        c.copy(lines = lines, priceChanged = false)
    }

    fun changeQuantity(index: Int, delta: Int) = cart.update { c ->
        val line = c.lines.getOrNull(index) ?: return@update c
        val quantity = line.quantity + delta
        val lines = c.lines.toMutableList()
        if (quantity <= 0) lines.removeAt(index) else lines[index] = line.copy(quantity = quantity)
        c.copy(lines = lines, priceChanged = false)
    }

    fun clear() = cart.update { Cart() }

    fun setCoupon(code: String) = cart.update { it.copy(coupon = code, priceChanged = false) }

    fun payCash(tendered: Long) = pay { lines, catalog, coupon, shown ->
        checkout.payCash(lines, catalog, coupon, shown, tendered)
    }

    fun payCard(amount: Long) = pay { lines, catalog, coupon, shown ->
        checkout.payCard(lines, catalog, coupon, shown, amount)
    }

    fun orderOpened() = cart.update { it.copy(openOrderId = null) }

    // Ignores a second tap while the first is saving. Once saved the cart is empty,
    // so a late tap has nothing to pay for.
    private fun pay(action: suspend (List<OrderLine>, Catalog, String?, PriceBreakdown) -> CheckoutResult) {
        val ui = state.value
        val catalog = ui.catalog ?: return
        val shown = ui.price ?: return
        if (cart.value.busy) return
        cart.update { it.copy(busy = true, priceChanged = false, error = null) }

        viewModelScope.launch {
            try {
                when (val result = action(cart.value.lines, catalog, cart.value.coupon.ifBlank { null }, shown)) {
                    is CheckoutResult.Saved -> cart.value = Cart(openOrderId = result.orderId)
                    is CheckoutResult.PriceChanged -> cart.update { it.copy(priceChanged = true) }
                }
            } catch (e: IllegalArgumentException) {
                cart.update { it.copy(error = e.message) }
            } finally {
                cart.update { it.copy(busy = false) }
            }
        }
    }
}
