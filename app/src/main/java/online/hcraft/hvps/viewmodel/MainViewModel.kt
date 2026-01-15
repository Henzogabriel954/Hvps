package online.hcraft.hvps.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import online.hcraft.hvps.model.AccountData
import online.hcraft.hvps.model.HistoryEvent
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.repository.ServerRepository
import online.hcraft.hvps.service.MonitoringService
import online.hcraft.hvps.utils.SettingsManager
import online.hcraft.hvps.utils.TokenManager

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val repository = ServerRepository()

    var serverList by mutableStateOf<List<VpsServer>>(emptyList())
        private set

    var selectedServer by mutableStateOf<VpsServer?>(null)
        private set

    // Detailed server object containing state/stats
    var serverDetail by mutableStateOf<VpsServer?>(null)
        private set

    var userData by mutableStateOf<AccountData?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isAuthenticated by mutableStateOf(false)
        private set

    // Settings State for Selected Server
    var isNotificationEnabled by mutableStateOf(false)
        private set
    var cpuThreshold by mutableStateOf(85)
        private set
    var isNotifyOffline by mutableStateOf(false)
        private set
    var isNotifyOnline by mutableStateOf(false)
        private set
        
    // History
    var historyEvents by mutableStateOf<List<HistoryEvent>>(emptyList())
        private set

    private val _notificationEvents = MutableSharedFlow<Pair<String, String>>()
    val notificationEvents = _notificationEvents.asSharedFlow()

    private var pollingJob: Job? = null
    // To prevent spamming notifications every few seconds
    private var lastNotificationTime = 0L

    init {
        checkAuth()
    }

    private fun checkAuth() {
        val token = TokenManager.getToken()
        if (!token.isNullOrBlank()) {
            // Verify token by fetching account
            fetchAccountInfo()
        } else {
            isAuthenticated = false
        }
    }

    fun login(apiKey: String) {
        if (apiKey.isBlank()) {
            error = "API Key cannot be empty"
            return
        }
        TokenManager.saveToken(apiKey)
        fetchAccountInfo()
    }

    private fun fetchAccountInfo() {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            val result = repository.getAccount()
            
            result.onSuccess { data ->
                userData = data
                isAuthenticated = true
                fetchServers()
            }.onFailure { e ->
                if (e.message?.contains("401") == true) {
                    logout()
                    error = "Session expired or invalid key."
                } else {
                     if (TokenManager.getToken() != null) {
                         // Keep session if it's just a network error
                         isAuthenticated = true
                         error = "Network error: ${e.message}"
                     } else {
                         isAuthenticated = false
                         error = "Login failed: ${e.message}"
                     }
                }
            }
            isLoading = false
        }
    }

    fun logout() {
        TokenManager.clearToken()
        isAuthenticated = false
        userData = null
        serverList = emptyList()
        selectedServer = null
        serverDetail = null
        pollingJob?.cancel()
        stopMonitoringService()
    }

    fun fetchServers() {
        viewModelScope.launch {
            isLoading = true
            val result = repository.getServers()
            
            result.onSuccess { data ->
                serverList = data
                // Auto-select first if available and none selected
                if (serverList.isNotEmpty() && selectedServer == null) {
                   selectServer(serverList.first())
                }
                refreshServiceState()
            }.onFailure { e ->
                error = "Failed to load servers. Check API Key or Connection."
            }
            isLoading = false
        }
    }

    fun selectServer(server: VpsServer) {
        selectedServer = server
        serverDetail = null // Clear old details to show loading state if desired, or keep generic info
        
        loadServerSettings(server.id)
        startPolling(server.id)
    }
    
    private fun loadServerSettings(serverId: String) {
        isNotificationEnabled = settingsManager.getNotificationEnabled(serverId)
        cpuThreshold = settingsManager.getCpuThreshold(serverId)
        isNotifyOffline = settingsManager.getNotifyOffline(serverId)
        isNotifyOnline = settingsManager.getNotifyOnline(serverId)
        historyEvents = settingsManager.getHistoryEvents(serverId)
    }

    fun updateSettings(enabled: Boolean, threshold: Int, notifyOffline: Boolean, notifyOnline: Boolean) {
        val serverId = selectedServer?.id ?: return
        isNotificationEnabled = enabled
        cpuThreshold = threshold
        isNotifyOffline = notifyOffline
        isNotifyOnline = notifyOnline
        
        settingsManager.setNotificationEnabled(serverId, enabled)
        settingsManager.setCpuThreshold(serverId, threshold)
        settingsManager.setNotifyOffline(serverId, notifyOffline)
        settingsManager.setNotifyOnline(serverId, notifyOnline)
        
        // After updating settings, check if we should run the service
        refreshServiceState()
    }
    
    fun refreshHistory() {
        val serverId = selectedServer?.id ?: return
        historyEvents = settingsManager.getHistoryEvents(serverId)
    }
    
    fun clearHistory() {
        val serverId = selectedServer?.id ?: return
        settingsManager.clearHistory(serverId)
        historyEvents = emptyList()
    }

    private fun refreshServiceState() {
        val monitoredIds = settingsManager.getMonitoredServerIds()
        if (monitoredIds.isNotEmpty()) {
            startMonitoringService()
        } else {
            stopMonitoringService()
        }
    }

    private fun startMonitoringService() {
        val context = getApplication<Application>()
        val intent = Intent(context, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, MonitoringService::class.java))
    }

    private fun startPolling(serverId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val result = repository.getServerDetails(serverId)
                
                result.onSuccess { data ->
                    serverDetail = data
                    checkCpuThreshold(data)
                    historyEvents = settingsManager.getHistoryEvents(serverId)
                }.onFailure { e ->
                    if (e.message?.contains("429") == true) {
                        delay(5000) // Rate limit backoff
                    }
                }
                
                delay(5000) // Optimization: Increased from 2s to 5s to reduce load/battery usage
            }
        }
    }

    private fun checkCpuThreshold(server: VpsServer) {
        if (!isNotificationEnabled) return

        val cpuStr = server.state?.cpu?.replace("%", "")?.trim() ?: return
        val cpuVal = cpuStr.toFloatOrNull() ?: return

        if (cpuVal >= cpuThreshold) {
            val currentTime = System.currentTimeMillis()
            // Throttle notifications: only one every 5 minutes per session or logic
            if (currentTime - lastNotificationTime > 5 * 60 * 1000) {
                viewModelScope.launch {
                    _notificationEvents.emit("High CPU Alert" to "${server.name} is using ${server.state.cpu} CPU")
                    lastNotificationTime = currentTime
                }
            }
        }
    }

    fun sendPowerSignal(action: String) {
        val id = selectedServer?.id ?: return
        viewModelScope.launch {
            val result = repository.powerAction(id, action)
            
            result.onSuccess {
                 // Force immediate refresh
                 val details = repository.getServerDetails(id)
                 details.onSuccess { serverDetail = it }
            }.onFailure { e ->
                error = "Power action failed: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}