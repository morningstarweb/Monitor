package app.monitor.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bounded, jittered reconnect backoff — pure JVM tests, no device required. */
class ReconnectPolicyTest {

    @Test
    fun firstAttemptStartsNearBaseWithSymmetricJitter() {
        val low = ReconnectPolicy(baseDelayMs = 1_000L, jitterFraction = 0.2, random = { 0.0 })
        val high = ReconnectPolicy(baseDelayMs = 1_000L, jitterFraction = 0.2, random = { 1.0 })

        assertEquals(800L, low.nextDelayMs())
        assertEquals(1_200L, high.nextDelayMs())
    }

    @Test
    fun delayGrowsExponentiallyAcrossConsecutiveFailures() {
        val policy = ReconnectPolicy(baseDelayMs = 1_000L, jitterFraction = 0.0, random = { 0.5 })
        val delays = (0..5).map { policy.nextDelayMs() }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L), delays)
        assertEquals(6, policy.failures)
    }

    @Test
    fun delayIsCappedAtSixtySecondsEvenWithMaximumJitter() {
        val policy = ReconnectPolicy(jitterFraction = 0.5, random = { 1.0 })
        repeat(40) {
            val delay = policy.nextDelayMs()
            assertTrue("delay $delay must not exceed 60_000", delay <= 60_000L)
        }
        assertEquals(60_000L, policy.nextDelayMs())
    }

    @Test
    fun randomDrawsCanNeverPushDelayAboveCap() {
        val policy = ReconnectPolicy(random = { 1.0 })
        repeat(100) { assertTrue(policy.nextDelayMs() <= policy.maxDelayMs) }
    }

    @Test
    fun successResetsBackoffToInitialWindow() {
        val policy = ReconnectPolicy(jitterFraction = 0.0, random = { 0.5 })
        repeat(8) { policy.nextDelayMs() }
        assertTrue(policy.failures >= 8)

        policy.onSuccess()
        assertEquals(0, policy.failures)
        assertEquals(1_000L, policy.nextDelayMs())
    }

    @Test
    fun resetIsEquivalentToSuccess() {
        val policy = ReconnectPolicy(jitterFraction = 0.0, random = { 0.5 })
        repeat(4) { policy.nextDelayMs() }
        policy.reset()
        assertEquals(0, policy.failures)
        assertEquals(1_000L, policy.nextDelayMs())
    }

    @Test
    fun outOfRangeRandomIsCoercedAndDelayNeverNegative() {
        val low = ReconnectPolicy(jitterFraction = 0.2, random = { -5.0 })
        val high = ReconnectPolicy(jitterFraction = 0.2, random = { 5.0 })
        assertEquals(800L, low.nextDelayMs())
        assertEquals(1_200L, high.nextDelayMs())
    }
}
