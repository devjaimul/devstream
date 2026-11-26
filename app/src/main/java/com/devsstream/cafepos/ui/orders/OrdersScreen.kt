package com.devsstream.cafepos.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.formatTime
import com.devsstream.cafepos.ui.order.syncText
import com.devsstream.cafepos.ui.taka
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orders: OrderRepository,
    private val sync: SyncScheduler,
) : ViewModel() {

    // newest first, nothing is ever removed from this list
    val list: StateFlow<List<OrderWithDetails>> =
        orders.observeOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(orderId: Long) {
        viewModelScope.launch {
            orders.retry(orderId)
            sync.requestSync()
        }
    }
}

@Composable
fun OrdersScreen(status: StatusUi, onOpen: (Long) -> Unit, onBack: () -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()

    PosScaffold(title = "Orders", status = status, onBack = onBack) { padding ->
        if (list.isEmpty()) {
            Text("No orders yet.", Modifier.padding(padding).padding(16.dp))
            return@PosScaffold
        }
        LazyColumn(contentPadding = padding) {
            items(list, key = { it.order.id }) { details ->
                val order = details.order
                val failed = order.status == OrderStatus.FAILED
                val time = status.zone?.let { formatTime(Instant.ofEpochMilli(order.createdAt), it) }.orEmpty()
                ListItem(
                    headlineContent = { Text("${order.receiptNo} · ${taka(order.payable)}") },
                    overlineContent = { Text(time + if (order.timeConfirmed) "" else " · time not confirmed") },
                    supportingContent = {
                        Text(
                            syncText(order),
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        if (failed) TextButton(onClick = { vm.retry(order.id) }) { Text("Retry") }
                    },
                    modifier = Modifier.clickable { onOpen(order.id) },
                )
            }
        }
    }
}
