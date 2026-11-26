package com.devsstream.cafepos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = actions,
                )
                StatusStrip(status)
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
private fun StatusStrip(status: StatusUi) {
    val now = status.now ?: return
    val zone = status.zone
    val time = if (zone != null) formatTime(now.time, zone) else now.time.toString()
    val menu = status.menuVersion?.let { "menu v$it" } ?: "no menu yet"
    val sync = if (status.notSynced > 0) "${status.notSynced} not synced" else "all synced"
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text("Server time $time · $menu · $sync", style = MaterialTheme.typography.bodySmall)
        if (!now.confirmed) {
            Text(
                "Time not confirmed: no server reply since the phone started. Sales made now are marked.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
