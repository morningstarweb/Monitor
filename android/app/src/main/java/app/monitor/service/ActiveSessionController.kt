package app.monitor.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Small helper used by MainActivity to keep the service in sync with session state. */
object ActiveSessionController {

    fun start(context: Context, activeSessionCount: Int) {
        if (activeSessionCount <= 0) {
            stop(context)
            return
        }
        dispatch(context, ActiveSessionService.ACTION_START, activeSessionCount)
    }

    fun update(context: Context, activeSessionCount: Int) {
        if (activeSessionCount <= 0) {
            stop(context)
            return
        }
        dispatch(context, ActiveSessionService.ACTION_UPDATE, activeSessionCount)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        runCatching { appContext.stopService(Intent(appContext, ActiveSessionService::class.java)) }
        runCatching { NotificationManagerCompat.from(appContext).cancel(ActiveSessionService.NOTIFICATION_ID) }
    }

    private fun dispatch(context: Context, action: String, activeSessionCount: Int) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, ActiveSessionService::class.java).apply {
            this.action = action
            putExtra(ActiveSessionService.EXTRA_ACTIVE_SESSION_COUNT, activeSessionCount)
        }
        // May throw ForegroundServiceStartNotAllowedException if called outside
        // a foreground/allowed window; the caller is a UI LaunchedEffect.
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
    }
}
