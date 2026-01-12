package online.hcraft.hvps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.ui.theme.*
import online.hcraft.hvps.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HvpsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent(viewModel: MainViewModel = viewModel()) {
    if (viewModel.isAuthenticated) {
        val selectedServer = viewModel.selectedServer
        
        // Initial automatic selection if list is available but nothing selected
        LaunchedEffect(viewModel.serverList) {
            if (viewModel.selectedServer == null && viewModel.serverList.isNotEmpty()) {
                viewModel.selectServer(viewModel.serverList.first())
            }
        }

        if (selectedServer != null) {
            val currentDetail = viewModel.serverDetail ?: selectedServer
            MainDrawerNav(
                servers = viewModel.serverList,
                selectedServer = currentDetail,
                onSelectServer = { viewModel.selectServer(it) },
                onLogout = { viewModel.logout() },
                onPowerAction = { viewModel.sendPowerSignal(it) }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    } else {
        LoginScreen(
            onLogin = { key -> viewModel.login(key) },
            error = viewModel.error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDrawerNav(
    servers: List<VpsServer>,
    selectedServer: VpsServer,
    onSelectServer: (VpsServer) -> Unit,
    onLogout: () -> Unit,
    onPowerAction: (String) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your Servers", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(servers) { server ->
                        NavigationDrawerItem(
                            label = { Text(server.name) },
                            selected = server.id == selectedServer.id,
                            onClick = {
                                onSelectServer(server)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Logout / Change Key") },
                    selected = false,
                    icon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onLogout() 
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(selectedServer.name, style = MaterialTheme.typography.titleMedium)
                            Text(selectedServer.hostname ?: "VPS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            ServerDetailContent(
                server = selectedServer,
                modifier = Modifier.padding(padding),
                onPowerAction = onPowerAction,
                onCopyIp = { ip ->
                    scope.launch {
                        snackbarHostState.showSnackbar("IP $ip copied!")
                    }
                }
            )
        }
    }
}

@Composable
fun ServerDetailContent(
    server: VpsServer,
    modifier: Modifier = Modifier,
    onPowerAction: (String) -> Unit,
    onCopyIp: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        StatusHeroCard(server)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Resource Usage", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        
        ResourcesCard(server)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Connectivity", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        
        val ip = server.network.primary.ipv4?.firstOrNull()?.address ?: "N/A"
        
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (ip != "N/A") {
                    clipboardManager.setText(AnnotatedString(ip))
                    onCopyIp(ip)
                }
            }
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("IP Address (Tap to copy)", style = MaterialTheme.typography.labelMedium)
                    Text(ip, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Power Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
        
        PowerControlGrid(onPowerAction)
    }
}

@Composable
fun ResourcesCard(server: VpsServer) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val cpuDisplay = server.state?.cpu ?: "0%"
            val cpuValue = cpuDisplay.replace("%", "").trim().toFloatOrNull() ?: 0f
            ResourceProgressBar(
                label = "CPU Usage",
                valueDisplay = cpuDisplay,
                progress = (cpuValue / 100f).coerceIn(0f, 1f),
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RAM Memory", style = MaterialTheme.typography.labelLarge)
                Text(server.memory, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Storage", style = MaterialTheme.typography.labelLarge)
                val primaryStorage = server.storage.find { it.primary }?.capacity ?: "N/A"
                Text(primaryStorage, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ResourceProgressBar(label: String, valueDisplay: String, progress: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueDisplay, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceDim
        )
    }
}

@Composable
fun StatusHeroCard(server: VpsServer) {
    val isRunning = server.state?.running == true
    val stateText = if (server.suspended) "Suspended" else (if (isRunning) "Online" else "Offline")
    val containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stateText.uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
            if (server.state?.cpu != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text("CPU: ${server.state.cpu}") },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = contentColor.copy(alpha = 0.1f),
                        labelColor = contentColor
                    ),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun SpecItem(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PowerControlGrid(onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { onAction("start") }, modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start")
            }
            FilledTonalButton(onClick = { onAction("restart") }, modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Default.Refresh, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restart")
            }
        }
        Button(
            onClick = { onAction("stop") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        ) {
            Icon(Icons.Default.Close, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: (String) -> Unit, error: String?) {
    var apiKey by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        ElevatedCard(modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Welcome", style = MaterialTheme.typography.headlineMedium)
                Text("Manage your DanBot VPS", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedTextField(
                    value = apiKey, 
                    onValueChange = { apiKey = it }, 
                    label = { Text("API Key") }, 
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top=8.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onLogin(apiKey) }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
            }
        }
    }
}
