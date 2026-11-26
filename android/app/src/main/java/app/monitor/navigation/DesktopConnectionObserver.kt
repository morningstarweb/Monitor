package app.monitor.navigation

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import app.monitor.feature.dashboard.DashboardViewModel

@Composable
fun ObserveDesktopConnection(viewModel: DashboardViewModel) {
    val context = LocalContext.current.applicationContext
    LifecycleStartEffect(viewModel, context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        var lastNetwork: Network? = connectivity?.activeNetwork
        var lastTransports: Int = connectivity?.getNetworkCapabilities(lastNetwork)?.transportMask() ?: 0
        var lastValidated: Boolean = connectivity?.getNetworkCapabilities(lastNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        var observing = true
        val handoverRunnable = Runnable {
            if (!observing) return@Runnable
            val current = connectivity?.activeNetwork
            val caps = connectivity?.getNetworkCapabilities(current)
            val mask = caps?.transportMask() ?: 0
            val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            if (current != lastNetwork || mask != lastTransports || (!lastValidated && isValidated)) {
                lastNetwork = current
                lastTransports = mask
                lastValidated = isValidated
                viewModel.reconnect()
            }
        }

        fun scheduleHandover() {
            mainHandler.removeCallbacks(handoverRunnable)
            mainHandler.postDelayed(handoverRunnable, 120L)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post {
                    if (observing) scheduleHandover()
                }
            }

            override fun onLost(network: Network) {
                mainHandler.post {
                    if (observing && (network == lastNetwork || connectivity?.activeNetwork == null)) {
                        lastValidated = false
                        scheduleHandover()
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                mainHandler.post {
                    val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val mask = networkCapabilities.transportMask()
                    if (observing && (network != lastNetwork || mask != lastTransports || (!lastValidated && isValidated))) {
                        scheduleHandover()
                    }
                }
            }
        }
        viewModel.start()
        connectivity?.registerDefaultNetworkCallback(callback)
        onStopOrDispose {
            observing = false
            connectivity?.unregisterNetworkCallback(callback)
            mainHandler.removeCallbacksAndMessages(null)
            viewModel.stop()
        }
    }
}

private fun NetworkCapabilities.transportMask(): Int {
    var mask = 0
    if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) mask = mask or 1
    if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) mask = mask or 2
    if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) mask = mask or 4
    if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) mask = mask or 8
    return mask
}
