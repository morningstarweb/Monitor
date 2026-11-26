package app.monitor

import android.app.Application
import app.monitor.core.network.MonitorApiClient
import app.monitor.core.network.TokenStorage
import app.monitor.notification.AndroidAlertNotifier
import kotlinx.coroutines.flow.MutableSharedFlow

class MonitorApp : Application() {
    val tokenStorage by lazy { TokenStorage(this) }
    val apiClient by lazy { MonitorApiClient(tokenStorage) }
    val alertNotifier by lazy { AndroidAlertNotifier(this) }

    /** One-shot signal emitted by the notification Disconnect action. */
    val disconnectRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
