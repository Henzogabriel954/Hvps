package online.hcraft.hvps.model

import com.google.gson.annotations.SerializedName

data class AgentStatsResponse(
    val status: String,
    @SerializedName("uptime_seconds") val uptimeSeconds: Long,
    val cpu: AgentCpuStats,
    val memory: AgentMemoryStats,
    val disk: AgentDiskStats
)

data class AgentCpuStats(
    @SerializedName("usage_percent") val usagePercent: Float,
    val cores: Int
)

data class AgentMemoryStats(
    @SerializedName("total_bytes") val totalBytes: Long,
    @SerializedName("used_bytes") val usedBytes: Long,
    @SerializedName("free_bytes") val freeBytes: Long,
    @SerializedName("usage_percent") val usagePercent: Float
)

data class AgentDiskStats(
    val path: String,
    @SerializedName("total_bytes") val totalBytes: Long,
    @SerializedName("used_bytes") val usedBytes: Long,
    @SerializedName("usage_percent") val usagePercent: Float
)