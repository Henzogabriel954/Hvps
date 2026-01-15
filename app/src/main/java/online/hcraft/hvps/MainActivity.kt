package online.hcraft.hvps

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import online.hcraft.hvps.model.AccountData
import online.hcraft.hvps.model.HistoryEvent
import online.hcraft.hvps.model.HistoryEventType
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.ui.theme.HvpsTheme
import online.hcraft.hvps.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result logic if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContent {
            HvpsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(requestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                             requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "High Usage Alerts"
            val descriptionText = "Notifications for high CPU usage on VPS"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("hvps_alerts", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun AppContent(
    viewModel: MainViewModel = viewModel(),
    requestPermission: () -> Unit
) {
    val context = LocalContext.current
    
    // Listen for notification events
    LaunchedEffect(Unit) {
        viewModel.notificationEvents.collectLatest { (title, message) ->
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val builder = NotificationCompat.Builder(context, "hvps_alerts")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert) // Replace with app icon
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                with(NotificationManagerCompat.from(context)) {
                    notify(System.currentTimeMillis().toInt(), builder.build())
                }
            }
        }
    }

    if (viewModel.isAuthenticated) {
        val selectedServer = viewModel.selectedServer

        LaunchedEffect(viewModel.serverList) {
            if (viewModel.selectedServer == null && viewModel.serverList.isNotEmpty()) {
                viewModel.selectServer(viewModel.serverList.first())
            }
        }

        if (selectedServer != null) {
            val currentDetail = viewModel.serverDetail ?: selectedServer
            MainDrawerNav(
                userData = viewModel.userData,
                servers = viewModel.serverList,
                selectedServer = currentDetail,
                notificationEnabled = viewModel.isNotificationEnabled,
                cpuThreshold = viewModel.cpuThreshold,
                notifyOffline = viewModel.isNotifyOffline,
                notifyOnline = viewModel.isNotifyOnline,
                historyEvents = viewModel.historyEvents,
                onSelectServer = { viewModel.selectServer(it) },
                onLogout = { viewModel.logout() },
                onPowerAction = { viewModel.sendPowerSignal(it) },
                onUpdateSettings = { enabled, threshold, nOffline, nOnline -> 
                    if (enabled || nOffline || nOnline) requestPermission()
                    viewModel.updateSettings(enabled, threshold, nOffline, nOnline) 
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    } else {
        LoginScreen(
            onLogin = { key -> viewModel.login(key) },
            error = viewModel.error,
            isLoading = viewModel.isLoading
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDrawerNav(
    userData: AccountData?,
    servers: List<VpsServer>,
    selectedServer: VpsServer,
    notificationEnabled: Boolean,
    cpuThreshold: Int,
    notifyOffline: Boolean,
    notifyOnline: Boolean,
    historyEvents: List<HistoryEvent>,
    onSelectServer: (VpsServer) -> Unit,
    onLogout: () -> Unit,
    onPowerAction: (String) -> Unit,
    onUpdateSettings: (Boolean, Int, Boolean, Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        SettingsDialog(
            currentEnabled = notificationEnabled,
            currentThreshold = cpuThreshold,
            currentNotifyOffline = notifyOffline,
            currentNotifyOnline = notifyOnline,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { enabled, threshold, nOffline, nOnline ->
                onUpdateSettings(enabled, threshold, nOffline, nOnline)
                showSettingsDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Settings saved for ${selectedServer.name}")
                }
            }
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            events = historyEvents,
            onDismiss = { showHistoryDialog = false }
        )
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                // User Profile Header in Drawer
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(
                        userData?.name ?: "User",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        userData?.email ?: "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Your Servers",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(servers) { server ->
                        val isSelected = server.id == selectedServer.id
                        NavigationDrawerItem(
                            label = { 
                                Column {
                                    Text(server.name, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                                    Text(
                                        "${server.cpu} • ${server.memory}", 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if(isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            selected = isSelected,
                            icon = { Icon(Icons.Default.Dns, null) },
                            onClick = {
                                onSelectServer(server)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Sign Out") },
                    selected = false,
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                selectedServer.name,
                                maxLines = 1
                            )
                            if (userData != null) {
                                Text(
                                    userData.email,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        // History Button
                        IconButton(onClick = { showHistoryDialog = true }) {
                            Icon(Icons.Default.History, "Server History")
                        }
                        // Settings Button
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, "Server Settings")
                        }

                        val isRunning = selectedServer.state?.running == true || selectedServer.state?.status == "running"
                        val statusColor = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        val statusText = if (selectedServer.suspended) "SUSPENDED" else (selectedServer.state?.status?.uppercase() ?: "OFFLINE")

                        Surface(
                            shape = CircleShape,
                            color = statusColor.copy(alpha = 0.1f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(statusColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            ServerDetailContent(
                server = selectedServer,
                contentPadding = padding,
                onPowerAction = onPowerAction,
                onCopyIp = { ip ->
                    scope.launch {
                        snackbarHostState.showSnackbar("Copied: $ip")
                    }
                }
            )
        }
    }
}

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
    onDismiss: () -> Unit
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


@Composable
fun ServerDetailContent(
    server: VpsServer,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onPowerAction: (String) -> Unit,
    onCopyIp: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Power Controls at the top for quick access
            PowerControlSection(onPowerAction)
        }

        item {
            SectionHeader("Monitoring")
        }

        item {
            ResourceStatsGrid(server)
        }

        item {
            SectionHeader("Network")
        }

        item {
            NetworkCard(server, onCopyIp)
        }
        
        item {
             Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
    )
}

@Composable
fun PowerControlSection(onAction: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = { onAction("start") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Text("Start")
            }

            FilledTonalButton(
                onClick = { onAction("restart") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Refresh, null)
                Text("Restart")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onAction("stop") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.PowerSettingsNew, null)
                Text("Stop")
            }
            
            Button(
                onClick = { onAction("kill") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Default.Dangerous, null)
                Text("Kill")
            }
        }
    }
}

@Composable
fun ResourceStatsGrid(server: VpsServer) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val cpuDisplay = server.state?.cpu ?: "0%"
        val cpuValue = cpuDisplay.replace("%", "").trim().toFloatOrNull() ?: 0f
        
        ResourceItem(
            icon = Icons.Default.Memory,
            label = "CPU Load",
            value = cpuDisplay,
            progress = (cpuValue / 100f).coerceIn(0f, 1f),
            color = MaterialTheme.colorScheme.primary
        )
        
        ResourceItem(
            icon = Icons.Default.SdStorage,
            label = "Memory (Allocated)",
            value = server.memory,
            progress = null, 
            color = MaterialTheme.colorScheme.secondary
        )
        
        val primaryStorage = server.storage.find { it.primary }?.capacity ?: "N/A"
        ResourceItem(
            icon = Icons.Default.Dns,
            label = "Disk Space (Allocated)",
            value = primaryStorage,
            progress = null,
            color = MaterialTheme.colorScheme.tertiary
        )

        val rx = server.state?.network?.primary?.traffic?.rx ?: 0L
        val tx = server.state?.network?.primary?.traffic?.tx ?: 0L
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                 TrafficItem(
                    icon = Icons.Default.Download,
                    label = "Incoming (RX)",
                    value = formatBytes(rx),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                 TrafficItem(
                    icon = Icons.Default.Upload,
                    label = "Outgoing (TX)",
                    value = formatBytes(tx),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

@Composable
fun TrafficItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResourceItem(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float?,
    color: Color
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                if (progress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = color,
                        trackColor = color.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkCard(server: VpsServer, onCopy: (String) -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val ipv4 = server.network.primary?.ipv4?.firstOrNull()?.address
    val ipv6 = server.network.primary?.ipv6?.firstOrNull()?.addresses?.firstOrNull() // Simplify for display

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (ipv4 != null) {
                NetworkRow("IPv4", ipv4) {
                    clipboardManager.setText(AnnotatedString(ipv4))
                    onCopy(ipv4)
                }
            }
            
            if (ipv4 != null && ipv6 != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            if (ipv6 != null) {
                NetworkRow("IPv6", ipv6) {
                    clipboardManager.setText(AnnotatedString(ipv6))
                    onCopy(ipv6)
                }
            }
        }
    }
}

@Composable
fun NetworkRow(label: String, ip: String, onCopy: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = ip,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.blur(if (isVisible) 0.dp else 10.dp)
            )
        }
        Row {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Hide" else "Show",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, "Copy")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: (String) -> Unit, error: String?, isLoading: Boolean) {
    var apiKey by remember { mutableStateOf("") }
    
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bolt, 
                        null, 
                        modifier = Modifier.size(48.dp), 
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Hvps Control",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Manage your VPS on the go",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                
                AnimatedVisibility(visible = error != null) {
                    if (error != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { onLogin(apiKey) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Connect", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}