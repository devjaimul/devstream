package com.devsstream.cafepos.ui.menu

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.taka

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
    PosScaffold(title = product?.name ?: "Item", status = status, onBack = onBack) { padding ->
        if (catalog == null || product == null) {
            Text("This item isn't on the current menu.", Modifier.padding(padding).padding(16.dp))
        } else {
            ItemPicker(product, catalog, padding) { onAdd(it); onBack() }
        }
    }
}

private val optionsSaver = Saver<Map<String, Int>, ArrayList<String>>(
    save = { map -> ArrayList(map.map { (id, qty) -> "$id=$qty" }) },
    restore = { list -> list.associate { it.substringBefore('=') to it.substringAfter('=').toInt() } },
)

// a soft cap for the + button only, the real limits are checked by validate()
private const val STEPPER_MAX = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemPicker(product: Product, catalog: Catalog, padding: PaddingValues, onAdd: (OrderLine) -> Unit) {
    var sizeId by rememberSaveable(product.id) { mutableStateOf<String?>(null) }
    var options by rememberSaveable(product.id, stateSaver = optionsSaver) { mutableStateOf(emptyMap<String, Int>()) }
    var quantity by rememberSaveable(product.id) { mutableIntStateOf(1) }

    val selection = ItemSelection(product.id, sizeId, options.filterValues { it > 0 })
    val violations = validate(selection, product, catalog)
    val price = if (violations.isEmpty()) unitPrice(selection, catalog) else null

    LazyColumn(contentPadding = padding, modifier = Modifier.padding(horizontal = 16.dp)) {
        if (product.sizes.isNotEmpty()) {
            item { SectionTitle("Size") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    product.sizes.forEach { size ->
                        FilterChip(
                            selected = sizeId == size.id,
                            onClick = { sizeId = size.id },
                            label = { Text(if (size.extraPrice > 0) "${size.name} +${taka(size.extraPrice)}" else size.name) },
                        )
                    }
                }
            }
        }

        for (groupId in product.groupIds) {
            val group = catalog.group(groupId) ?: continue
            item(key = "group-$groupId") { SectionTitle(group.name + limitsText(group)) }
            items(group.options, key = { "option-${it.id}" }) { option ->
                val qty = options[option.id] ?: 0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(option.name)
                        if (option.price > 0) Text("+${taka(option.price)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Stepper(
                        value = qty,
                        onMinus = { options = options + (option.id to qty - 1) },
                        onPlus = { options = options + (option.id to qty + 1) },
                        minusEnabled = qty > 0,
                        plusEnabled = qty < STEPPER_MAX,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(vertical = 12.dp)) {
                violations.forEach {
                    Text(it.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper(
                    value = quantity,
                    onMinus = { quantity-- },
                    onPlus = { quantity++ },
                    minusEnabled = quantity > 1,
                    plusEnabled = true,
                )
                Button(
                    onClick = { onAdd(OrderLine(selection, quantity)) },
                    enabled = violations.isEmpty(),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                ) {
                    Text(if (price != null) "Add · ${taka(price * quantity)}" else "Add")
                }
            }
        }
    }
}

private fun limitsText(group: ModifierGroup): String {
    val parts = listOfNotNull(
        group.max?.let { "up to $it" },
        group.maxDistinct?.let { "$it different" },
        group.maxPerOption?.let { "$it of each" },
    )
    return if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean, plusEnabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMinus, enabled = minusEnabled) { Text("−") }
        Text("$value")
        TextButton(onClick = onPlus, enabled = plusEnabled) { Text("+") }
    }
}
