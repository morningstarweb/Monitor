package app.monitor.core.network

import app.monitor.core.model.ServerEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 5 White-Box Adversarial Hardening Suite for ServerEvent decoding (:core:network).
 *
 * Empirically tests:
 * 1. Corrupt WebSocket frame isolation.
 * 2. Missing event names.
 * 3. Unknown future event tolerance without connection drops.
 * 4. Empty and malformed JSON payloads.
 */
class Tier5ServerEventAdversarialTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun testCorruptAndMalformedJsonFramesThrowSerializationOrIllegalArgument() {
        val malformedFrames = listOf(
            "",
            "not a json",
            "12345",
            "true",
            "\"just a string\"",
            "[]",
            "{}",
            """{"data": {}}""",
            """{"event": "TelemetryTick", "data": {}}"""
        )

        for (frame in malformedFrames) {
            val failure = runCatching { json.decodeServerEvent(frame) }.isFailure
            assertTrue("Expected failure for malformed frame: '$frame'", failure)
        }
    }

    @Test
    fun testNullOrUnknownFutureEventsIgnoredCleanly() {
        val futureEvent = """{"event": "FutureEvent2027", "data": {"key": "value"}}"""
        assertNull(json.decodeServerEvent(futureEvent))

        val nullEvent = """{"event": null}"""
        assertNull(json.decodeServerEvent(nullEvent))
    }

    @Test
    fun testResyncRequiredDecodesProperly() {
        val resyncEvent = """{"event": "ResyncRequired"}"""
        val result = json.decodeServerEvent(resyncEvent)
        assertEquals(ServerEvent.ResyncRequired, result)
    }
}
