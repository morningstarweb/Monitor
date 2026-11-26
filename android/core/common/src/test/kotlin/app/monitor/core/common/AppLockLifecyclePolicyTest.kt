package app.monitor.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEW-09 regression: the lock is process-scoped and the stored preference is
 * authoritative. Pure JVM tests of the policy used by AppLockGate — no
 * instrumentation or emulator required.
 */
class AppLockLifecyclePolicyTest {

    @Test
    fun freshProcessStartsLockedWhenPreferenceEnabled() {
        assertTrue(
            AppLockLifecyclePolicy.lockedAfterProcessDeath(
                appLockEnabled = true,
                restoredUnlockedState = true
            )
        )
    }

    @Test
    fun savedUnlockedBundleNeverBypassesTheLock() {
        for (restored in listOf(true, false)) {
            assertTrue(AppLockLifecyclePolicy.lockedAfterProcessDeath(true, restored))
        }
    }

    @Test
    fun disabledPreferenceMeansNoLockAtAll() {
        assertFalse(AppLockLifecyclePolicy.lockedAfterProcessDeath(false, false))
        assertFalse(AppLockLifecyclePolicy.mustShowLock(false, unlockedInProcess = false))
        assertFalse(AppLockLifecyclePolicy.shouldRelockOnResume(false, wasInBackground = true))
    }

    @Test
    fun returningFromBackgroundRelocksOnlyWhenPreferenceEnabled() {
        assertTrue(AppLockLifecyclePolicy.shouldRelockOnResume(true, true))
        assertFalse(AppLockLifecyclePolicy.shouldRelockOnResume(true, false))
    }

    @Test
    fun overlayVisibilityTracksAuthoritativePreferenceAndProcessState() {
        assertTrue(AppLockLifecyclePolicy.mustShowLock(true, unlockedInProcess = false))
        assertFalse(AppLockLifecyclePolicy.mustShowLock(true, unlockedInProcess = true))
    }

    @Test
    fun inProcessUnlockIsAlwaysReEvaluatedAgainstThePreference() {
        // A successful in-process unlock only hides the overlay for this process.
        assertFalse(AppLockLifecyclePolicy.mustShowLock(true, unlockedInProcess = true))
        // The next composition still consults the authoritative preference and the
        // process-scoped flag; going back to background re-locks (see test above).
        assertTrue(AppLockLifecyclePolicy.mustShowLock(true, unlockedInProcess = false))
    }
}
