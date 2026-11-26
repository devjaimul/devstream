package com.devsstream.cafepos.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.devsstream.cafepos.ui.OrderStatusPill
import com.devsstream.cafepos.ui.Pill
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
fun OrdersScreen(
    status: StatusUi,
    tabs: @Composable () -> Unit,
    onOpen: (Long) -> Unit,
    vm: OrdersViewModel = hiltViewModel(),
) {
    val list by vm.list.collectAsStateWithLifecycle()
    val c = MaterialTheme.colorScheme

    PosScaffold(title = "Orders", status = status, bottomBar = tabs) { padding ->
        if (list.isEmpty()) {
            Text("No orders yet.", Modifier.padding(padding).padding(16.dp))
            return@PosScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.order.id }) { details ->
                val order = details.order
                val failed = order.status == OrderStatus.FAILED
                Card(
                    onClick = { onOpen(order.id) },
                    colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLowest),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(order.receiptNo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(taka(order.payable), style = MaterialTheme.typography.titleMedium, color = c.primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OrderStatusPill(order.status)
                            if (!order.timeConfirmed) Pill("Time not confirmed", c.errorContainer, c.onErrorContainer, Icons.Filled.Warning)
                        }
                        val time = status.zone?.let { formatTime(Instant.ofEpochMilli(order.createdAt), it) }.orEmpty()
                        val items = details.lines.sumOf { it.quantity }
                        Text(
                            "$time · $items ${if (items == 1) "item" else "items"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant,
                        )
                        if (order.status == OrderStatus.PENDING && order.syncAttempts > 0 || failed) {
                            Text(syncText(order), style = MaterialTheme.typography.bodySmall, color = if (failed) c.error else c.onSurfaceVariant)
                        }
                        if (failed) {
                            FilledTonalButton(onClick = { vm.retry(order.id) }, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}
