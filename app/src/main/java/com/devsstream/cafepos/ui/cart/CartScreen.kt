package com.devsstream.cafepos.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.ui.BigButton
import com.devsstream.cafepos.ui.Breakdown
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.Stepper
import com.devsstream.cafepos.ui.priceRows
import com.devsstream.cafepos.ui.taka

@Composable
fun CartScreen(
    ui: CartUi,
    status: StatusUi,
    onQuantity: (index: Int, delta: Int) -> Unit,
    onCoupon: (String) -> Unit,
    onClear: () -> Unit,
    onPay: () -> Unit,
    onBack: () -> Unit,
) {
    val price = ui.price
    PosScaffold(
        title = "Cart",
        status = status,
        onBack = onBack,
        actions = { TextButton(onClick = onClear, enabled = ui.cart.lines.isNotEmpty()) { Text("Clear") } },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                BigButton(
                    text = if (price != null) "Pay ${taka(price.payable)}" else "Pay",
                    onClick = onPay,
                    enabled = price != null,
                    modifier = Modifier.navigationBarsPadding().padding(16.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.cart.lines.isEmpty()) {
                item { Text("The cart is empty.", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyLarge) }
            }
            itemsIndexed(ui.cart.lines) { index, line ->
                val priced = price?.lines?.getOrNull(index)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(priced?.label ?: line.selection.productId, style = MaterialTheme.typography.titleSmall)
                            if (priced != null) {
                                Text(
                                    "${taka(priced.unitPrice)} each · ${taka(priced.total)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Stepper(line.quantity, { onQuantity(index, -1) }, { onQuantity(index, +1) }, minusEnabled = true, plusEnabled = true)
                    }
                }
            }
            if (ui.cart.lines.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = ui.cart.coupon,
                        onValueChange = onCoupon,
                        label = { Text("Coupon code") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Bill", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                            if (price != null) {
                                Breakdown(priceRows(price, ui.catalog, ui.cart.coupon))
                            } else {
                                Text(
                                    "Some items don't fit the current menu any more. Remove them or clear the cart.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
