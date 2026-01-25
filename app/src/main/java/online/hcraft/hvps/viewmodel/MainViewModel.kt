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
import online.hcraft.hvps.model.ServerTask
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.repository.ServerRepository
import online.hcraft.hvps.service.MonitoringService
import online.hcraft.hvps.utils.SettingsManager
import online.hcraft.hvps.utils.TokenManager
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val repository = ServerRepository(settingsManager)

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
        
    // Agent Settings State
    var isAgentEnabled by mutableStateOf(false)
    var agentIp by mutableStateOf("")
    var agentPort by mutableStateOf("8765")
    var agentToken by mutableStateOf("")
        
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
        
        // Load Agent Settings
        isAgentEnabled = settingsManager.isAgentEnabled(serverId)
        agentIp = settingsManager.getAgentIp(serverId)
        agentPort = settingsManager.getAgentPort(serverId)
        agentToken = settingsManager.getAgentToken(serverId)
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
    
    fun saveAgentSettings(enabled: Boolean, ip: String, port: String, token: String) {
        val serverId = selectedServer?.id ?: return
        isAgentEnabled = enabled
        agentIp = ip
        agentPort = port
        agentToken = token
        
        settingsManager.setAgentEnabled(serverId, enabled)
        settingsManager.setAgentIp(serverId, ip)
        settingsManager.setAgentPort(serverId, port)
        settingsManager.setAgentToken(serverId, token)
        
        // Restart polling to apply changes immediately
        startPolling(serverId)
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

    // State to track if we are successfully using the agent or fell back to standard
    private var isUsingAgent = true // Assume we want to use agent if enabled

    private fun startPolling(serverId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                // Fetch Details (Hybrid Logic inside Repository)
                val result = repository.getServerDetails(serverId)
                
                result.onSuccess { data ->
                    serverDetail = data
                    checkCpuThreshold(data)
                    historyEvents = settingsManager.getHistoryEvents(serverId)
                    
                    // If we succeeded via repository (which tries Agent first if enabled), 
                    // we check if we were previously in fallback mode.
                    // However, Repository hides whether it used Agent or Standard in success path 
                    // (unless we inspect data or change return type).
                    // But wait: repository.getServerDetails returns Failure if Agent fails!
                    // So if we are here (Success), it means Agent worked OR Agent was disabled.
                    
                    if (settingsManager.isAgentEnabled(serverId)) {
                        if (!isUsingAgent) {
                            _notificationEvents.emit("Connected" to "Proprietary Agent connected.")
                            isUsingAgent = true
                        }
                    }
                }.onFailure { e ->
                    if (e is ServerRepository.AgentConnectionException) {
                        // Agent failed
                        if (isUsingAgent) {
                             _notificationEvents.emit("Warning" to "Agent unreachable. Using Standard API.")
                             isUsingAgent = false
                        }
                        // Fallback fetch to standard API
                        fetchStandardDetails(serverId)
                    } else if (e.message?.contains("429") == true) {
                        delay(5000) // Rate limit backoff
                    }
                }
                
                val pollingInterval = if (settingsManager.isAgentEnabled(serverId) && isUsingAgent) 15000L else 30000L
                delay(pollingInterval)
            }
        }
    }

    private suspend fun fetchStandardDetails(serverId: String) {
        // We use the repository but we need to bypass the Agent check.
        // Since we can't easily modify the repo signature right now, we will assume 
        // the repository logic will fallback internally OR we can call `fetchServers` to at least refresh the list?
        // No, we want details.
        // Let's assume for this step that we simply retry the `getServerDetails` but we need to temporarily disable the agent check?
        // This is tricky without changing repo signature. 
        // A cleaner way in a real app is `repository.getServerDetails(id, forceStandard = true)`.
        // Since I haven't added that parameter yet, I will modify `ServerRepository` in the next step to support it 
        // OR simply implement a workaround here.
        // Workaround: Call `repository.getServers()` which updates the list, then find the server?
        // That's heavy.
        
        // I will add a `getStandardServerDetails` method to `ServerRepository` in the next step.
        // So here I will call it assuming it exists.
        val result = repository.getStandardServerDetails(serverId)
        result.onSuccess { data ->
             serverDetail = data
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