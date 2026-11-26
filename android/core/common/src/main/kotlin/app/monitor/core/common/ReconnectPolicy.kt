package app.monitor.core.common

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Bounded reconnect policy for the desktop stream (pure JVM logic, no Android deps).
 *
 * Uses full-ish exponential backoff with symmetric jitter and a hard cap of 60 s:
 *  - attempt 0 starts near [baseDelayMs];
 *  - each consecutive failure doubles the window (clamped before jitter);
 *  - jitter is injected between [1 - jitterFraction, 1 + jitterFraction] so that
 *    many devices recovering at once do not synchronise into a reconnect storm;
 *  - [onSuccess] / [reset] return the policy to the initial state.
 *
 * The cap is deliberately applied *after* jitter as well, so no random draw can
 * ever exceed 60 s. All randomness is injected, which keeps this testable without
 * sleeping or seeding global state.
 */
class ReconnectPolicy(
    private val baseDelayMs: Long = 1_000L,
    val maxDelayMs: Long = 60_000L,
    private val multiplier: Double = 2.0,
    private val jitterFraction: Double = 0.20,
    private val random: () -> Double = Math::random
) {
    private var consecutiveFailures: Int = 0

    /** Number of failures since the last [onSuccess] / [reset]. */
    val failures: Int get() = consecutiveFailures

    /**
     * Returns the delay to wait before the next reconnect and advances the
     * failure counter. Never negative and never greater than [maxDelayMs].
     */
    fun nextDelayMs(): Long {
        val exponent = consecutiveFailures.coerceIn(0, MAX_EXPONENT)
        val uncapped = baseDelayMs * multiplier.pow(exponent)
        val window = minOf(uncapped, maxDelayMs.toDouble())

        val draw = random().coerceIn(0.0, 1.0)
        val jitter = 1.0 - jitterFraction + (2.0 * jitterFraction * draw)
        val delayed = (window * jitter).roundToLong()

        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(Int.MAX_VALUE)
        return delayed.coerceIn(0L, maxDelayMs)
    }

    /** Call whenever a ticket is acquired or any server event is received. */
    fun onSuccess() {
        consecutiveFailures = 0
    }

    fun reset() = onSuccess()

    private companion object {
        /** 2^20 > 60 000 / 1 000, so higher exponents cannot change the clamped window. */
        const val MAX_EXPONENT = 20
    }
}
