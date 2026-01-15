package online.hcraft.hvps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import online.hcraft.hvps.R
import online.hcraft.hvps.model.HistoryEvent
import online.hcraft.hvps.model.HistoryEventType
import online.hcraft.hvps.network.RetrofitClient
import online.hcraft.hvps.utils.SettingsManager
import online.hcraft.hvps.utils.TokenManager

class MonitoringService : Service() {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var settingsManager: SettingsManager
    
    // To prevent spamming, store last notification time per server
    private val lastAlertTime = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_NOTIFICATION_ID, createForegroundNotification())
        
        startMonitoring()
        
        return START_STICKY
    }

    private fun startMonitoring() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val monitoredIds = settingsManager.getMonitoredServerIds()
                if (monitoredIds.isEmpty()) {
                    stopSelf()
                    return@launch
                }

                if (TokenManager.getToken().isNullOrBlank()) {
                    stopSelf()
                    return@launch
                }

                for (id in monitoredIds) {
                    try {
                        val response = RetrofitClient.api.getServerDetails(id, state = true)
                        val server = response.data
                        
                        // 1. Check CPU Threshold
                        if (settingsManager.getNotificationEnabled(id)) {
                            val threshold = settingsManager.getCpuThreshold(id)
                            val cpuStr = server.state?.cpu?.replace("%", "")?.trim()
                            val cpuVal = cpuStr?.toFloatOrNull()
                            
                            if (cpuVal != null && cpuVal >= threshold) {
                                sendAlert(id, server.name, "High CPU: ${server.state.cpu}", "Current usage is ${server.state.cpu}", true)
                            }
                        }

                        // 2. Check Status Change (Online/Offline)
                        val currentStatus = server.state?.status ?: "unknown"
                        val lastStatus = settingsManager.getLastStatus(id)
                        
                        if (lastStatus != null && lastStatus != currentStatus) {
                            // Status changed
                            if (currentStatus == "offline" && settingsManager.getNotifyOffline(id)) {
                                sendAlert(id, server.name, "Server Offline", "${server.name} has gone offline.", false, HistoryEventType.ERROR)
                            } else if (currentStatus == "running" && settingsManager.getNotifyOnline(id)) {
                                sendAlert(id, server.name, "Server Online", "${server.name} is now online.", false, HistoryEventType.SUCCESS)
                            } else {
                                // Just log the change to history if notification is disabled? 
                                // Or maybe user only wants history if notification is enabled?
                                // Let's log significant state changes regardless for history if they are "monitored"
                                val type = if(currentStatus == "running") HistoryEventType.SUCCESS else if(currentStatus == "offline") HistoryEventType.ERROR else HistoryEventType.INFO
                                settingsManager.addHistoryEvent(id, HistoryEvent(System.currentTimeMillis(), "Status changed to $currentStatus", type))
                            }
                        }
                        
                        settingsManager.setLastStatus(id, currentStatus)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Check every 60 seconds to save battery/bandwidth
                delay(60000) 
            }
        }
    }
    
    private fun sendAlert(
        serverId: String, 
        serverName: String, 
        title: String, 
        message: String, 
        throttle: Boolean,
        eventType: HistoryEventType = HistoryEventType.WARNING
    ) {
        val now = System.currentTimeMillis()
        val lastTime = lastAlertTime[serverId] ?: 0L
        
        // If throttling is requested, limit to once every 10 mins
        if (!throttle || now - lastTime > 10 * 60 * 1000) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Use a unique ID based on server + title hash to stack or separate
            notificationManager.notify((serverId + title).hashCode(), notification)
            
            if (throttle) {
                lastAlertTime[serverId] = now
            }
            
            // Log to History
            settingsManager.addHistoryEvent(serverId, HistoryEvent(now, message, eventType))
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle("Hvps Monitoring")
            .setContentText("Monitoring your servers in the background...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Channel for the persistent service notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(serviceChannel)

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "High Usage Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "hvps_monitoring_service"
        const val CHANNEL_ALERTS_ID = "hvps_alerts"
        const val SERVICE_NOTIFICATION_ID = 1001
    }
}