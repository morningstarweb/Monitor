package app.monitor.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.monitor.MonitorApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getStringExtra(AlertNotificationManager.EXTRA_ALERT_ID) ?: return
        val app = context.applicationContext as? MonitorApp ?: return
        val apiClient = app.apiClient

        // Immediately dismiss the notification
        AlertNotificationManager.cancelNotification(context, alertId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    AlertNotificationManager.ACTION_ACK_ALERT -> {
                        apiClient.ackAlert(alertId)
                    }
                    AlertNotificationManager.ACTION_SNOOZE_ALERT -> {
                        apiClient.snoozeAlert(alertId, 3600)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
