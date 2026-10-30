package com.example.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class StreamingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var memoryUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_STREAM -> {
                handleEndStreamAction()
                return START_NOT_STICKY
            }
            ACTION_STOP_SERVICE -> {
                stopUpdatingAndForeground()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startStreamingForeground()
                return START_STICKY
            }
        }
    }

    private fun startStreamingForeground() {
        ensureNotificationChannel()
        val initialMemMb = computeTotalMemoryMb(this)
        val notification = buildRunningNotification(initialMemMb)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // If POST_NOTIFICATIONS is denied or background restriction occurs, keep service alive
        }

        if (memoryUpdateJob?.isActive != true) {
            memoryUpdateJob = serviceScope.launch {
                while (isActive) {
                    delay(UPDATE_INTERVAL_MS)
                    val memMb = computeTotalMemoryMb(this@StreamingForegroundService)
                    val updatedNotification = buildRunningNotification(memMb)
                    try {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIFICATION_ID, updatedNotification)
                    } catch (_: SecurityException) {
                        // POST_NOTIFICATIONS denied on Android 13+; service continues running without crashing
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun handleEndStreamAction() {
        stopUpdatingAndForeground()
        // Trigger registered shutdown callback
        onEndStreamRequested?.invoke()
        stopSelf()
        // Force kill the process to release all memory and resources
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun stopUpdatingAndForeground() {
        memoryUpdateJob?.cancel()
        memoryUpdateJob = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "xCloud Pad Running Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent status notification while streaming on xCloud Pad"
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildRunningNotification(memoryMb: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val endIntent = Intent(this, StreamingForegroundService::class.java).apply {
            action = ACTION_END_STREAM
        }
        val endPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_END,
            endIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // Fallback or custom icon
            .setContentTitle("xCloud Pad Active")
            .setContentText("Memory Usage: $memoryMb MB")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(0xFF000000.toInt()) // Pure Black
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Open Pad",
                openPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Process",
                endPendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("xCloud Pad System Monitor")
                .bigText("The stream engine is active. Current process memory footprint: $memoryMb MB. Tap 'End' to kill the application process completely."))
            .build()
    }

    override fun onDestroy() {
        stopUpdatingAndForeground()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "xcloud_pad_running_channel"
        const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 3_000L
        private const val REQUEST_CODE_OPEN = 201
        private const val REQUEST_CODE_END = 202

        const val ACTION_START_SERVICE = "com.example.service.action.START_STREAM_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.service.action.STOP_STREAM_SERVICE"
        const val ACTION_END_STREAM = "com.example.service.action.END_STREAM"

        @Volatile
        var onEndStreamRequested: (() -> Unit)? = null

        fun start(context: Context) {
            val intent = Intent(context, StreamingForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamingForegroundService::class.java)
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }

        /**
         * Computes total PSS memory in MB for the app's own process plus any separate
         * WebView renderer processes belonging to this app/package.
         */
        fun computeTotalMemoryMb(context: Context): Int {
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return 1
                val myPid = Process.myPid()
                val myUid = Process.myUid()
                val pkg = context.packageName

                val pids = linkedSetOf(myPid)
                val runningProcesses = am.runningAppProcesses.orEmpty()
                for (proc in runningProcesses) {
                    val isAppOrWebViewProcess = proc.uid == myUid ||
                        proc.processName.startsWith(pkg) ||
                        proc.processName.contains("webview", ignoreCase = true) ||
                        proc.processName.contains("sandboxed_process", ignoreCase = true)
                    if (isAppOrWebViewProcess && proc.pid > 0) {
                        pids.add(proc.pid)
                    }
                }

                val memInfos = am.getProcessMemoryInfo(pids.toIntArray())
                val totalPssKb = memInfos.sumOf { it?.totalPss ?: 0 }
                (totalPssKb / 1024.0).roundToInt().coerceAtLeast(1)
            } catch (_: Exception) {
                1
            }
        }
    }
}
