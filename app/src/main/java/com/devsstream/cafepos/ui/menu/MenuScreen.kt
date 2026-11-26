package com.devsstream.cafepos.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.cart.CartUi
import com.devsstream.cafepos.ui.taka

@Composable
fun MenuScreen(
    ui: CartUi,
    status: StatusUi,
    onProduct: (String) -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onSummary: () -> Unit,
    onDebug: () -> Unit,
) {
    PosScaffold(
        title = "Menu",
        status = status,
        actions = {
            TextButton(onClick = onOrders) { Text("Orders") }
            TextButton(onClick = onSummary) { Text("Summary") }
            TextButton(onClick = onDebug) { Text("Debug") }
        },
        bottomBar = { if (ui.cart.lines.isNotEmpty()) CartBar(ui, onCart) },
    ) { padding ->
        val catalog = ui.catalog
        if (catalog == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading the menu from the server…")
            }
            return@PosScaffold
        }
        LazyColumn(contentPadding = padding) {
            catalog.products.groupBy { it.category }.forEach { (category, products) ->
                item(key = "category-$category") {
                    Text(
                        category.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(products, key = { it.id }) { product ->
                    val from = product.basePrice + (product.sizes.minOfOrNull { it.extraPrice } ?: 0)
                    ListItem(
                        headlineContent = { Text(product.name) },
                        supportingContent = {
                            if (product.sizes.isNotEmpty()) Text(product.sizes.joinToString(" / ") { it.name })
                        },
                        trailingContent = { Text(if (product.sizes.isEmpty()) taka(from) else "from ${taka(from)}") },
                        modifier = Modifier.clickable { onProduct(product.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CartBar(ui: CartUi, onCart: () -> Unit) {
    val items = ui.cart.lines.sumOf { it.quantity }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$items items · ${ui.price?.let { taka(it.payable) } ?: "-"}", Modifier.weight(1f))
            Button(onClick = onCart) { Text("View cart") }
        }
    }
}
