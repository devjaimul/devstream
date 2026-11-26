package com.devsstream.cafepos.ui.cart

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.parseTaka
import com.devsstream.cafepos.ui.taka

private enum class Tender(val label: String) { CASH("Cash"), CARD("Card"), SPLIT("Card + cash") }

// D4: a split charges the card part first, the cash for the rest is taken on the order screen.
@OptIn(ExperimentalMaterial3Api::class)
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
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (price == null) {
                Text("Nothing to pay.")
                return@Column
            }
            val payable = price.payable
            val busy = ui.cart.busy

            Text("Payable", style = MaterialTheme.typography.labelLarge)
            Text(taka(payable), style = MaterialTheme.typography.displaySmall)
            if (ui.cart.priceChanged) {
                Text(
                    "The price changed just now (happy hour, coupon or menu). Check the new total and tap again.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ui.cart.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tender.entries.forEach {
                    FilterChip(selected = tender == it, onClick = { tender = it }, label = { Text(it.label) })
                }
            }

            when (tender) {
                Tender.CASH -> {
                    val tendered = parseTaka(tenderedText)
                    TakaField("Cash tendered", tenderedText) { tenderedText = it }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickCash(payable).forEach { amount ->
                            OutlinedButton(onClick = { tenderedText = (amount / 100).toString() }) { Text(taka(amount)) }
                        }
                    }
                    if (tendered != null && tendered >= payable) Text("Change ${taka(tendered - payable)}")
                    Button(
                        onClick = { onCash(tendered!!) },
                        enabled = !busy && tendered != null && tendered >= payable,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Take cash") }
                }

                Tender.CARD -> Button(onClick = { onCard(payable) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Charge card ${taka(payable)}")
                }

                Tender.SPLIT -> {
                    val card = parseTaka(cardText)
                    val ok = card != null && card > 0 && card < payable
                    TakaField("Card part", cardText) { cardText = it }
                    if (ok) Text("Then ${taka(payable - card!!)} in cash, once the card is approved")
                    Button(onClick = { onCard(card!!) }, enabled = !busy && ok, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ok) "Charge card ${taka(card!!)} first" else "Charge card first")
                    }
                }
            }
        }
    }
}

@Composable
fun TakaField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(7)) },
        label = { Text("$label (৳)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

// exact, then the next round notes up
private fun quickCash(payable: Long): List<Long> =
    listOf(payable, roundUp(payable, 10_000), roundUp(payable, 50_000), roundUp(payable, 100_000)).distinct()

private fun roundUp(amount: Long, step: Long) = (amount + step - 1) / step * step
