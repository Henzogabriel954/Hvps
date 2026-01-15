package online.hcraft.hvps.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import online.hcraft.hvps.model.AccountData
import online.hcraft.hvps.model.HistoryEvent
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.ui.components.HistoryDialog
import online.hcraft.hvps.ui.components.ServerDetailContent
import online.hcraft.hvps.ui.components.SettingsDialog
import online.hcraft.hvps.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    requestPermission: () -> Unit
) {
    val context = LocalContext.current
    val isAuthenticated = viewModel.isAuthenticated
    val serverList = viewModel.serverList
    val selectedServer = viewModel.selectedServer
    val serverDetail = viewModel.serverDetail
    val userData = viewModel.userData
    val historyEvents = viewModel.historyEvents
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    
    // Listen for notification events
    LaunchedEffect(Unit) {
        viewModel.notificationEvents.collectLatest { (title, message) ->
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val builder = NotificationCompat.Builder(context, "hvps_alerts")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
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

    if (isAuthenticated) {
        LaunchedEffect(serverList) {
            if (selectedServer == null && serverList.isNotEmpty()) {
                viewModel.selectServer(serverList.first())
            }
        }

        if (selectedServer != null) {
            val currentDetail = serverDetail ?: selectedServer
            MainDrawerNav(
                userData = userData,
                servers = serverList,
                selectedServer = currentDetail,
                notificationEnabled = viewModel.isNotificationEnabled,
                cpuThreshold = viewModel.cpuThreshold,
                notifyOffline = viewModel.isNotifyOffline,
                notifyOnline = viewModel.isNotifyOnline,
                historyEvents = historyEvents,
                onSelectServer = { viewModel.selectServer(it) },
                onLogout = { viewModel.logout() },
                onPowerAction = { viewModel.sendPowerSignal(it) },
                onUpdateSettings = { enabled, threshold, nOffline, nOnline -> 
                    if (enabled || nOffline || nOnline) requestPermission()
                    viewModel.updateSettings(enabled, threshold, nOffline, nOnline) 
                },
                onClearHistory = { viewModel.clearHistory() }
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
            error = error,
            isLoading = isLoading
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
    onUpdateSettings: (Boolean, Int, Boolean, Boolean) -> Unit,
    onClearHistory: () -> Unit
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
            onDismiss = { showHistoryDialog = false },
            onClear = onClearHistory
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
                        IconButton(onClick = { showHistoryDialog = true }) {
                            Icon(Icons.Default.History, "Server History")
                        }
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
