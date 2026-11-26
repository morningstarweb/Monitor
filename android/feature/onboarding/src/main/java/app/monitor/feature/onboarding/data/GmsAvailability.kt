package app.monitor.feature.onboarding.data

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Single gate for optional Google Play services features (Google sign-in and
 * ML Kit QR scanning). Devices without GMS keep the manual 6-digit pairing path.
 */
object GmsAvailability {

    data class CheckResult(val available: Boolean, val code: Int, val message: String?)

    private const val UNSUPPORTED_MESSAGE =
        "Google Play services is unavailable on this device. Google sign-in and QR " +
            "scanning are disabled. Use manual pairing with the 6-digit code shown " +
            "on your desktop instead."

    fun check(context: Context): CheckResult {
        val availability = runCatching { GoogleApiAvailability.getInstance() }.getOrNull()
            ?: return CheckResult(false, ConnectionResult.SERVICE_MISSING, UNSUPPORTED_MESSAGE)

        // -1 is our local "unclassified" sentinel; avoids depending on a GMS
        // constant that may vary across play-services-base versions.
        val code = runCatching {
            availability.isGooglePlayServicesAvailable(context.applicationContext)
        }.getOrDefault(-1)

        if (code == ConnectionResult.SUCCESS) return CheckResult(true, code, null)

        val message = when (code) {
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ->
                "Google Play services needs an update. Google sign-in and QR scanning are " +
                    "disabled until it is updated. Use manual pairing with the 6-digit code " +
                    "shown on your desktop instead."

            else -> UNSUPPORTED_MESSAGE
        }
        return CheckResult(false, code, message)
    }
}
