package app.monitor.core.network

import app.monitor.core.model.EnvelopeV2
import app.monitor.core.model.MessageTypeV2
import app.monitor.core.model.StreamKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class SecureSessionV2Test {

    private val keyA = ByteArray(32) { (it + 1).toByte() }
    private val keyB = ByteArray(32) { (it + 33).toByte() }

    @Test
    fun testBidirectionalEncryptionAndDecryption() {
        val alice = SecureSessionV2(sendKey = keyA, recvKey = keyB, sessionId = "s1")
        val bob = SecureSessionV2(sendKey = keyB, recvKey = keyA, sessionId = "s1")

        val payload = "Hello secure monitor".toByteArray(Charsets.UTF_8)
        val ciphertext = alice.encrypt(payload)

        assertEquals(payload.size + 16, ciphertext.size)
        assertFalse(payload.contentEquals(ciphertext))

        val decrypted = bob.decrypt(ciphertext)
        assertTrue(payload.contentEquals(decrypted))
        assertEquals(1L, alice.currentSendNonce())
        assertEquals(1L, bob.currentRecvNonce())
    }

    @Test
    fun testEnvelopeSerializationAndDecryption() {
        val alice = SecureSessionV2(sendKey = keyA, recvKey = keyB, sessionId = "s1")
        val bob = SecureSessionV2(sendKey = keyB, recvKey = keyA, sessionId = "s1")

        val envelope = EnvelopeV2(
            protocolVersion = 2,
            sessionId = "s1",
            messageId = "m1",
            epoch = 1L,
            stream = StreamKind.TELEMETRY,
            type = MessageTypeV2.SNAPSHOT,
            seq = 0L,
            ack = 0L,
            deadlineMs = 15000L,
            payload = buildJsonObject {
                put("cpu", 25.5)
                put("ram", 62.0)
            }
        )

        val ciphertext = alice.encryptEnvelope(envelope)
        val receivedEnv = bob.decryptEnvelope(ciphertext)

        assertEquals(envelope.protocolVersion, receivedEnv.protocolVersion)
        assertEquals(envelope.stream, receivedEnv.stream)
        assertEquals(envelope.type, receivedEnv.type)
        assertNotNull(receivedEnv.payload)
    }

    @Test
    fun testTamperedCiphertextThrowsBadTag() {
        val alice = SecureSessionV2(sendKey = keyA, recvKey = keyB, sessionId = "s1")
        val bob = SecureSessionV2(sendKey = keyB, recvKey = keyA, sessionId = "s1")

        val ciphertext = alice.encrypt("Confidential data".toByteArray(Charsets.UTF_8))
        ciphertext[0] = (ciphertext[0].toInt() xor 0xFF).toByte()

        try {
            bob.decrypt(ciphertext)
            fail("Decryption of tampered ciphertext must throw AEADBadTagException")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e.cause is AEADBadTagException)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOversizedPayloadIsRejected() {
        val session = SecureSessionV2(sendKey = keyA, recvKey = keyB, sessionId = "s1")
        val oversized = ByteArray(17 * 1024)
        session.encrypt(oversized)
    }

    @Test
    fun testEnvelopeWireJsonAlwaysIncludesProtocolVersionWithDefaultJson() {
        val alice = SecureSessionV2(sendKey = keyA, recvKey = keyB, sessionId = "s1")
        val bob = SecureSessionV2(sendKey = keyB, recvKey = keyA, sessionId = "s1")
        val envelope = EnvelopeV2(
            sessionId = "s1",
            messageId = "m2",
            epoch = 1L,
            stream = StreamKind.CONTROL,
            type = MessageTypeV2.COMMAND_REQUEST,
            seq = 1L,
            ack = 0L,
            deadlineMs = 5000L
        )
        val defaultJsonEncoded = Json.encodeToString(EnvelopeV2.serializer(), envelope)
        assertTrue(defaultJsonEncoded.contains("\"protocol_version\":2"))
        val ct = alice.encryptEnvelope(envelope)
        val wireJson = String(bob.decrypt(ct), Charsets.UTF_8)
        assertTrue(wireJson.contains("\"protocol_version\":2"))
    }
}
