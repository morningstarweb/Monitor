package app.monitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.monitor.MonitorApp

/** Handles the Disconnect action on the ongoing session-monitor notification. */
class SessionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISCONNECT) return
        val app = context.applicationContext as? MonitorApp ?: return

        // Turn the opt-in off first so the UI LaunchedEffect cannot immediately
        // restart the service after this broadcast.
        SessionMonitorPrefs.setEnabled(context, false)
        ActiveSessionController.stop(context)
        runCatching {
            NotificationManagerCompat.from(context.applicationContext)
                .cancel(ActiveSessionService.NOTIFICATION_ID)
        }
        // MainActivity collects this and performs the actual disconnect/unpair.
        app.disconnectRequests.tryEmit(Unit)
    }

    companion object {
        const val ACTION_DISCONNECT = "app.monitor.action.SESSION_DISCONNECT"
    }
}
