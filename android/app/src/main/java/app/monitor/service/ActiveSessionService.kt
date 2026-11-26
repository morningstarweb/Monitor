package app.monitor.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.monitor.MainActivity
import app.monitor.R

/**
 * User-initiated foreground service that runs ONLY while at least one desktop
 * agent session is active (Active/Stalled). It keeps a visible, dismissible
 * connection monitor alive and exposes a Disconnect action.
 *
 * Lifecycle contract:
 *  - started with [ACTION_START] and an active-session count > 0;
 *  - [ACTION_UPDATE] with count == 0 stops the service (idle / all sessions ended);
 *  - the notification's Disconnect action broadcasts to SessionActionReceiver,
 *    which also stops the service;
 *  - [onStartCommand] returns START_NOT_STICKY: the OS must NOT resurrect a
 *    session monitor after process death. The user can re-enable it from the UI.
 *
 * Android 14+ (API 34) requires a declared and granted
 * FOREGROUND_SERVICE_CONNECTED_DEVICE permission before using the
 * `connectedDevice` foreground service type; we check it and fail closed.
 * No battery-optimization exemption is requested.
 *
 * WHY NO PERMANENT WAKE LOCK: holding a PARTIAL_WAKE_LOCK for the lifetime of a
 * session would keep the CPU on indefinitely and drain the battery even while the
 * device is idle in a pocket. The WebSocket transport already uses server pings
 * plus OS network callbacks (see ObserveDesktopConnection) to re-establish the
 * stream after suspend/resume. A foreground service gives the user visibility and
 * an immediate Disconnect control while letting the OS suspend the device normally.
 */
class ActiveSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE -> {
                val count = intent.getIntExtra(EXTRA_ACTIVE_SESSION_COUNT, 0)
                if (count <= 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                updateNotification(count)
            }

            else -> {
                val count = intent?.getIntExtra(EXTRA_ACTIVE_SESSION_COUNT, 0) ?: 0
                if (count <= 0 || !connectedDeviceTypePermitted()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    startForegroundCompat(count)
                } catch (_: Exception) {
                    // e.g. ForegroundServiceStartNotAllowedException or a missing
                    // notification permission: never leave an orphaned service.
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun connectedDeviceTypePermitted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundCompat(activeCount: Int) {
        val notification = buildNotification(activeCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(activeCount: Int) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(activeCount))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted: the foreground service still runs.
        }
    }

    private fun buildNotification(activeCount: Int): android.app.Notification {
        val disconnectIntent = Intent(this, SessionActionReceiver::class.java).apply {
            action = SessionActionReceiver.ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Session monitor active")
            .setContentText("$activeCount active desktop session(s) · tap to open Monitor")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Disconnect", disconnectPendingIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active session monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Visible control for an active desktop session monitor"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "app.monitor.action.SESSION_MONITOR_START"
        const val ACTION_UPDATE = "app.monitor.action.SESSION_MONITOR_UPDATE"
        const val ACTION_STOP = "app.monitor.action.SESSION_MONITOR_STOP"
        const val EXTRA_ACTIVE_SESSION_COUNT = "active_session_count"

        internal const val CHANNEL_ID = "monitor_active_session"
        internal const val NOTIFICATION_ID = 4101
    }
}
