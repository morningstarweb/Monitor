package app.monitor.security

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import app.monitor.MainActivity
import app.monitor.core.common.DestructiveActionGate
import app.monitor.core.network.TokenStorage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AppLockAndDestructiveGateAndroidTest {
    @Test
    fun mainActivityLaunchesWithAppLockEnabledAndDestructiveGateFailsClosed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val storage = TokenStorage(context)
        storage.clearAll()
        storage.setAppLockEnabled(true)
        assertTrue(storage.isAppLockEnabled())
        assertTrue(storage.isDestructiveOpsBiometricGated())

        // Verify destructive actions fail closed without biometric confirmation.
        var triggered = false
        var denial = ""
        DestructiveActionGate.runGuarded(
            activity = null,
            appLockEnabled = true,
            gateDestructiveOps = true,
            title = "Confirm Delete",
            subtitle = "Authenticate",
            onDenied = { denial = it },
            onAuthorized = { triggered = true }
        )
        assertFalse(triggered)
        assertTrue(denial.isNotBlank())

        // Launch real MainActivity under API 37 ART with AndroidKeyStore AppLock active.
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val activity = instrumentation.startActivitySync(intent)
        try {
            instrumentation.waitForIdleSync()
            assertFalse(activity.isFinishing)
            assertTrue(TokenStorage(context).isAppLockEnabled())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            storage.clearAll()
        }
    }
}
