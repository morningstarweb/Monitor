package app.monitor.core.common

import android.app.Activity

/**
 * Enforces biometric / device-credential verification on high-impact or
 * destructive operations when App Lock and Destructive Action Gating are
 * enabled (`AUD-026`). Fail-closed whenever no active [Activity] is available.
 */
object DestructiveActionGate {
    fun requiresAuthentication(
        appLockEnabled: Boolean,
        gateDestructiveOps: Boolean
    ): Boolean = appLockEnabled && gateDestructiveOps

    fun runGuarded(
        activity: Activity?,
        appLockEnabled: Boolean,
        gateDestructiveOps: Boolean,
        title: String,
        subtitle: String,
        authenticate: (
            activity: Activity,
            title: String,
            subtitle: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
            onUnavailable: () -> Unit
        ) -> Unit = { act, t, sub, ok, err, unavail ->
            BiometricAuthManager.authenticate(
                activity = act,
                title = t,
                subtitle = sub,
                onSuccess = ok,
                onError = err,
                onUnavailable = unavail
            )
        },
        onDenied: (String) -> Unit = {},
        onAuthorized: () -> Unit
    ) {
        if (!requiresAuthentication(appLockEnabled, gateDestructiveOps)) {
            onAuthorized()
            return
        }
        val currentActivity = activity ?: run {
            onDenied("Biometric confirmation requires an active screen")
            return
        }
        authenticate(
            currentActivity,
            title,
            subtitle,
            onAuthorized,
            onDenied,
            { onDenied("Device biometric or credential authentication is unavailable") }
        )
    }
}
