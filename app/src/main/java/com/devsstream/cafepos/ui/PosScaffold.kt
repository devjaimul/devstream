package com.devsstream.cafepos.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScaffold(
    title: String,
    status: StatusUi,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                StatusRow(status)
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

private val stripTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm:ss")

// Server time, menu version and sync state, on every screen.
@Composable
private fun StatusRow(status: StatusUi) {
    val now = status.now ?: return
    val c = MaterialTheme.colorScheme
    val zone = status.zone
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val time = if (zone != null) now.time.atZone(zone).format(stripTime) else now.time.toString()
            Pill(time, c.surfaceContainerHigh, c.onSurfaceVariant, Icons.Filled.DateRange)
            Pill(status.menuVersion?.let { "Menu v$it" } ?: "No menu yet", c.surfaceContainerHigh, c.onSurfaceVariant)
            if (status.notSynced == 0) {
                Pill("All synced", c.tertiaryContainer, c.onTertiaryContainer, Icons.Filled.Check)
            } else {
                Pill("${status.notSynced} not synced", c.secondaryContainer, c.onSecondaryContainer, Icons.Filled.Refresh)
            }
        }
        if (!now.confirmed) {
            Surface(
                color = c.errorContainer,
                contentColor = c.onErrorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        "Time not confirmed: no server reply since the phone started. Sales made now are marked.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
