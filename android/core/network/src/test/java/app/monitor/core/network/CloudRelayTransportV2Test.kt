package app.monitor.core.network

import app.monitor.core.model.EnvelopeV2
import app.monitor.core.model.MessageTypeV2
import app.monitor.core.model.StreamKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudRelayTransportV2Test {

    @Test
    fun testViewerTicketResultDataModel() {
        val ticketResult = ViewerTicketResult(
            ticket = "tkt_1234567890abcdef",
            hostNoisePublicKey = "aabbccdd"
        )

        assertEquals("tkt_1234567890abcdef", ticketResult.ticket)
        assertEquals("aabbccdd", ticketResult.hostNoisePublicKey)
    }

    @Test
    fun testCloudRelayEnvelopeEncryptionRoundtrip() {
        val sendKey = ByteArray(32) { 0x11 }
        val recvKey = ByteArray(32) { 0x22 }
        val viewerSession = SecureSessionV2(sendKey, recvKey, "test-cloud-session", 1L)
        val hostSession = SecureSessionV2(recvKey, sendKey, "test-cloud-session", 1L)

        val pingEnvelope = EnvelopeV2(
            protocolVersion = 2,
            sessionId = "test-cloud-session",
            messageId = UUID.randomUUID().toString(),
            epoch = 1L,
            stream = StreamKind.CONTROL,
            type = MessageTypeV2.PING,
            seq = viewerSession.currentSendNonce(),
            ack = 0L,
            deadlineMs = 30000L,
            payload = buildJsonObject { put("ts", 123456789L) }
        )

        val encryptedBytes = viewerSession.encryptEnvelope(pingEnvelope)
        assertTrue(encryptedBytes.isNotEmpty())

        val decryptedEnvelope = hostSession.decryptEnvelope(encryptedBytes)
        assertEquals(2, decryptedEnvelope.protocolVersion)
        assertEquals("test-cloud-session", decryptedEnvelope.sessionId)
        assertEquals(MessageTypeV2.PING, decryptedEnvelope.type)
        assertNotNull(decryptedEnvelope.payload)
    }

    @Test
    fun testDecodeNonTelemetryEventsV2() {
        val json = Json { ignoreUnknownKeys = true }

        // Test 1: Watchdog alert via MessageTypeV2.WATCHDOG_ALERT
        val alertPayload = buildJsonObject {
            put("alert_id", "alt_123")
            put("rule_name", "high_memory")
            put("message", "Memory usage exceeds 90%")
            put("severity", "critical")
        }
        val alertEvent = json.decodeV2PayloadEvent(MessageTypeV2.WATCHDOG_ALERT, alertPayload)
        assertTrue(alertEvent is app.monitor.core.model.ServerEvent.AlertTriggered)
        val castAlert = alertEvent as app.monitor.core.model.ServerEvent.AlertTriggered
        assertEquals("alt_123", castAlert.alertId)
        assertEquals("critical", castAlert.severity)

        // Test 2: Rust ServerEvent envelope {"event": ..., "data": ...}
        val rustEnvelopePayload = buildJsonObject {
            put("event", "AlertTriggered")
            put("data", alertPayload)
        }
        val rustEvent = json.decodeV2PayloadEvent(MessageTypeV2.WATCHDOG_ALERT, rustEnvelopePayload)
        assertTrue(rustEvent is app.monitor.core.model.ServerEvent.AlertTriggered)

        // Test 3: ResyncRequired
        val resyncEvent = json.decodeV2PayloadEvent(MessageTypeV2.RESYNC_REQUEST, null)
        assertTrue(resyncEvent is app.monitor.core.model.ServerEvent.ResyncRequired)
    }

    @Test
    fun testNoiseHandshakePrologues() {
        assertEquals("MONITOR_V3_2_CLOUD_V2", String(NoiseIkViewerHandshake.PROLOGUE_CLOUD, Charsets.US_ASCII))
        assertEquals("MONITOR_V3_2_LAN_V2", String(NoiseIkViewerHandshake.PROLOGUE_LAN, Charsets.US_ASCII))
    }
}
