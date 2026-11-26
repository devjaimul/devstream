package com.devsstream.cafepos.ui.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.ui.PosScaffold
import com.devsstream.cafepos.ui.StatusUi
import com.devsstream.cafepos.ui.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class DebugUi(
    val failures: Failures = Failures.RANDOM,
    val serverTime: Instant? = null,
    val serverOrders: Int = 0,
    val message: String? = null,
)

// The Appendix C test hooks, so the review can break things on purpose.
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val server: FakePosServer,
    private val catalogs: CatalogRepository,
    private val sync: SyncScheduler,
) : ViewModel() {

    private val failures = MutableStateFlow(server.failures)
    private val message = MutableStateFlow<String?>(null)
    private val serverTicks = flow {
        while (true) {
            emit(server.now() to server.orders().size)
            delay(1_000)
        }
    }

    val state: StateFlow<DebugUi> = combine(failures, serverTicks, message) { f, (time, count), msg ->
        DebugUi(f, time, count, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUi())

    // falls back to Dhaka only if there is no catalog yet
    val zone: ZoneId get() = catalogs.catalog.value?.timeZone ?: ZoneId.of("Asia/Dhaka")

    fun setFailures(mode: Failures) {
        server.failures = mode
        failures.value = mode
    }

    // The app's clock follows on the next server reply, so ask for one straight away.
    fun setServerTime(time: LocalDateTime) {
        viewModelScope.launch {
            server.setServerTime(time.atZone(zone).toInstant())
            message.value = if (catalogs.refresh()) "Server time set, the app follows it now" else "Server time set, the app follows on the next reply"
        }
    }

    fun bumpCatalog() {
        viewModelScope.launch {
            message.value = if (!server.bumpCatalog()) {
                "There is no newer catalog"
            } else if (catalogs.refresh()) {
                "Catalog bumped, menu updated"
            } else {
                "Catalog bumped, the menu updates on the next reply"
            }
        }
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            message.value = if (catalogs.refresh()) "Menu refreshed" else "Server not reachable"
        }
    }

    fun syncNow() {
        sync.requestSync()
        message.value = "Sync requested (runs when there is a network)"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(status: StatusUi, onBack: () -> Unit, vm: DebugViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()

    PosScaffold(title = "Fake server", status = status, onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("Server clock: ${ui.serverTime?.let { formatTime(it, vm.zone) } ?: "-"}")
            Text("Orders on the server: ${ui.serverOrders}")

            Text("Failures", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Failures.entries.forEach { mode ->
                    FilterChip(selected = ui.failures == mode, onClick = { vm.setFailures(mode) }, label = { Text(mode.name) })
                }
            }
            Text(
                "RANDOM is Appendix C (20% dropped, 10% saved then 500). For a real \"no network\", turn on airplane mode.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Server time (section 8 times)", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { vm.setServerTime(LocalDateTime.of(2026, 11, 20, 15, 30)) }) { Text("20 Nov 15:30, happy hour") }
            OutlinedButton(onClick = { vm.setServerTime(LocalDateTime.of(2026, 11, 20, 17, 0)) }) { Text("20 Nov 17:00, after happy hour") }
            OutlinedButton(onClick = { vm.setServerTime(LocalDateTime.of(2026, 12, 1, 15, 30)) }) { Text("1 Dec 15:30, coupon expired") }

            Text("Catalog and sync", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = vm::bumpCatalog) { Text("Bump catalog (Latte base 240)") }
            OutlinedButton(onClick = vm::refreshCatalog) { Text("Refresh menu now") }
            OutlinedButton(onClick = vm::syncNow) { Text("Sync now") }
        }
    }
}
