package com.devsstream.cafepos.ui.cart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.ui.Breakdown
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.menu.Stepper
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
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onPay,
                    enabled = price != null,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Text(if (price != null) "Pay ${taka(price.payable)}" else "Pay")
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.padding(horizontal = 16.dp)) {
            itemsIndexed(ui.cart.lines) { index, line ->
                val priced = price?.lines?.getOrNull(index)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(priced?.label ?: line.selection.productId)
                        if (priced != null) Text("${taka(priced.unitPrice)} each", style = MaterialTheme.typography.bodySmall)
                    }
                    Stepper(
                        value = line.quantity,
                        onMinus = { onQuantity(index, -1) },
                        onPlus = { onQuantity(index, +1) },
                        minusEnabled = true,
                        plusEnabled = true,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = ui.cart.coupon,
                    onValueChange = onCoupon,
                    label = { Text("Coupon code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
            item {
                when {
                    price != null -> Breakdown(priceRows(price, ui.catalog, ui.cart.coupon))
                    ui.cart.lines.isEmpty() -> Text("The cart is empty.")
                    else -> Text(
                        "Some items don't fit the current menu any more. Remove them or clear the cart.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
