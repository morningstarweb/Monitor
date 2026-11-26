package app.monitor.security

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.monitor.core.common.AppLockAuthPolicy
import app.monitor.core.common.AppLockAuthResult
import app.monitor.core.common.AppLockLifecyclePolicy
import app.monitor.core.common.BiometricAuthManager
import app.monitor.core.network.TokenStorage
import app.monitor.feature.dashboard.settings.ui.components.AppLockOverlay

/**
 * Enforces the app lock around protected content.
 *
 * AUD-02: the lock is released only when [AppLockAuthPolicy] authorizes the
 * result of a trusted Android authentication. Unavailable biometrics,
 * cancellation, failure and async state changes can never unlock content.
 */
@Composable
fun AppLockGate(
    activity: Activity,
    tokenStorage: TokenStorage,
    content: @Composable () -> Unit
) {
    // AUD-02 / NEW-09: authentication state is process-scoped and fail-closed.
    // It is deliberately NOT rememberSaveable: an unlocked state must never be
    // restored from a saved-instance bundle after process death. Every fresh
    // composition starts locked when the app lock is enabled.
    var isAppLocked by remember {
        mutableStateOf(
            AppLockLifecyclePolicy.lockedAfterProcessDeath(
                appLockEnabled = tokenStorage.isAppLockEnabled(),
                restoredUnlockedState = false
            )
        )
    }
    var authResult by remember { mutableStateOf(AppLockAuthResult.NONE) }
    var authInProgress by remember { mutableStateOf(false) }
    var wasInBackground by rememberSaveable { mutableStateOf(false) }

    val errorMessage = AppLockAuthPolicy.messageFor(authResult)
    val lifecycleOwner = LocalLifecycleOwner.current

    fun triggerUnlock(force: Boolean = false) {
        if (!tokenStorage.isAppLockEnabled()) {
            isAppLocked = false
            return
        }
        if (!force && !isAppLocked) return
        if (authInProgress) return

        authInProgress = true
        authResult = AppLockAuthResult.NONE

        BiometricAuthManager.authenticate(
            activity = activity,
            title = "Unlock Monitor",
            subtitle = "Authenticate with Face, Fingerprint, or PIN",
            onSuccess = {
                authResult = AppLockAuthResult.SUCCESS
                // Single authoritative unlock decision.
                if (AppLockAuthPolicy.unlockPermitted(authResult)) {
                    isAppLocked = false
                }
                authInProgress = false
            },
            onError = { err ->
                authResult = if (err.contains("cancel", ignoreCase = true)) {
                    AppLockAuthResult.CANCELED
                } else {
                    AppLockAuthResult.ERROR
                }
                // No unlock: the lock remains active on every error path.
                authInProgress = false
            },
            onUnavailable = {
                authResult = AppLockAuthResult.UNAVAILABLE
                authInProgress = false
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val prefs = activity.getSharedPreferences("app_monitor_settings_preferences", android.content.Context.MODE_PRIVATE)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    val lockOnResume = prefs.getBoolean("pref_bio_resume", false)
                    if (tokenStorage.isAppLockEnabled() && lockOnResume && !authInProgress) {
                        wasInBackground = true
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    val lockOnResume = prefs.getBoolean("pref_bio_resume", false)
                    if (!authInProgress && lockOnResume && AppLockLifecyclePolicy.shouldRelockOnResume(tokenStorage.isAppLockEnabled(), wasInBackground)) {
                        wasInBackground = false
                        isAppLocked = true
                        triggerUnlock(force = true)
                    } else {
                        wasInBackground = false
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isAppLocked) {
        if (isAppLocked && tokenStorage.isAppLockEnabled()) {
            triggerUnlock()
        }
    }

    // The stored preference is authoritative on every composition, including
    // immediately after an in-process unlock or a configuration change.
    val locked = AppLockLifecyclePolicy.mustShowLock(
        appLockEnabled = tokenStorage.isAppLockEnabled(),
        unlockedInProcess = !isAppLocked
    )
    Box(modifier = Modifier.fillMaxSize()) {
        // NEW-09: protected content is not composed at all while locked, so no
        // transition frame can expose it beneath the overlay and no background
        // data load runs before authentication succeeds.
        if (!locked) {
            content()
        } else {
            AppLockOverlay(
                onUnlockClick = { triggerUnlock(force = true) },
                errorMessage = errorMessage
            )
        }
    }
}
