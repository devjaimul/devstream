package com.devsstream.cafepos.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.core.catalog.Product
import com.devsstream.cafepos.ui.Initial
import com.devsstream.cafepos.ui.Pill
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.cart.CartUi
import com.devsstream.cafepos.ui.taka

fun prettyCategory(category: String) = category.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun MenuScreen(
    ui: CartUi,
    status: StatusUi,
    tabs: @Composable () -> Unit,
    onProduct: (String) -> Unit,
    onCart: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    PosScaffold(
        title = "Café POS",
        status = status,
        bottomBar = {
            Column {
                if (ui.cart.lines.isNotEmpty()) CartBar(ui, onCart)
                tabs()
            }
        },
    ) { padding ->
        val catalog = ui.catalog
        if (catalog == null) {
            Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("Loading the menu from the server…", Modifier.padding(top = 16.dp))
            }
            return@PosScaffold
        }

        val categories = catalog.products.map { it.category }.distinct()
        val shown = catalog.products.filter { category == null || it.category == category }
        val inCart = ui.cart.lines.groupBy { it.selection.productId }.mapValues { (_, l) -> l.sumOf { it.quantity } }

        Column(Modifier.padding(padding)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                categories.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(prettyCategory(c)) })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.id }) { product ->
                    ProductCard(product, categories.indexOf(product.category), inCart[product.id] ?: 0) { onProduct(product.id) }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, colourIndex: Int, inCart: Int, onClick: () -> Unit) {
    val from = product.basePrice + (product.sizes.minOfOrNull { it.extraPrice } ?: 0)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Initial(product.name, colourIndex)
                Spacer(Modifier.weight(1f))
                if (inCart > 0) {
                    Pill("$inCart in cart", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (product.sizes.isEmpty()) prettyCategory(product.category) else product.sizes.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (product.sizes.isEmpty()) taka(from) else "from ${taka(from)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CartBar(ui: CartUi, onCart: () -> Unit) {
    val items = ui.cart.lines.sumOf { it.quantity }
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onCart),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = null)
            Text("  $items ${if (items == 1) "item" else "items"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(ui.price?.let { taka(it.payable) } ?: "-", style = MaterialTheme.typography.titleMedium)
            Text("  View cart ›", style = MaterialTheme.typography.labelLarge)
        }
    }
}
