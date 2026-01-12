package online.hcraft.hvps.model

import com.google.gson.annotations.SerializedName

// --- Server List Models ---
data class ServerListResponse(
    val data: List<VpsServer>
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
    val primary: InterfaceInfo
)

data class InterfaceInfo(
    val ipv4: List<IpAddress>?
)

data class IpAddress(
    val address: String
)

data class ServerState(
    val status: String,
    val running: Boolean,
    val cpu: String?,    // CPU Usage (e.g., "0.3 %")
    val memory: String?, // RAM Usage (if available)
    val disk: String?    // Disk Usage (if available)
)

data class ServerDetailResponse(
    val data: VpsServer
)

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
