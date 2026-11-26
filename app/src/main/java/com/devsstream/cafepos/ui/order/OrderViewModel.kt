package com.devsstream.cafepos.ui.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsstream.cafepos.checkout.Checkout
import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.payment.CardPayments
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderUi(
    val details: OrderWithDetails? = null,
    val step: PaymentStep? = null,
    val catalog: Catalog? = null,
    val message: String? = null,
)

// Only watches Room. Rotation or process death just means watching again: the
// order id comes back from the nav arguments and the state from the database.
@HiltViewModel
class OrderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val orders: OrderRepository,
    private val checkout: Checkout,
    private val cards: CardPayments,
    private val sync: SyncScheduler,
    catalogs: CatalogRepository,
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedState["orderId"])
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<OrderUi> =
        combine(orders.observeOrder(orderId), cards.waitingRefs, catalogs.catalog, message) { details, waiting, catalog, msg ->
            OrderUi(details, details?.let { paymentStep(it, waiting) }, catalog, msg)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderUi())

    fun anotherCard(amount: Long) = act { checkout.anotherCard(orderId, amount) }

    fun payRestInCash(tendered: Long) = act { checkout.payRestInCash(orderId, tendered) }

    fun checkAgain(ref: String) = act { cards.checkAgain(ref) }

    fun notCharged(ref: String) = act { cards.markNotCharged(ref) }

    fun cancel() = act {
        if (!checkout.cancel(orderId)) message.value = "Can't cancel: money was taken or a card result is still open."
    }

    fun retrySync() = act {
        orders.retry(orderId)
        sync.requestSync()
    }

    // A refused action (say a second tap while a card is open) is shown, not a crash.
    private fun act(block: suspend () -> Unit) {
        message.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: IllegalStateException) {
                message.value = e.message
            } catch (e: IllegalArgumentException) {
                message.value = e.message
            }
        }
    }
}
