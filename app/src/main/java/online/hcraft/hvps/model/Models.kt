package online.hcraft.hvps.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// --- Account Models ---
data class AccountResponse(
    val data: AccountData
)

data class AccountData(
    val name: String,
    val email: String,
    val timezone: String
)

// --- Server List Models ---
data class ServerListResponse(
    val data: List<VpsServer>,
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("last_page") val lastPage: Int
)

data class VpsServer(
    val id: String,
    val name: String,
    val hostname: String?,
    val memory: String, // Allocated total (e.g., "1024 MB")
    val cpu: String,    // Allocated cores (e.g., "2 Core")
    val suspended: Boolean,
    val network: NetworkInfo,
    val storage: List<StorageInfo>,
    val state: ServerState? = null
)

data class StorageInfo(
    val capacity: String,
    val enabled: Boolean,
    val primary: Boolean
)

data class NetworkInfo(
    val primary: InterfaceInfo?,
    // secondary can be Object or Array ([]), so we use JsonElement to avoid parsing errors
    val secondary: JsonElement? 
)

data class InterfaceInfo(
    val mac: String?,
    val ipv4: List<Ipv4Address>?,
    val ipv6: List<Ipv6Address>?
)

data class Ipv4Address(
    val address: String,
    val gateway: String,
    val netmask: String
)

data class Ipv6Address(
    val subnet: String,
    val gateway: String,
    val addresses: List<String>
)

// --- Server Detail & State ---
data class ServerDetailResponse(
    val data: VpsServer
)

data class ServerState(
    val status: String,
    val running: Boolean,
    val cpu: String?,    // CPU Usage (e.g., "0.3 %")
    val network: StateNetworkInfo?,
    val memory: String? = null,
    val disk: String? = null,
    val memoryPercent: Float? = null,
    val diskPercent: Float? = null,
    val uptime: String? = null
)

data class StateNetworkInfo(
    val primary: StateInterfaceInfo?
)

data class StateInterfaceInfo(
    val traffic: TrafficInfo?
)

data class TrafficInfo(
    val rx: Long,
    val tx: Long,
    val total: Long
)

// --- Task/Power Models ---
data class TaskResponse(
    val data: TaskData
)

data class TaskData(
    val task: TaskDetails
)

data class TaskDetails(
    val id: Int,
    val status: String
)

data class TaskListResponse(
    val data: List<ServerTask>,
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("last_page") val lastPage: Int
)

data class ServerTask(
    val action: String,
    val started: String,
    val updated: String,
    val finished: String,
    val completed: Boolean,
    val status: String,
    val success: Boolean
)

data class HistoryEvent(
    val timestamp: Long,
    val message: String,
    val type: HistoryEventType
)

enum class HistoryEventType {
    INFO, WARNING, SUCCESS, ERROR
}
