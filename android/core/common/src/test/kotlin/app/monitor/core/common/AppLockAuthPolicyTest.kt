package app.monitor.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-02 regression: only a trusted SUCCESS result may unlock protected content.
 * These are behavioral tests of the policy used by AppLockGate.
 */
class AppLockAuthPolicyTest {

    @Test
    fun onlySuccessAuthorizesUnlock() {
        assertTrue(AppLockAuthPolicy.unlockPermitted(AppLockAuthResult.SUCCESS))

        assertFalse(AppLockAuthPolicy.unlockPermitted(AppLockAuthResult.NONE))
        assertFalse(AppLockAuthPolicy.unlockPermitted(AppLockAuthResult.FAILURE))
        assertFalse(AppLockAuthPolicy.unlockPermitted(AppLockAuthResult.CANCELED))
        assertFalse(AppLockAuthPolicy.unlockPermitted(AppLockAuthResult.UNAVAILABLE))
        assertFalse(AppLockAuthPolicy.unlockPermitted(AppLockAuthResult.ERROR))
    }

    @Test
    fun unavailableBiometricsKeepLockActiveWithExplanatoryMessage() {
        val message = AppLockAuthPolicy.messageFor(AppLockAuthResult.UNAVAILABLE)
        assertFalse(AppLockAuthPolicy.unlockPermitted(AppLockAuthResult.UNAVAILABLE))
        assertTrue(message!!.contains("remains active", ignoreCase = true))
    }

    @Test
    fun unsuccessfulResultsNeverClearTheLock() {
        for (result in listOf(
            AppLockAuthResult.FAILURE,
            AppLockAuthResult.CANCELED,
            AppLockAuthResult.ERROR
        )) {
            assertFalse(AppLockAuthPolicy.unlockPermitted(result))
            assertTrue(AppLockAuthPolicy.messageFor(result)!!.isNotBlank())
        }
    }

    @Test
    fun successAndInitialStateHaveNoErrorMessage() {
        assertNull(AppLockAuthPolicy.messageFor(AppLockAuthResult.SUCCESS))
        assertNull(AppLockAuthPolicy.messageFor(AppLockAuthResult.NONE))
    }
}
