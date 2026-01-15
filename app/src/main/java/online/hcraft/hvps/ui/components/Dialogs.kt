package online.hcraft.hvps.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.hcraft.hvps.model.HistoryEvent
import online.hcraft.hvps.model.HistoryEventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    currentEnabled: Boolean,
    currentThreshold: Int,
    currentNotifyOffline: Boolean,
    currentNotifyOnline: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int, Boolean, Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(currentEnabled) }
    var threshold by remember { mutableStateOf(currentThreshold.toFloat()) }
    var notifyOffline by remember { mutableStateOf(currentNotifyOffline) }
    var notifyOnline by remember { mutableStateOf(currentNotifyOnline) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Monitoring") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // High CPU Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High CPU Alerts")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
                
                AnimatedVisibility(visible = enabled) {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                        Text(
                            "CPU Threshold: ${threshold.roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = threshold,
                            onValueChange = { threshold = it },
                            valueRange = 10f..100f,
                            steps = 8
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Status Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notify when Offline")
                    Switch(
                        checked = notifyOffline,
                        onCheckedChange = { notifyOffline = it }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notify when Online")
                    Switch(
                        checked = notifyOnline,
                        onCheckedChange = { notifyOnline = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(enabled, threshold.roundToInt(), notifyOffline, notifyOnline) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HistoryDialog(
    events: List<HistoryEvent>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Event History") },
        text = {
            if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No events recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(events) { event ->
                        HistoryItem(event)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            if (events.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onClear()
                        onDismiss()
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun HistoryItem(event: HistoryEvent) {
    val icon = when(event.type) {
        HistoryEventType.INFO -> Icons.Default.Info
        HistoryEventType.WARNING -> Icons.Default.Warning
        HistoryEventType.SUCCESS -> Icons.Default.CheckCircle
        HistoryEventType.ERROR -> Icons.Default.Error
    }
    val color = when(event.type) {
        HistoryEventType.INFO -> MaterialTheme.colorScheme.primary
        HistoryEventType.WARNING -> MaterialTheme.colorScheme.tertiary
        HistoryEventType.SUCCESS -> MaterialTheme.colorScheme.primary
        HistoryEventType.ERROR -> MaterialTheme.colorScheme.error
    }
    
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(event.timestamp))

    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(event.message, style = MaterialTheme.typography.bodyMedium)
            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
