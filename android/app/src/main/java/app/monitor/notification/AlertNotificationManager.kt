package app.monitor.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.monitor.MainActivity
import app.monitor.R
import app.monitor.core.model.AlertItem

object AlertNotificationManager {
    const val CHANNEL_CRITICAL = "monitor_alerts_critical"
    const val CHANNEL_WARNING = "monitor_alerts_warning"
    const val CHANNEL_INFO = "monitor_alerts_info"
    const val CHANNEL_COMPANION = "monitor_companion_status"

    private const val COMPANION_NOTIFICATION_ID = 948001

    const val ACTION_ACK_ALERT = "app.monitor.action.ACK_ALERT"
    const val ACTION_SNOOZE_ALERT = "app.monitor.action.SNOOZE_ALERT"
    const val EXTRA_ALERT_ID = "alert_id"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val critical = NotificationChannel(
            CHANNEL_CRITICAL,
            "Critical Watchdog Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical stalls, high battery drain, and emergency watchdogs"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }

        val warning = NotificationChannel(
            CHANNEL_WARNING,
            "Warning Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Agent stalls, low memory, and disconnect warnings"
        }

        val info = NotificationChannel(
            CHANNEL_INFO,
            "Informational Alerts",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Agent completions and power transitions"
        }

        val companion = NotificationChannel(
            CHANNEL_COMPANION,
            "Laptop Companion Status",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important Windows PC companion status and verification reminders"
        }

        manager.createNotificationChannels(listOf(critical, warning, info, companion))
    }

    fun showCompanionStatusNotification(context: Context, title: String, message: String) {
        val prefs = context.getSharedPreferences("app_monitor_settings_preferences", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pref_notifications", true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannels(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            COMPANION_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPANION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(COMPANION_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission was not granted.
        }
    }

    fun cancelCompanionNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(COMPANION_NOTIFICATION_ID)
        } catch (_: SecurityException) {}
    }

    fun showNotification(context: Context, alert: AlertItem) {
        val prefs = context.getSharedPreferences("app_monitor_settings_preferences", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pref_notifications", true)) return
        createChannels(context)

        val channelId = when (alert.severity.lowercase()) {
            "critical" -> CHANNEL_CRITICAL
            "warning" -> CHANNEL_WARNING
            else -> CHANNEL_INFO
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            alert.alertId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ackIntent = PendingIntent.getBroadcast(
            context,
            alert.alertId.hashCode(),
            Intent(context, AlertActionReceiver::class.java).apply {
                action = ACTION_ACK_ALERT
                putExtra(EXTRA_ALERT_ID, alert.alertId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            alert.alertId.hashCode() + 1,
            Intent(context, AlertActionReceiver::class.java).apply {
                action = ACTION_SNOOZE_ALERT
                putExtra(EXTRA_ALERT_ID, alert.alertId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(
                if (alert.severity.equals("critical", true)) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .addAction(0, "Acknowledge", ackIntent)
            .addAction(0, "Snooze 1h", snoozeIntent)

        try {
            NotificationManagerCompat.from(context).notify(alert.alertId.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Notification permission might not be granted yet
        }
    }

    fun cancelNotification(context: Context, alertId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(alertId.hashCode())
        } catch (_: SecurityException) {}
    }
}
