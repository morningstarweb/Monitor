package app.monitor.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.monitor.MainActivity
import app.monitor.R
import app.monitor.core.model.AlertItem

enum class MonitorNotifTone {
    LIVE,
    VIOLET,
    WARNING,
    CRITICAL
}

data class MonitorNotifSpec(
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val leftChipLabel: String,
    val leftChipValue: String,
    val rightChipLabel: String,
    val rightChipValue: String,
    val tone: MonitorNotifTone,
    val isAlertIcon: Boolean = false
)

object AlertNotificationManager {
    const val CHANNEL_CRITICAL = "monitor_alerts_critical"
    const val CHANNEL_WARNING = "monitor_alerts_warning"
    const val CHANNEL_INFO = "monitor_alerts_info"
    const val CHANNEL_COMPANION = "monitor_companion_status"

    private const val COMPANION_NOTIFICATION_ID = 948001

    const val ACTION_ACK_ALERT = "app.monitor.action.ACK_ALERT"
    const val ACTION_SNOOZE_ALERT = "app.monitor.action.SNOOZE_ALERT"
    const val EXTRA_ALERT_ID = "alert_id"

    private data class ToneStyle(
        @DrawableRes val badgeBgRes: Int,
        @DrawableRes val pillBgRes: Int,
        @ColorInt val accentColor: Int
    )

    private fun resolveToneStyle(context: Context, tone: MonitorNotifTone): ToneStyle =
        when (tone) {
            MonitorNotifTone.LIVE -> ToneStyle(
                badgeBgRes = R.drawable.bg_notif_badge_live,
                pillBgRes = R.drawable.bg_notif_pill_live,
                accentColor = ContextCompat.getColor(context, R.color.notif_brand_emerald)
            )
            MonitorNotifTone.VIOLET -> ToneStyle(
                badgeBgRes = R.drawable.bg_notif_badge_violet,
                pillBgRes = R.drawable.bg_notif_pill_violet,
                accentColor = ContextCompat.getColor(context, R.color.notif_brand_violet)
            )
            MonitorNotifTone.WARNING -> ToneStyle(
                badgeBgRes = R.drawable.bg_notif_badge_warn,
                pillBgRes = R.drawable.bg_notif_pill_warn,
                accentColor = ContextCompat.getColor(context, R.color.notif_brand_amber)
            )
            MonitorNotifTone.CRITICAL -> ToneStyle(
                badgeBgRes = R.drawable.bg_notif_badge_crit,
                pillBgRes = R.drawable.bg_notif_pill_crit,
                accentColor = ContextCompat.getColor(context, R.color.notif_brand_crimson)
            )
        }

    fun applyMonitorStyle(
        context: Context,
        builder: NotificationCompat.Builder,
        spec: MonitorNotifSpec
    ): NotificationCompat.Builder {
        val style = resolveToneStyle(context, spec.tone)
        val iconRes = if (spec.isAlertIcon) R.drawable.ic_notif_shield_alert else R.drawable.ic_notif_pc

        val collapsed = RemoteViews(context.packageName, R.layout.monitor_notif_collapsed).apply {
            setInt(R.id.notif_badge_container, "setBackgroundResource", style.badgeBgRes)
            setImageViewResource(R.id.notif_badge_icon, iconRes)
            setTextViewText(R.id.notif_title, spec.title)
            setTextViewText(R.id.notif_subtitle, spec.subtitle)
            setTextViewText(R.id.notif_pill, spec.badgeText)
            setInt(R.id.notif_pill, "setBackgroundResource", style.pillBgRes)
            setTextColor(R.id.notif_pill, style.accentColor)
        }

        val expanded = RemoteViews(context.packageName, R.layout.monitor_notif_expanded).apply {
            setInt(R.id.notif_badge_container, "setBackgroundResource", style.badgeBgRes)
            setImageViewResource(R.id.notif_badge_icon, iconRes)
            setTextViewText(R.id.notif_title, spec.title)
            setTextViewText(R.id.notif_subtitle, spec.subtitle)
            setTextViewText(R.id.notif_pill, spec.badgeText)
            setInt(R.id.notif_pill, "setBackgroundResource", style.pillBgRes)
            setTextColor(R.id.notif_pill, style.accentColor)
            setTextViewText(R.id.notif_chip_left_label, spec.leftChipLabel)
            setTextViewText(R.id.notif_chip_left_value, spec.leftChipValue)
            setTextViewText(R.id.notif_chip_right_label, spec.rightChipLabel)
            setTextViewText(R.id.notif_chip_right_value, spec.rightChipValue)
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setColor(style.accentColor)
            .setContentTitle(spec.title)
            .setContentText(spec.subtitle)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
    }

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

    fun showCompanionStatusNotification(
        context: Context,
        title: String,
        message: String,
        badgeText: String = "LIVE",
        leftChipLabel: String = "TRANSPORT",
        leftChipValue: String = "Encrypted Link",
        rightChipLabel: String = "SECURITY",
        rightChipValue: String = "Noise E2EE",
        tone: MonitorNotifTone = MonitorNotifTone.LIVE
    ) {
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

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPANION)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        applyMonitorStyle(
            context = context,
            builder = builder,
            spec = MonitorNotifSpec(
                title = title,
                subtitle = message,
                badgeText = badgeText,
                leftChipLabel = leftChipLabel,
                leftChipValue = leftChipValue,
                rightChipLabel = rightChipLabel,
                rightChipValue = rightChipValue,
                tone = tone
            )
        )

        try {
            NotificationManagerCompat.from(context).notify(COMPANION_NOTIFICATION_ID, builder.build())
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

        val (tone, badge) = when (alert.severity.lowercase()) {
            "critical" -> MonitorNotifTone.CRITICAL to "CRITICAL"
            "warning" -> MonitorNotifTone.WARNING to "WARNING"
            else -> MonitorNotifTone.VIOLET to "NOTICE"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(
                if (alert.severity.equals("critical", true)) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .addAction(0, "Acknowledge", ackIntent)
            .addAction(0, "Snooze 1h", snoozeIntent)

        applyMonitorStyle(
            context = context,
            builder = builder,
            spec = MonitorNotifSpec(
                title = alert.title,
                subtitle = alert.body,
                badgeText = badge,
                leftChipLabel = "WATCHDOG",
                leftChipValue = alert.ruleKind.replace('_', ' ').replaceFirstChar { it.uppercase() },
                rightChipLabel = "SEVERITY",
                rightChipValue = alert.severity.replaceFirstChar { it.uppercase() },
                tone = tone,
                isAlertIcon = true
            )
        )

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
