package com.devsstream.cafepos.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.catalog.ModifierGroup
import com.devsstream.cafepos.core.catalog.Product
import com.devsstream.cafepos.core.order.ItemSelection
import com.devsstream.cafepos.core.pricing.OrderLine
import com.devsstream.cafepos.core.pricing.unitPrice
import com.devsstream.cafepos.core.validation.validate
import com.devsstream.cafepos.ui.BigButton
import com.devsstream.cafepos.ui.Initial
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.Stepper
import com.devsstream.cafepos.ui.taka

private val optionsSaver = Saver<Map<String, Int>, ArrayList<String>>(
    save = { map -> ArrayList(map.map { (id, qty) -> "$id=$qty" }) },
    restore = { list -> list.associate { it.substringBefore('=') to it.substringAfter('=').toInt() } },
)

// Everything comes from the catalog: sizes, groups, limits and rules. The picker lets the
// cashier go past a limit on purpose, so validate() can say what's wrong (TC-01 to TC-04).
@Composable
fun ItemScreen(
    productId: String,
    catalog: Catalog?,
    status: StatusUi,
    onAdd: (OrderLine) -> Unit,
    onBack: () -> Unit,
) {
    val product = catalog?.product(productId)
    var sizeId by rememberSaveable(productId) { mutableStateOf<String?>(null) }
    var options by rememberSaveable(productId, stateSaver = optionsSaver) { mutableStateOf(emptyMap<String, Int>()) }
    var quantity by rememberSaveable(productId) { mutableIntStateOf(1) }

    val selection = ItemSelection(productId, sizeId, options.filterValues { it > 0 })
    val violations = if (product != null) validate(selection, product, catalog) else emptyList()
    val price = if (product != null && violations.isEmpty()) unitPrice(selection, catalog) else null

    PosScaffold(
        title = "Add to order",
        status = status,
        onBack = onBack,
        bottomBar = {
            if (product != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        // the full list is in the page, the first problem stays in sight here
                        violations.firstOrNull()?.let {
                            Text(
                                if (violations.size > 1) "${it.message} (+${violations.size - 1} more)" else it.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Stepper(quantity, { quantity-- }, { quantity++ }, minusEnabled = quantity > 1, plusEnabled = true)
                            Spacer(Modifier.width(16.dp))
                            BigButton(
                                text = if (price != null) "Add · ${taka(price * quantity)}" else "Add",
                                onClick = { onAdd(OrderLine(selection, quantity)); onBack() },
                                enabled = violations.isEmpty(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (catalog == null || product == null) {
            Text("This item isn't on the current menu.", Modifier.padding(padding).padding(16.dp))
            return@PosScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(product, catalog.products.map { it.category }.distinct().indexOf(product.category), price) }
            if (product.sizes.isNotEmpty()) {
                item {
                    Section("Size", "Pick one") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            product.sizes.forEach { size ->
                                SizeCard(
                                    name = size.name,
                                    extra = if (size.extraPrice > 0) "+${taka(size.extraPrice)}" else "Base price",
                                    selected = sizeId == size.id,
                                    onClick = { sizeId = size.id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            for (groupId in product.groupIds) {
                val group = catalog.group(groupId) ?: continue
                item(key = "group-$groupId") {
                    Section(group.name, limitsText(group)) {
                        group.options.forEachIndexed { i, option ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val qty = options[option.id] ?: 0
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.name, style = MaterialTheme.typography.bodyLarge)
                                    if (option.price > 0) {
                                        Text("+${taka(option.price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Stepper(
                                    value = qty,
                                    onMinus = { options = options + (option.id to qty - 1) },
                                    onPlus = { options = options + (option.id to qty + 1) },
                                    minusEnabled = qty > 0,
                                    // no cap here: going past a limit is allowed, validate() explains it
                                    plusEnabled = true,
                                )
                            }
                        }
                    }
                }
            }
            if (violations.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, Modifier.size(20.dp))
                            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                violations.forEach { Text(it.message, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(product: Product, colourIndex: Int, unitPrice: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Initial(product.name, colourIndex, size = 56)
        Column(Modifier.padding(start = 16.dp)) {
            Text(product.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                if (unitPrice != null) "${taka(unitPrice)} each" else "Base ${taka(product.basePrice)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Section(title: String, hint: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (hint.isNotEmpty()) {
                    Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
private fun SizeCard(name: String, extra: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (selected) c.primaryContainer else c.surfaceContainerLow),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(extra, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }
}

private fun limitsText(group: ModifierGroup): String = listOfNotNull(
    group.max?.let { "Up to $it" },
    group.maxDistinct?.let { "$it different" },
    group.maxPerOption?.let { "$it of each" },
).joinToString(" · ")
