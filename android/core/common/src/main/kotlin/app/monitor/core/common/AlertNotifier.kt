package app.monitor.core.common

/**
 * Cross-module seam for posting a watchdog alert to the Android notification shade.
 *
 * Defined in `core:common` (which has no dependency on `core:model`) so that both
 * `feature:dashboard` (the producer) and `app` (the Android implementation) can
 * share it without an inverted dependency.
 *
 * SECURITY / PRIVACY: the interface intentionally carries only an opaque alert id
 * and a coarse severity. Repository names, file paths, terminal text and raw
 * alert messages must never cross this boundary into a system notification.
 */
interface AlertNotifier {
    fun postAlert(alertId: String, severity: String)

    /** Default used in unit tests and in any host that has no notification shade. */
    object NoOp : AlertNotifier {
        override fun postAlert(alertId: String, severity: String) = Unit
    }
}
