package app.monitor.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.monitor.core.common.AlertNotifier
import app.monitor.core.model.AlertItem

/**
 * Android-side implementation of [AlertNotifier].
 *
 * Called from the dashboard's ServerEvent.AlertTriggered branch. Payloads are
 * deliberately generic: only an opaque alert id and a coarse severity cross the
 * module boundary. No repository name, file path, terminal text or raw alert
 * message is ever placed in the system notification shade.
 *
 * POST_NOTIFICATIONS is checked before posting and the underlying manager also
 * catches SecurityException, so a missing/revoked permission degrades silently
 * rather than crashing the stream collector.
 */
class AndroidAlertNotifier(context: Context) : AlertNotifier {

    private val appContext: Context = context.applicationContext

    override fun postAlert(alertId: String, severity: String) {
        if (!canPostNotifications()) return

        val normalized = severity.lowercase()
        val title = when (normalized) {
            "critical" -> "Critical monitor alert"
            "warning" -> "Monitor warning"
            else -> "Monitor alert"
        }
        val body = "A $normalized watchdog alert was triggered on your desktop."

        // AlertNotificationManager owns channels, PendingIntents and the final
        // SecurityException guard.
        AlertNotificationManager.showNotification(
            appContext,
            AlertItem(
                alertId = alertId,
                ruleKind = "watchdog",
                severity = normalized,
                title = title,
                body = body,
                dedupKey = alertId,
                createdAtUtcMs = System.currentTimeMillis()
            )
        )
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
}
