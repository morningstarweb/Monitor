package app.monitor.core.common

/**
 * Pure, testable security policy for the app lock.
 *
 * AUD-02: only a successful, trusted Android authentication result may unlock
 * protected content. Biometric unavailability, cancellation, failure and errors
 * are all explicitly non-authorizing. Keeping this decision in a pure function
 * makes the security property unit-testable without a device or Robolectric.
 */
enum class AppLockAuthResult {
    NONE,
    SUCCESS,
    FAILURE,
    CANCELED,
    UNAVAILABLE,
    ERROR
}

object AppLockAuthPolicy {
    /** The single authoritative unlock decision. */
    fun unlockPermitted(result: AppLockAuthResult): Boolean = result == AppLockAuthResult.SUCCESS

    /** User-facing message for a non-authorizing result; null for success/none. */
    fun messageFor(result: AppLockAuthResult): String? = when (result) {
        AppLockAuthResult.SUCCESS,
        AppLockAuthResult.NONE -> null

        AppLockAuthResult.UNAVAILABLE ->
            "Device authentication is unavailable. App Lock remains active."

        AppLockAuthResult.CANCELED -> "Authentication canceled. App Lock remains active."
        AppLockAuthResult.FAILURE -> "Authentication not recognized. Try again."
        AppLockAuthResult.ERROR -> "Authentication failed. App Lock remains active."
    }
}
