package online.hcraft.hvps.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.hcraft.hvps.model.AccountData
import online.hcraft.hvps.model.TaskResponse
import online.hcraft.hvps.model.VpsServer
import online.hcraft.hvps.network.RetrofitClient
import retrofit2.Response

class ServerRepository {
    private val api = RetrofitClient.api

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
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getServerDetails(serverId: String): Result<VpsServer> = withContext(Dispatchers.IO) {
        try {
            val response = api.getServerDetails(serverId, state = true)
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