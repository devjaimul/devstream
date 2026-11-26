package com.devsstream.cafepos.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.remote.PaymentType
import com.devsstream.cafepos.ui.BigButton
import com.devsstream.cafepos.ui.Breakdown
import com.devsstream.cafepos.ui.OrderStatusPill
import com.devsstream.cafepos.ui.Pill
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.cart.ChangeCard
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
    val c = MaterialTheme.colorScheme

    PosScaffold(title = details?.order?.receiptNo ?: "Order", status = status, onBack = onDone) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (details == null) {
                Text("Loading…")
                return@Column
            }
            ui.message?.let { Text(it, color = c.error) }

            when (val step = ui.step) {
                PaymentStep.WaitingForCard -> Hero(c.secondaryContainer, c.onSecondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(32.dp), color = c.secondary)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("Waiting for the card terminal", style = MaterialTheme.typography.titleMedium)
                            Text("If it doesn't answer in 15 s, the app asks the terminal", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                is PaymentStep.CheckingCard -> Hero(c.secondaryContainer, c.onSecondaryContainer) {
                    Text("Checking the last card with the terminal…", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { vm.checkAgain(step.ref) }) { Text("Check again") }
                }

                is PaymentStep.CardUnknown -> CardUnknown(step.ref, vm)

                is PaymentStep.Due -> Due(step, details, vm)

                PaymentStep.Paid -> Paid(details, vm, onDone)

                PaymentStep.Cancelled -> Hero(c.surfaceVariant, c.onSurfaceVariant) {
                    Text("Cancelled", style = MaterialTheme.typography.headlineSmall)
                    Text("Kept for the record, never sent to the server.")
                    BigButton("Back to menu", onClick = onDone)
                }

                null -> Unit
            }

            Receipt(details, ui.catalog?.timeZone ?: status.zone, ui)
        }
    }
}

@Composable
private fun Hero(container: Color, content: Color, body: @Composable ColumnScope.() -> Unit) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
    }
}

@Composable
private fun CardUnknown(ref: String, vm: OrderViewModel) {
    val c = MaterialTheme.colorScheme
    var confirm by rememberSaveable { mutableStateOf(false) }
    Hero(c.errorContainer, c.onErrorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text("Card result unknown", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
        Text("Don't charge again until you know. Look at the terminal or its slip.")
        BigButton("Check again", onClick = { vm.checkAgain(ref) })
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Not charged, try again") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Mark as not charged?") },
            text = {
                Text("Only if the terminal or its slip shows this card was NOT charged. The app asks the terminal once more first. A new attempt is a new charge.")
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.notCharged(ref) }) { Text("Not charged") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Go back") } },
        )
    }
}

@Composable
private fun Due(step: PaymentStep.Due, details: OrderWithDetails, vm: OrderViewModel) {
    var tenderedText by rememberSaveable { mutableStateOf("") }
    val tendered = parseTaka(tenderedText)
    val nothingTaken = details.payments.none { it.status == PaymentStatus.APPROVED }
    val c = MaterialTheme.colorScheme

    Hero(c.surfaceContainerLowest, c.onSurface) {
        val last = step.lastCard
        when (last?.status) {
            PaymentStatus.DECLINED -> Pill("Card declined" + (last.declineReason?.let { ": $it" } ?: ""), c.errorContainer, c.onErrorContainer)
            PaymentStatus.VOIDED -> Pill("Last card marked as not charged", c.errorContainer, c.onErrorContainer)
            PaymentStatus.APPROVED -> Pill("Card part approved", c.tertiaryContainer, c.onTertiaryContainer, Icons.Filled.CheckCircle)
            else -> Unit
        }
        Text("Still to pay", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        Text(taka(step.amount), style = MaterialTheme.typography.displaySmall)

        BigButton("Card for ${taka(step.amount)}", onClick = { vm.anotherCard(step.amount) })
        TakaField("Cash tendered", tenderedText) { tenderedText = it }
        if (tendered != null && tendered >= step.amount) ChangeCard(tendered - step.amount)
        OutlinedButton(
            onClick = { vm.payRestInCash(tendered!!) },
            enabled = tendered != null && tendered >= step.amount,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Take cash for ${taka(step.amount)}") }

        if (nothingTaken) {
            OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel sale", color = c.error) }
        }
    }
}

@Composable
private fun Paid(details: OrderWithDetails, vm: OrderViewModel, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val failed = details.order.status == OrderStatus.FAILED
    Hero(c.tertiaryContainer, c.onTertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, Modifier.size(32.dp))
            Text("Done", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 12.dp))
        }
        details.payments.lastOrNull { it.type == PaymentType.CASH && it.status == PaymentStatus.APPROVED }?.change?.let {
            if (it > 0) Text("Give back ${taka(it)} change", style = MaterialTheme.typography.titleLarge)
        }
        Text(syncText(details.order), color = if (failed) c.error else c.onTertiaryContainer)
        if (failed) OutlinedButton(onClick = vm::retrySync) { Text("Retry") }
        BigButton("New sale", onClick = onDone)
    }
}

@Composable
private fun Receipt(details: OrderWithDetails, zone: java.time.ZoneId?, ui: OrderUi) {
    val c = MaterialTheme.colorScheme
    val order = details.order
    Card(
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLowest),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.receiptNo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OrderStatusPill(order.status)
            }
            val time = Instant.ofEpochMilli(order.createdAt)
            Text(if (zone != null) formatTime(time, zone) else time.toString(), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            if (!order.timeConfirmed) Pill("Time not confirmed", c.errorContainer, c.onErrorContainer, Icons.Filled.Warning)
            Breakdown(orderRows(details, ui.catalog), Modifier.padding(top = 8.dp))

            if (details.payments.isNotEmpty()) {
                Text("Payments", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                details.payments.sortedBy { it.id }.forEach { p ->
                    val extra = when (p.type) {
                        PaymentType.CASH -> "tendered ${taka(p.tendered ?: 0)}, change ${taka(p.change ?: 0)}"
                        PaymentType.CARD -> listOfNotNull(p.authCode, p.declineReason).joinToString(" ")
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.type} ${taka(p.amount)}", style = MaterialTheme.typography.bodyMedium)
                            if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                        PaymentPill(p.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentPill(status: PaymentStatus) {
    val c = MaterialTheme.colorScheme
    when (status) {
        PaymentStatus.APPROVED -> Pill("Approved", c.tertiaryContainer, c.onTertiaryContainer)
        PaymentStatus.PENDING -> Pill("Waiting", c.secondaryContainer, c.onSecondaryContainer)
        PaymentStatus.UNKNOWN -> Pill("Unknown", c.errorContainer, c.onErrorContainer)
        PaymentStatus.DECLINED -> Pill("Declined", c.errorContainer, c.onErrorContainer)
        PaymentStatus.VOIDED -> Pill("Not charged", c.surfaceVariant, c.onSurfaceVariant)
    }
}
