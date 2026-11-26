package com.devsstream.cafepos.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.time.businessDay
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.summary.DaySummary
import com.devsstream.cafepos.summary.SeedLoader
import com.devsstream.cafepos.summary.SeedResult
import com.devsstream.cafepos.summary.summarize
import com.devsstream.cafepos.time.ServerClock
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.taka
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SummaryUi(
    val summary: DaySummary? = null,
    val catalog: Catalog? = null,
    val message: String? = null,
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    orders: OrderRepository,
    catalogs: CatalogRepository,
    private val clock: ServerClock,
    private val seed: SeedLoader,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    // the day the cashier picked, or today's business day by server time
    private val day = combine(savedState.getStateFlow<String?>(DAY, null), catalogs.catalog) { picked, catalog ->
        picked?.let(LocalDate::parse) ?: catalog?.let { clock.now().time.businessDay(it.timeZone) }
    }.filterNotNull().distinctUntilChanged()

    // straight from Room, so it changes the moment a sale is saved or synced
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SummaryUi> = combine(
        day.flatMapLatest { d -> orders.observeDay(d).map { summarize(d, it) } },
        catalogs.catalog,
        message,
    ) { summary, catalog, msg -> SummaryUi(summary, catalog, msg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUi())

    fun moveDay(days: Long) {
        val current = state.value.summary?.day ?: return
        savedState[DAY] = current.plusDays(days).toString()
    }

    fun today() {
        savedState[DAY] = null
    }

    fun loadSeed() {
        viewModelScope.launch {
            message.value = when (val result = seed.loadAppendixB()) {
                is SeedResult.Loaded -> {
                    savedState[DAY] = result.day.toString()
                    if (result.added > 0) "Loaded ${result.added} Appendix B orders" else "Appendix B is already loaded"
                }
                is SeedResult.WrongCatalog -> "Appendix B is for catalog v${result.needed}, the menu is v${result.current}"
                SeedResult.NoCatalog -> "No menu yet, try again in a moment"
            }
        }
    }

    private companion object {
        const val DAY = "day"
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

@Composable
fun SummaryScreen(status: StatusUi, tabs: @Composable () -> Unit, vm: SummaryViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val c = MaterialTheme.colorScheme

    PosScaffold(title = "Sales summary", status = status, bottomBar = tabs) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val summary = ui.summary
            if (summary == null) {
                Text("Loading…")
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { vm.moveDay(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(summary.day.format(dayFormat), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = vm::today) { Text("Today") }
                }
                FilledTonalIconButton(onClick = { vm.moveDay(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }

            Surface(color = c.primary, contentColor = c.onPrimary, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Total payable", style = MaterialTheme.typography.labelLarge)
                    Text(taka(summary.payable), style = MaterialTheme.typography.displaySmall)
                    Text("${summary.orders} paid orders", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Cash", taka(summary.cash), c.secondaryContainer, c.onSecondaryContainer, Modifier.weight(1f))
                Tile("Card", taka(summary.card), c.primaryContainer, c.onPrimaryContainer, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Top item", topItemText(summary, ui.catalog), c.surfaceContainerLowest, c.onSurface, Modifier.weight(1f))
                if (summary.notSynced == 0) {
                    Tile("Not synced", "0", c.tertiaryContainer, c.onTertiaryContainer, Modifier.weight(1f))
                } else {
                    Tile("Not synced", "${summary.notSynced}", c.errorContainer, c.onErrorContainer, Modifier.weight(1f))
                }
            }
            Text(
                "Business day in server time (Asia/Dhaka). Orders still being paid, or cancelled, don't count.",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )

            ui.message?.let { Text(it, color = c.primary) }
            OutlinedButton(onClick = vm::loadSeed, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text("Load Appendix B seed")
            }
        }
    }
}

private fun topItemText(summary: DaySummary, catalog: Catalog?): String {
    if (summary.topItems.isEmpty()) return "-"
    val names = summary.topItems.joinToString { id -> catalog?.product(id)?.name ?: id }
    val each = if (summary.topItems.size > 1) " each" else ""
    return "$names\n${summary.topUnits} units$each"
}

@Composable
private fun Tile(label: String, value: String, container: Color, content: Color, modifier: Modifier) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
