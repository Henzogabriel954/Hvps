package online.hcraft.hvps.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.network.RetrofitClient
import online.hcraft.hvps.utils.TokenManager

class MainViewModel : ViewModel() {
    var serverList by mutableStateOf<List<VpsServer>>(emptyList())
        private set

    var selectedServer by mutableStateOf<VpsServer?>(null)
        private set

    // Detailed server object containing state/stats
    var serverDetail by mutableStateOf<VpsServer?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isAuthenticated by mutableStateOf(false)
        private set

    private var pollingJob: Job? = null

    init {
        checkAuth()
    }

    private fun checkAuth() {
        val token = TokenManager.getToken()
        if (!token.isNullOrBlank()) {
            isAuthenticated = true
            fetchServers()
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
        isAuthenticated = true
        fetchServers()
    }

    fun logout() {
        TokenManager.clearToken()
        isAuthenticated = false
        serverList = emptyList()
        selectedServer = null
        serverDetail = null
        pollingJob?.cancel()
    }

    fun fetchServers() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val response = RetrofitClient.api.getServers()
                serverList = response.data
                // Auto-select first if available
                if (serverList.isNotEmpty() && selectedServer == null) {
                   selectServer(serverList.first())
                }
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
        startPolling(server.id)
    }

    private fun startPolling(serverId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    // Fetch details with state=true
                    val response = RetrofitClient.api.getServerDetails(serverId, state = true)
                    // Debug log
                    println("API Update: CPU=${response.data.state?.cpu} Status=${response.data.state?.status}")
                    
                    // Force update to trigger UI refresh
                    serverDetail = response.data
                } catch (e: Exception) {
                    println("Polling error: ${e.message}")
                }
                delay(1000) // 1s polling interval for real-time updates
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