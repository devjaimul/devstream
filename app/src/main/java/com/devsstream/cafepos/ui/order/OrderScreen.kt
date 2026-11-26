package com.devsstream.cafepos.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.remote.PaymentType
import com.devsstream.cafepos.ui.Breakdown
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.cart.TakaField
import com.devsstream.cafepos.ui.formatTime
import com.devsstream.cafepos.ui.orderRows
import com.devsstream.cafepos.ui.parseTaka
import com.devsstream.cafepos.ui.taka
import java.time.Instant

@Composable
fun OrderScreen(status: StatusUi, onDone: () -> Unit, vm: OrderViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val details = ui.details

    PosScaffold(title = details?.order?.receiptNo ?: "Order", status = status, onBack = onDone) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (details == null) {
                Text("Loading…")
                return@Column
            }
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (val step = ui.step) {
                PaymentStep.WaitingForCard -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text("Waiting for the card terminal… (asks the terminal after 15 s)")
                }

                is PaymentStep.CheckingCard -> Column {
                    Text("Checking the last card with the terminal…")
                    OutlinedButton(onClick = { vm.checkAgain(step.ref) }) { Text("Check again") }
                }

                is PaymentStep.CardUnknown -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Card result unknown", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text("Don't charge again until you know. Look at the terminal or its slip.")
                    Button(onClick = { vm.checkAgain(step.ref) }) { Text("Check again") }
                    OutlinedButton(onClick = { vm.notCharged(step.ref) }) { Text("Not charged, try again") }
                }

                is PaymentStep.Due -> Due(step, details, vm)

                PaymentStep.Paid -> Paid(details, vm, onDone)

                PaymentStep.Cancelled -> Column {
                    Text("Cancelled", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onDone) { Text("Back to menu") }
                }

                null -> Unit
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val zone = ui.catalog?.timeZone ?: status.zone
                    val time = Instant.ofEpochMilli(details.order.createdAt)
                    Text(if (zone != null) formatTime(time, zone) else time.toString(), style = MaterialTheme.typography.bodySmall)
                    if (!details.order.timeConfirmed) {
                        Text("Time not confirmed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Breakdown(orderRows(details, ui.catalog), Modifier.padding(top = 8.dp))
                    Payments(details)
                }
            }
        }
    }
}

@Composable
private fun Due(step: PaymentStep.Due, details: OrderWithDetails, vm: OrderViewModel) {
    var tenderedText by rememberSaveable { mutableStateOf("") }
    val tendered = parseTaka(tenderedText)
    val nothingTaken = details.payments.none { it.status == PaymentStatus.APPROVED }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val last = step.lastCard
        when (last?.status) {
            PaymentStatus.DECLINED -> Text("Card declined" + (last.declineReason?.let { ": $it" } ?: ""), color = MaterialTheme.colorScheme.error)
            PaymentStatus.VOIDED -> Text("Last card attempt marked as not charged", color = MaterialTheme.colorScheme.error)
            PaymentStatus.APPROVED -> Text("Card part approved")
            else -> Unit
        }
        Text("Due ${taka(step.amount)}", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = { vm.anotherCard(step.amount) }, modifier = Modifier.fillMaxWidth()) {
            Text("Card for ${taka(step.amount)}")
        }
        TakaField("Cash tendered", tenderedText) { tenderedText = it }
        if (tendered != null && tendered >= step.amount) Text("Change ${taka(tendered - step.amount)}")
        Button(
            onClick = { vm.payRestInCash(tendered!!) },
            enabled = tendered != null && tendered >= step.amount,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Take cash for ${taka(step.amount)}") }

        if (nothingTaken) {
            OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel sale") }
        }
    }
}

@Composable
private fun Paid(details: OrderWithDetails, vm: OrderViewModel, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Done", style = MaterialTheme.typography.headlineSmall)
        details.payments.lastOrNull { it.type == PaymentType.CASH && it.status == PaymentStatus.APPROVED }?.change?.let {
            if (it > 0) Text("Change ${taka(it)}", style = MaterialTheme.typography.titleMedium)
        }
        val failed = details.order.status == OrderStatus.FAILED
        Text(syncText(details.order), color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (failed) OutlinedButton(onClick = vm::retrySync) { Text("Retry") }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("New sale") }
    }
}

@Composable
private fun Payments(details: OrderWithDetails) {
    if (details.payments.isEmpty()) return
    Text("Payments", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    details.payments.sortedBy { it.id }.forEach { p ->
        val extra = when (p.type) {
            PaymentType.CASH -> "tendered ${taka(p.tendered ?: 0)}, change ${taka(p.change ?: 0)}"
            PaymentType.CARD -> listOfNotNull(p.authCode, p.declineReason).joinToString(" ")
        }
        Text("${p.type} ${taka(p.amount)} · ${p.status} $extra", style = MaterialTheme.typography.bodySmall)
    }
}
