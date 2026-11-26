package app.monitor.core.common

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveActionGateTest {
    @Test
    fun requiresAuthenticationOnlyWhenBothAppLockAndGatingAreEnabled() {
        assertFalse(DestructiveActionGate.requiresAuthentication(appLockEnabled = false, gateDestructiveOps = true))
        assertFalse(DestructiveActionGate.requiresAuthentication(appLockEnabled = true, gateDestructiveOps = false))
        assertFalse(DestructiveActionGate.requiresAuthentication(appLockEnabled = false, gateDestructiveOps = false))
        assertTrue(DestructiveActionGate.requiresAuthentication(appLockEnabled = true, gateDestructiveOps = true))
    }

    @Test
    fun missingActivityFailsClosedWhenGateEnabled() {
        var executed = false
        var deniedReason: String? = null
        DestructiveActionGate.runGuarded(
            activity = null,
            appLockEnabled = true,
            gateDestructiveOps = true,
            title = "Confirm",
            subtitle = "Auth",
            onDenied = { deniedReason = it },
            onAuthorized = { executed = true }
        )
        assertFalse(executed)
        assertTrue(!deniedReason.isNullOrBlank())
    }

    @Test
    fun gateAllowsActionDirectlyWhenDisabled() {
        var executed = false
        var authInvoked = false
        DestructiveActionGate.runGuarded(
            activity = null,
            appLockEnabled = false,
            gateDestructiveOps = true,
            title = "Confirm",
            subtitle = "Auth",
            authenticate = { _, _, _, _, _, _ -> authInvoked = true },
            onAuthorized = { executed = true }
        )
        assertFalse(authInvoked)
        assertTrue(executed)
    }

    @Test
    fun errorOrUnavailablePromptNeverInvokesActionAndSuccessDoes() {
        val dummyActivity = Activity()
        var executed = false
        var denied = ""

        DestructiveActionGate.runGuarded(
            activity = dummyActivity,
            appLockEnabled = true,
            gateDestructiveOps = true,
            title = "Confirm",
            subtitle = "Auth",
            authenticate = { _, _, _, _, onError, _ -> onError("Canceled") },
            onDenied = { denied = it },
            onAuthorized = { executed = true }
        )
        assertFalse(executed)
        assertEquals("Canceled", denied)

        DestructiveActionGate.runGuarded(
            activity = dummyActivity,
            appLockEnabled = true,
            gateDestructiveOps = true,
            title = "Confirm",
            subtitle = "Auth",
            authenticate = { _, _, _, _, _, onUnavailable -> onUnavailable() },
            onDenied = { denied = it },
            onAuthorized = { executed = true }
        )
        assertFalse(executed)
        assertTrue(denied.isNotBlank())

        DestructiveActionGate.runGuarded(
            activity = dummyActivity,
            appLockEnabled = true,
            gateDestructiveOps = true,
            title = "Confirm",
            subtitle = "Auth",
            authenticate = { _, _, _, onSuccess, _, _ -> onSuccess() },
            onAuthorized = { executed = true }
        )
        assertTrue(executed)
    }
}
