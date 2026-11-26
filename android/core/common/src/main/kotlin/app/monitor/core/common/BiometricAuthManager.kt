package app.monitor.core.common

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import java.util.concurrent.Executor

/**
 * Hardware-backed biometric & system credential gating (Fingerprint, Face, PIN/Pattern).
 * Works across all Android 11+ (API 30+) devices with biometric + device credential fallback.
 */
object BiometricAuthManager {

    val AUTHENTICATORS: Int = Authenticators.BIOMETRIC_STRONG or
            Authenticators.BIOMETRIC_WEAK or
            Authenticators.DEVICE_CREDENTIAL

    fun isBiometricAvailable(context: Context): Boolean {
        val pm = context.packageManager
        val hasSensor = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
                pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
                pm.hasSystemFeature("android.hardware.biometrics.face")
        val bm = context.getSystemService(BiometricManager::class.java)
        val canAuth = bm?.canAuthenticate(AUTHENTICATORS)
        return hasSensor || canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Requests trusted authentication.
     *
     * Security contract (AUD-02): [onSuccess] is invoked ONLY after the Android
     * system reports a successful authentication result. Biometric/credential
     * unavailability is reported through [onUnavailable] and must never be
     * treated as authentication success by callers that gate protected content.
     */
    fun authenticate(
        activity: Activity,
        title: String = "Unlock Monitor",
        subtitle: String = "Use Face, Fingerprint, or PIN to continue",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onUnavailable: () -> Unit = {}
    ) {
        val bm = activity.getSystemService(BiometricManager::class.java)
        val canAuth = bm?.canAuthenticate(AUTHENTICATORS)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // No trusted authenticator is available. This is NOT a successful
            // authentication and must leave any protected state locked.
            onUnavailable()
            return
        }

        try {
            val cancellationSignal = CancellationSignal()
            val executor: Executor = activity.mainExecutor

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    activity.runOnUiThread { onSuccess() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    activity.runOnUiThread {
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        ) {
                            onError("Authentication canceled")
                        } else {
                            onError(errString?.toString() ?: "Authentication error")
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    activity.runOnUiThread {
                        onError("Authentication not recognized. Try again.")
                    }
                }
            }

            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()

            prompt.authenticate(cancellationSignal, executor, callback)
        } catch (e: Exception) {
            onError(e.message ?: "Authentication failed")
        }
    }
}
