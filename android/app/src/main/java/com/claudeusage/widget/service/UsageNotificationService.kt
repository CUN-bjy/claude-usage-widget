package com.claudeusage.widget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.R
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.repository.UsageRepository
import kotlinx.coroutines.*

class UsageNotificationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = UsageRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Loading usage data..."))
        startPolling()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claude Usage",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current Claude usage status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                updateNotification()
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private suspend fun updateNotification() {
        val credentialManager = CredentialManager(applicationContext)
        val credentials = credentialManager.getCredentials() ?: return

        val result = repository.fetchUsageData(credentials)
        result.onSuccess { data ->
            val notification = buildUsageNotification(data)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildUsageNotification(data: UsageData): Notification {
        val fiveHour = data.fiveHour
        val sevenDay = data.sevenDay

        val lines = mutableListOf<String>()

        if (fiveHour != null) {
            val remaining = fiveHour.remainingDuration
            val timeStr = if (remaining != null && remaining.seconds > 0) {
                val h = remaining.seconds / 3600
                val m = (remaining.seconds % 3600) / 60
                if (h > 0) "${h}h ${m}m left" else "${m}m left"
            } else ""
            lines.add("5h: ${String.format("%.1f", fiveHour.utilization)}% $timeStr")
        }

        if (sevenDay != null) {
            val remaining = sevenDay.remainingDuration
            val timeStr = if (remaining != null && remaining.seconds > 0) {
                val d = remaining.seconds / 86400
                val h = (remaining.seconds % 86400) / 3600
                if (d > 0) "${d}d ${h}h left" else "${h}h left"
            } else ""
            lines.add("7d: ${String.format("%.1f", sevenDay.utilization)}% $timeStr")
        }

        val title = lines.joinToString("  |  ").ifEmpty { "No usage data" }

        // Use 5h utilization for progress bar in notification
        val progress = fiveHour?.utilization?.toInt()?.coerceIn(0, 100) ?: 0

        return buildNotification(title, progress)
    }

    private fun buildNotification(text: String, progress: Int = -1): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "claude_usage_channel"
        const val NOTIFICATION_ID = 1001
        const val UPDATE_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes

        fun start(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            context.stopService(intent)
        }
    }
}
