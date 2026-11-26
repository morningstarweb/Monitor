package app.monitor.feature.dashboard.settings

import android.app.Activity
import android.content.Context
import app.monitor.core.common.BiometricAuthManager

internal fun requireBiometricConfirmation(
    context: Context,
    requiresAuth: Boolean,
    title: String,
    subtitle: String,
    onConfirmed: () -> Unit
) {
    if (!requiresAuth) {
        onConfirmed()
        return
    }
    val activity = context as? Activity ?: return
    BiometricAuthManager.authenticate(
        activity = activity,
        title = title,
        subtitle = subtitle,
        onSuccess = onConfirmed,
        onError = {}
    )
}
