package app.monitor.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** NEW-08: LAN host identity must be pinned across reconnects. */
class HostKeyPinningTest {

    private val original = ByteArray(32) { it.toByte() }
    private val attacker = ByteArray(32) { (it + 100).toByte() }

    @Test
    fun firstContactReturnsKeyAndPin() {
        val (key, pin) = HostKeyPinning.resolve(original, null, null)
        assertArrayEquals(original, key)
        assertEquals(noiseKeyToHex(original), pin)
    }

    @Test
    fun matchingReconnectUsesPinnedKey() {
        val (key, pin) = HostKeyPinning.resolve(original, noiseKeyToHex(original), null)
        assertArrayEquals(original, key)
        assertEquals(null, pin)
    }

    @Test
    fun substitutedHostKeyIsRejected() {
        try {
            HostKeyPinning.resolve(attacker, noiseKeyToHex(original), null)
            fail("a changed host key must never be silently trusted")
        } catch (_: HostKeyMismatchException) {
            // expected
        }
    }

    @Test
    fun explicitOverrideIsUsedWithoutChangingPin() {
        val (key, pin) = HostKeyPinning.resolve(attacker, noiseKeyToHex(original), original)
        assertArrayEquals(original, key)
        assertEquals(null, pin)
    }
}
