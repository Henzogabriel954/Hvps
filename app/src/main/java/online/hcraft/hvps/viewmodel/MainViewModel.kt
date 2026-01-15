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
import online.hcraft.hvps.network.RetrofitClient
import online.hcraft.hvps.service.MonitoringService
import online.hcraft.hvps.utils.SettingsManager
import online.hcraft.hvps.utils.TokenManager
import retrofit2.HttpException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

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
    // To prevent spamming notifications every 2 seconds
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
            try {
                val accountResponse = RetrofitClient.api.getAccount()
                userData = accountResponse.data
                isAuthenticated = true
                fetchServers()
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) {
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
                e.printStackTrace()
            } finally {
                isLoading = false
            }
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
            try {
                val response = RetrofitClient.api.getServers(results = 50)
                serverList = response.data
                // Auto-select first if available
                if (serverList.isNotEmpty() && selectedServer == null) {
                   selectServer(serverList.first())
                }
                // Refresh service state based on potentially new data (though data is persistent)
                refreshServiceState()
            } catch (e: Exception) {
                error = "Failed to load servers. Check API Key or Connection."
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun selectServer(server: VpsServer) {
        selectedServer = server
        serverDetail = null // Clear old details
        
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
                try {
                    // Fetch details with state=true
                    val response = RetrofitClient.api.getServerDetails(serverId, state = true)
                    // Update detail
                    serverDetail = response.data

                    // Check Notification Logic (In-App only for immediate feedback)
                    checkCpuThreshold(response.data)
                    
                    // Also refresh history periodically in case background service added something
                    historyEvents = settingsManager.getHistoryEvents(serverId)
                } catch (e: Exception) {
                    if (e is HttpException && e.code() == 429) {
                        println("Rate limit hit, backing off...")
                        delay(5000) // Wait longer if rate limited
                    } else {
                        println("Polling error: ${e.message}")
                    }
                }
                delay(2000) // 2s polling interval to be safe
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
            try {
                val response = when(action) {
                    "start" -> RetrofitClient.api.bootServer(id)
                    "restart" -> RetrofitClient.api.restartServer(id)
                    "stop" -> RetrofitClient.api.shutdownServer(id)
                    "kill" -> RetrofitClient.api.powerOffServer(id) // Brutal kill
                    else -> null
                }
                
                if (response == null) return@launch

                if (!response.isSuccessful) {
                    error = "Power action failed: ${response.code()}"
                } else {
                    // Force immediate refresh
                    val details = RetrofitClient.api.getServerDetails(id, state = true)
                    serverDetail = details.data
                    // History update will happen on next poll or via service
                }
            } catch (e: Exception) {
                error = "Power action error: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}