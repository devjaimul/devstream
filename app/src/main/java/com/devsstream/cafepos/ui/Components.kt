package com.devsstream.cafepos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devsstream.cafepos.data.local.OrderStatus

@Composable
fun Pill(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun OrderStatusPill(status: OrderStatus) {
    val c = MaterialTheme.colorScheme
    when (status) {
        OrderStatus.PAYING -> Pill("Paying", c.primaryContainer, c.onPrimaryContainer)
        OrderStatus.PENDING -> Pill("Not synced", c.secondaryContainer, c.onSecondaryContainer)
        OrderStatus.SYNCED -> Pill("Synced", c.tertiaryContainer, c.onTertiaryContainer)
        OrderStatus.FAILED -> Pill("Sync failed", c.errorContainer, c.onErrorContainer)
        OrderStatus.CANCELLED -> Pill("Cancelled", c.surfaceVariant, c.onSurfaceVariant)
    }
}

@Composable
fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean, plusEnabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(36.dp)) { Text("−") }
        Text(
            "$value",
            Modifier.width(32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(36.dp)) { Text("+") }
    }
}

// First letter of the name on its category's colour. The colour comes from the
// category's position in the catalog, so a new catalog needs no code change.
@Composable
fun Initial(name: String, colourIndex: Int, size: Int = 44) {
    val c = MaterialTheme.colorScheme
    val colours = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
    )
    val (bg, fg) = colours[Math.floorMod(colourIndex, colours.size)]
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    MENU("menu", "Menu", Icons.Filled.Home),
    ORDERS("orders", "Orders", Icons.AutoMirrored.Filled.List),
    SUMMARY("summary", "Summary", Icons.Filled.DateRange),
    SERVER("debug", "Server", Icons.Filled.Settings),
}

@Composable
fun PosTabs(current: Tab, notSynced: Int, onTab: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onTab(tab) },
                label = { Text(tab.label) },
                icon = {
                    if (tab == Tab.ORDERS && notSynced > 0) {
                        BadgedBox(badge = { Badge { Text("$notSynced") } }) { Icon(tab.icon, contentDescription = null) }
                    } else {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
            )
        }
    }
}
