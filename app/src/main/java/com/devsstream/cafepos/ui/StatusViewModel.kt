package com.devsstream.cafepos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.time.ServerClock
import com.devsstream.cafepos.time.ServerNow
import com.devsstream.cafepos.time.ticks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class StatusUi(
    val now: ServerNow? = null,
    val zone: ZoneId? = null,
    val menuVersion: Int? = null,
    val notSynced: Int = 0,
    val unfinished: Int = 0,
)

// What the top bar shows on every screen: server time, menu version, unsynced count.
@HiltViewModel
class StatusViewModel @Inject constructor(
    private val catalogs: CatalogRepository,
    clock: ServerClock,
    orders: OrderRepository,
) : ViewModel() {

    val status: StateFlow<StatusUi> =
        combine(
            clock.ticks(),
            catalogs.catalog,
            orders.observeNotSyncedCount(),
            orders.observeUnfinishedCount(),
        ) { now, catalog, notSynced, unfinished ->
            StatusUi(now, catalog?.timeZone, catalog?.version, notSynced, unfinished)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUi())

    init {
        // keeps the menu and the server time anchor fresh while the app is open
        viewModelScope.launch {
            while (true) {
                catalogs.refresh()
                delay(if (catalogs.catalog.value == null) 3.seconds else 30.seconds)
            }
        }
    }
}
