package app.monitor.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure JVM tests of the on-disk secure-storage envelope and value codec.
 * No Android Keystore or device is involved: only the binary format is checked.
 */
class SecureEnvelopeTest {

    private val nonce = ByteArray(SecureEnvelope.NONCE_LENGTH) { (it + 1).toByte() }
    private val ciphertext = ByteArray(48) { (it * 7).toByte() } // 32 bytes payload + 16-byte GCM tag

    @Test
    fun wrapUnwrapRoundTripPreservesNonceAndCiphertext() {
        val envelope = SecureEnvelope.wrap(nonce, ciphertext)
        val unwrapped = SecureEnvelope.unwrap(envelope)

        assertArrayEquals(nonce, unwrapped!!.nonce)
        assertArrayEquals(ciphertext, unwrapped.ciphertext)
        // Header is magic(4) + version(1) + reserved(1) + nonce(12).
        assertEquals(4 + 1 + 1 + SecureEnvelope.NONCE_LENGTH + ciphertext.size, envelope.size)
    }

    @Test
    fun wrapRejectsWrongNonceLengthAndMissingTag() {
        try {
            SecureEnvelope.wrap(ByteArray(8), ciphertext)
            fail("nonce must be exactly 12 bytes")
        } catch (_: IllegalArgumentException) {
        }

        try {
            SecureEnvelope.wrap(nonce, ByteArray(4))
            fail("ciphertext must include the GCM tag")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun unwrapFailsClosedOnCorruptHeaders() {
        val valid = SecureEnvelope.wrap(nonce, ciphertext)

        val badMagic = valid.copyOf().also { it[0] = 0x58 }
        val badVersion = valid.copyOf().also { it[4] = (SecureEnvelope.VERSION + 1).toByte() }
        val badReserved = valid.copyOf().also { it[5] = 1 }
        val truncated = valid.copyOfRange(0, 6 + SecureEnvelope.NONCE_LENGTH + 4)

        assertNull(SecureEnvelope.unwrap(badMagic))
        assertNull(SecureEnvelope.unwrap(badVersion))
        assertNull(SecureEnvelope.unwrap(badReserved))
        assertNull(SecureEnvelope.unwrap(truncated))
        assertNull(SecureEnvelope.unwrap(ByteArray(0)))
    }

    @Test
    fun valueCodecRoundTripsAllSupportedTypesIncludingUnicode() {
        val values = linkedMapOf(
            "device_token" to StoredValue.StoredText("mon_dev_üñîçødé_42"),
            "server_port" to StoredValue.StoredNumber(9480),
            "pref_app_lock_enabled" to StoredValue.StoredFlag(true),
            "noise_static_private" to StoredValue.StoredText("base64key")
        )

        val decoded = StoredValueCodec.decode(StoredValueCodec.encode(values))

        assertEquals(values, decoded)
    }

    @Test
    fun valueCodecRejectsMalformedPayloads() {
        val valid = StoredValueCodec.encode(mapOf("k" to StoredValue.StoredText("v")))

        assertNull(StoredValueCodec.decode(valid.copyOfRange(0, valid.size - 1)))
        assertNull(StoredValueCodec.decode(valid.copyOf().also { it[3] = 99 })) // impossible entry count
        assertNull(StoredValueCodec.decode(valid + 0x00)) // trailing bytes

        val unknownType = StoredValueCodec.encode(
            mapOf("k" to StoredValue.StoredText("v"))
        ).copyOf().also { it[8] = 99 }
        assertNull(StoredValueCodec.decode(unknownType))
    }

    @Test
    fun decodeOfEmptyMapIsEmpty() {
        val decoded = StoredValueCodec.decode(StoredValueCodec.encode(emptyMap()))
        assertTrue(decoded!!.isEmpty())
    }
}
