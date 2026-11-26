package com.devsstream.cafepos.ui.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.ui.BigButton
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.parseTaka
import com.devsstream.cafepos.ui.taka

private enum class Tender(val label: String, val hint: String) {
    CASH("Cash", "Notes"),
    CARD("Card", "Full amount"),
    SPLIT("Split", "Card, then cash"),
}

// D4: a split charges the card part first, the cash for the rest is taken on the order screen.
@Composable
fun PayScreen(
    ui: CartUi,
    status: StatusUi,
    onCash: (tendered: Long) -> Unit,
    onCard: (amount: Long) -> Unit,
    onBack: () -> Unit,
) {
    var tender by rememberSaveable { mutableStateOf(Tender.CASH) }
    var tenderedText by rememberSaveable { mutableStateOf("") }
    var cardText by rememberSaveable { mutableStateOf("") }

    PosScaffold(title = "Payment", status = status, onBack = onBack) { padding ->
        val price = ui.price
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (price == null) {
                Text("Nothing to pay.")
                return@Column
            }
            val payable = price.payable
            val busy = ui.cart.busy

            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Amount to pay", style = MaterialTheme.typography.labelLarge)
                    Text(taka(payable), style = MaterialTheme.typography.displaySmall)
                    Text("Total ${taka(price.total)}, rounded to whole taka", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (ui.cart.priceChanged) {
                Notice("The price changed just now (happy hour, coupon or menu). Check the new total and tap again.")
            }
            ui.cart.error?.let { Notice(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tender.entries.forEach {
                    TenderCard(it, selected = tender == it, onClick = { tender = it }, modifier = Modifier.weight(1f))
                }
            }

            when (tender) {
                Tender.CASH -> {
                    val tendered = parseTaka(tenderedText)
                    TakaField("Cash tendered", tenderedText) { tenderedText = it }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickCash(payable).forEach { amount ->
                            AssistChip(onClick = { tenderedText = (amount / 100).toString() }, label = { Text(taka(amount)) })
                        }
                    }
                    if (tendered != null && tendered >= payable) ChangeCard(tendered - payable)
                    BigButton("Take cash", onClick = { onCash(tendered!!) }, enabled = !busy && tendered != null && tendered >= payable)
                }

                Tender.CARD -> BigButton("Charge card ${taka(payable)}", onClick = { onCard(payable) }, enabled = !busy)

                Tender.SPLIT -> {
                    val card = parseTaka(cardText)
                    val ok = card != null && card > 0 && card < payable
                    TakaField("Card part", cardText) { cardText = it }
                    if (ok) {
                        Text(
                            "Then ${taka(payable - card!!)} in cash, once the card is approved",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BigButton(if (ok) "Charge card ${taka(card!!)} first" else "Charge card first", onClick = { onCard(card!!) }, enabled = !busy && ok)
                }
            }
        }
    }
}

@Composable
private fun TenderCard(tender: Tender, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (selected) c.primaryContainer else c.surfaceContainerLowest),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(tender.label, style = MaterialTheme.typography.titleSmall)
            Text(tender.hint, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }
}

@Composable
fun ChangeCard(change: Long) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Text("Change", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(taka(change), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(16.dp))
    }
}

@Composable
fun TakaField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(7)) },
        label = { Text("$label (৳)") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

// exact, then the next round notes up
private fun quickCash(payable: Long): List<Long> =
    listOf(payable, roundUp(payable, 10_000), roundUp(payable, 50_000), roundUp(payable, 100_000)).distinct()

private fun roundUp(amount: Long, step: Long) = (amount + step - 1) / step * step
