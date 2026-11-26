package app.monitor.core.common

/**
 * Pure, JVM-testable policy for app-lock lifecycle transitions.
 *
 * AUD-02 / NEW-09: the persisted app-lock preference (owned by TokenStorage) is
 * the single authoritative source of truth. An unlocked state is process-scoped
 * and is never restored from a saved-instance bundle, so a fresh process always
 * starts locked whenever the preference is enabled.
 */
object AppLockLifecyclePolicy {

    /**
     * State used to initialise the lock on a fresh composition / fresh process.
     *
     * [restoredUnlockedState] is deliberately ignored: a saved bundle, a killed
     * process or a restored activity may claim "unlocked", but only a new trusted
     * authentication may clear the lock. Kept as a parameter so callers and tests
     * document that the value is never trusted.
     */
    @Suppress("UNUSED_PARAMETER")
    fun lockedAfterProcessDeath(appLockEnabled: Boolean, restoredUnlockedState: Boolean): Boolean =
        appLockEnabled

    /** Re-lock when the app returns from background after having been paused/stopped. */
    fun shouldRelockOnResume(appLockEnabled: Boolean, wasInBackground: Boolean): Boolean =
        appLockEnabled && wasInBackground

    /**
     * Whether protected content must be hidden right now.
     * The preference is authoritative: disabling it is the only way to bypass
     * the overlay without a new authentication.
     */
    fun mustShowLock(appLockEnabled: Boolean, unlockedInProcess: Boolean): Boolean =
        appLockEnabled && !unlockedInProcess
}
