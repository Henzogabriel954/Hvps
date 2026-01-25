package online.hcraft.hvps.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.hcraft.hvps.model.AccountData
import online.hcraft.hvps.model.AgentStatsResponse
import online.hcraft.hvps.model.ServerState
import online.hcraft.hvps.model.ServerTask
import online.hcraft.hvps.model.StorageInfo
import online.hcraft.hvps.model.TaskResponse
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.network.AgentClient
import online.hcraft.hvps.network.RetrofitClient
import online.hcraft.hvps.utils.SettingsManager
import retrofit2.Response

class ServerRepository(private val settingsManager: SettingsManager? = null) {
    private val api = RetrofitClient.api
    private val agentApi = AgentClient.api
    
    // Cache to store base server details to avoid redundant calls when using Agent
    private val cachedServers = mutableMapOf<String, VpsServer>()

    suspend fun getAccount(): Result<AccountData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAccount()
            // Retrofit (with direct object return) throws on 4xx/5xx, so if we are here, it's success.
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getServers(): Result<List<VpsServer>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getServers(results = 100)
            // Update cache
            response.data.forEach { server ->
                cachedServers[server.id] = server
            }
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getServerDetails(serverId: String): Result<VpsServer> = withContext(Dispatchers.IO) {
        // Hybrid Logic: Check if Agent is enabled
        var agentEnabled = false
        if (settingsManager != null) {
            agentEnabled = settingsManager.isAgentEnabled(serverId)
        }

        if (agentEnabled && settingsManager != null) {
            try {
                val ip = settingsManager.getAgentIp(serverId)
                val port = settingsManager.getAgentPort(serverId)
                val token = settingsManager.getAgentToken(serverId)
                val url = "http://$ip:$port/stats"

                val agentStats = agentApi.getStats(url, token)
                
                // Optimisation: Use cached base server if available, otherwise fetch.
                // We rely on the fact that "static" info (name, ip, specs) changes rarely.
                var baseServer = cachedServers[serverId]
                
                // If cache is missing OR state is missing (needed for network info), fetch fresh details
                if (baseServer == null || baseServer.state == null) {
                    val standardResult = api.getServerDetails(serverId, state = true)
                    baseServer = standardResult.data
                    cachedServers[serverId] = baseServer
                }
                
                val mergedServer = mergeAgentData(baseServer, agentStats)
                return@withContext Result.success(mergedServer)

            } catch (e: Exception) {
                // Agent Failed (Timeout or Error) -> Fallback to Standard API
                // We return a specific failure so the ViewModel knows to show a Toast and retry with Standard API.
                return@withContext Result.failure(AgentConnectionException("Agent inaccessible"))
            }
        } else {
            // Standard Path
            try {
                val response = api.getServerDetails(serverId, state = true)
                cachedServers[serverId] = response.data // Update cache
                Result.success(response.data)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getStandardServerDetails(serverId: String): Result<VpsServer> = withContext(Dispatchers.IO) {
        try {
            val response = api.getServerDetails(serverId, state = true)
            cachedServers[serverId] = response.data // Update cache
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mergeAgentData(base: VpsServer, stats: AgentStatsResponse): VpsServer {
        // Convert Agent stats to Strings expected by UI
        val cpuFormatted = "%.2f %%".format(stats.cpu.usagePercent)
        
        val memUsedFormatted = formatBytes(stats.memory.usedBytes)
        val memTotalFormatted = formatBytes(stats.memory.totalBytes)
        val memDisplay = "$memUsedFormatted / $memTotalFormatted"
        val memPercent = stats.memory.usagePercent / 100f

        val diskUsedFormatted = formatBytes(stats.disk.usedBytes)
        val diskTotalFormatted = formatBytes(stats.disk.totalBytes)
        val diskDisplay = "$diskUsedFormatted / $diskTotalFormatted"
        val diskPercent = stats.disk.usagePercent / 100f
        
        val uptimeFormatted = formatUptime(stats.uptimeSeconds)
        
        val newState = base.state?.copy(
            cpu = cpuFormatted,
            running = stats.status == "online",
            memory = memDisplay,
            memoryPercent = memPercent,
            disk = diskDisplay,
            diskPercent = diskPercent,
            uptime = uptimeFormatted
        ) ?: ServerState(
            status = stats.status,
            running = stats.status == "online",
            cpu = cpuFormatted,
            network = null,
            memory = memDisplay,
            memoryPercent = memPercent,
            disk = diskDisplay,
            diskPercent = diskPercent,
            uptime = uptimeFormatted
        )
        
        // Update Memory text in the main object
        return base.copy(
            memory = memTotalFormatted,
            state = newState
        )
    }

    private fun formatUptime(seconds: Long): String {
        val days = seconds / (24 * 3600)
        val hours = (seconds % (24 * 3600)) / 3600
        val minutes = (seconds % 3600) / 60
        
        val sb = StringBuilder()
        if (days > 0) sb.append("${days}d ")
        if (hours > 0 || days > 0) sb.append("${hours}h ")
        sb.append("${minutes}m")
        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.0f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    class AgentConnectionException(message: String) : Exception(message)

    suspend fun getServerTasks(serverId: String): Result<List<ServerTask>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getServerTasks(serverId)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun powerAction(serverId: String, action: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response: Response<TaskResponse> = when (action) {
                "start" -> api.bootServer(serverId)
                "restart" -> api.restartServer(serverId)
                "stop" -> api.shutdownServer(serverId)
                "kill" -> api.powerOffServer(serverId)
                else -> throw IllegalArgumentException("Unknown action: $action")
            }

            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Action failed with code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}