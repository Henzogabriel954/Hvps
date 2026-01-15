package online.hcraft.hvps.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import online.hcraft.hvps.model.HistoryEvent

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vps_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getNotificationEnabled(serverId: String): Boolean {
        return prefs.getBoolean("notify_$serverId", false)
    }

    fun setNotificationEnabled(serverId: String, enabled: Boolean) {
        prefs.edit().putBoolean("notify_$serverId", enabled).apply()
        updateMonitoredList(serverId)
    }

    fun getNotifyOffline(serverId: String): Boolean {
        return prefs.getBoolean("notify_offline_$serverId", false)
    }

    fun setNotifyOffline(serverId: String, enabled: Boolean) {
        prefs.edit().putBoolean("notify_offline_$serverId", enabled).apply()
        updateMonitoredList(serverId)
    }

    fun getNotifyOnline(serverId: String): Boolean {
        return prefs.getBoolean("notify_online_$serverId", false)
    }

    fun setNotifyOnline(serverId: String, enabled: Boolean) {
        prefs.edit().putBoolean("notify_online_$serverId", enabled).apply()
        updateMonitoredList(serverId)
    }

    private fun updateMonitoredList(serverId: String) {
        val anyEnabled = getNotificationEnabled(serverId) || getNotifyOffline(serverId) || getNotifyOnline(serverId)
        val currentSet = getMonitoredServerIds().toMutableSet()
        if (anyEnabled) {
            currentSet.add(serverId)
        } else {
            // Check if ANY other setting is enabled for this server, if not remove
            if (!getNotificationEnabled(serverId) && !getNotifyOffline(serverId) && !getNotifyOnline(serverId)) {
                currentSet.remove(serverId)
            }
        }
        prefs.edit().putStringSet("monitored_servers", currentSet).apply()
    }

    fun getMonitoredServerIds(): Set<String> {
        return prefs.getStringSet("monitored_servers", emptySet()) ?: emptySet()
    }

    fun getCpuThreshold(serverId: String): Int {
        return prefs.getInt("cpu_threshold_$serverId", 85) // Default 85%
    }

    fun setCpuThreshold(serverId: String, threshold: Int) {
        prefs.edit().putInt("cpu_threshold_$serverId", threshold).apply()
    }

    // --- History & State Tracking ---

    fun getLastStatus(serverId: String): String? {
        return prefs.getString("last_status_$serverId", null)
    }

    fun setLastStatus(serverId: String, status: String) {
        prefs.edit().putString("last_status_$serverId", status).apply()
    }

    fun addHistoryEvent(serverId: String, event: HistoryEvent) {
        val currentList = getHistoryEvents(serverId).toMutableList()
        currentList.add(0, event) // Add to top
        
        // Retention Policy: Max 50 items AND Max 30 days old
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        val filteredList = currentList
            .filter { it.timestamp > thirtyDaysAgo }
            .take(50)
            
        val json = gson.toJson(filteredList)
        prefs.edit().putString("history_$serverId", json).apply()
    }

    fun getHistoryEvents(serverId: String): List<HistoryEvent> {
        val json = prefs.getString("history_$serverId", null) ?: return emptyList()
        val type = object : TypeToken<List<HistoryEvent>>() {}.type
        return try {
            val list: List<HistoryEvent> = gson.fromJson(json, type)
            // Filter on read as well to ensure UI doesn't show stale data if no new events occurred
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            list.filter { it.timestamp > thirtyDaysAgo }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory(serverId: String) {
        prefs.edit().remove("history_$serverId").apply()
    }
}