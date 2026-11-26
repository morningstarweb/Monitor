package app.monitor.core.network

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DualPathTransportManagerTest {

    @Test
    fun testConnectionPathRelayClassification() {
        assertFalse(ConnectionPath.LAN_DIRECT.isRelay)
        assertFalse(ConnectionPath.WEBRTC_P2P.isRelay)
        assertTrue(ConnectionPath.WEBRTC_TURN.isRelay)
        assertTrue(ConnectionPath.CLOUD_RELAY_WSS.isRelay)
        assertFalse(ConnectionPath.DISCONNECTED.isRelay)
    }

    @Test
    fun createMonitorHttpClientBlocksNonLanCleartextRequests() = runBlocking {
        val client = createMonitorHttpClient(Json { ignoreUnknownKeys = true })
        try {
            val failure = runCatching {
                client.get("http://example.com:9480/api/v1/health")
            }.exceptionOrNull()
            assertTrue(
                "Expected IOException blocking non-LAN cleartext request, got $failure",
                failure is IOException
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun lanEndpointPolicyPermitsDynamicPortRangeAndRejectsOutOfRangePorts() {
        assertTrue(LanEndpointPolicy.isAllowedCleartextEndpoint("192.168.1.20", 9480))
        assertTrue(LanEndpointPolicy.isAllowedCleartextEndpoint("192.168.1.20", 9485))
        assertTrue(LanEndpointPolicy.isAllowedCleartextEndpoint("10.0.2.2", 9500))
        assertFalse(LanEndpointPolicy.isAllowedCleartextEndpoint("192.168.1.20", 80))
        assertFalse(LanEndpointPolicy.isAllowedCleartextEndpoint("192.168.1.20", 9501))
        assertFalse(LanEndpointPolicy.isAllowedCleartextEndpoint("example.com", 9480))
    }

    @Test
    fun cloudFallbackPromotesImmediatelyWhenLanOrWebRtcStalls() {
        assertFalse(
            DualPathTransportManager.shouldPromoteCloudFallback(
                currentPath = ConnectionPath.LAN_DIRECT,
                lanConnected = true,
                webrtcOpen = false,
                nowMs = 10_000L,
                lastLanEventAtMs = 9_500L,
                lastWebRtcEventAtMs = 0L
            )
        )
        assertTrue(
            DualPathTransportManager.shouldPromoteCloudFallback(
                currentPath = ConnectionPath.LAN_DIRECT,
                lanConnected = false,
                webrtcOpen = false,
                nowMs = 10_000L,
                lastLanEventAtMs = 7_000L,
                lastWebRtcEventAtMs = 0L
            )
        )
        assertFalse(
            DualPathTransportManager.shouldPromoteCloudFallback(
                currentPath = ConnectionPath.WEBRTC_P2P,
                lanConnected = false,
                webrtcOpen = true,
                nowMs = 10_000L,
                lastLanEventAtMs = 0L,
                lastWebRtcEventAtMs = 9_000L
            )
        )
        assertTrue(
            DualPathTransportManager.shouldPromoteCloudFallback(
                currentPath = ConnectionPath.WEBRTC_P2P,
                lanConnected = false,
                webrtcOpen = true,
                nowMs = 10_000L,
                lastLanEventAtMs = 0L,
                lastWebRtcEventAtMs = 7_000L
            )
        )
    }
}
