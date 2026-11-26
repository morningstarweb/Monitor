package app.monitor.core.network

import app.monitor.core.model.StreamKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LanNoiseIkLiveTest {

    @Test
    fun testLiveDesktopNoiseIkHandshakeAndTelemetryOverLan() = runBlocking {
        val host = System.getProperty("monitor.live.host")?.takeIf { it.isNotBlank() } ?: "192.168.31.101"
        val port = System.getProperty("monitor.live.port")?.toIntOrNull() ?: 9480

        // Live hardware test: probe if live desktop peer is reachable before attempting live handshake
        val isLiveDesktopReachable = try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), 500)
                true
            }
        } catch (_: Exception) {
            false
        }
        org.junit.Assume.assumeTrue(
            "Live desktop peer not reachable at $host:$port (required only for physical LAN verification)",
            isLiveDesktopReachable
        )

        val client = HttpClient(OkHttp) {
            install(WebSockets) {
                pingIntervalMillis = 15_000
            }
        }

        try {
            // Step 1: Discover Desktop and fetch host Noise public key
            println("[Monitor] [LanDiscovery] Probing Desktop at http://$host:$port/api/v2/noise-key ...")
            val keyResp = client.get("http://$host:$port/api/v2/noise-key").bodyAsText()
            println("[Monitor] [LanDiscovery] Desktop response: $keyResp")
            val hostKeyHex = Regex("\"public_key\"\\s*:\\s*\"([0-9a-fA-F]+)\"").find(keyResp)?.groupValues?.get(1)
                ?: error("public_key not found in $keyResp")
            val hostNoiseKey = decodeNoiseHex(hostKeyHex)
            assertNotNull(hostNoiseKey)
            assertTrue(hostNoiseKey.size == 32)
            println("[Monitor] [LanDiscovery] Host static Noise public key: $hostKeyHex")

            // Step 2: Initialize Noise IK Viewer Handshake with PROLOGUE_LAN
            val viewerKeys = NoiseKeyMaterial.generate()
            val handshake = NoiseIkViewerHandshake(
                viewerKeys,
                hostNoiseKey,
                NoiseIkViewerHandshake.PROLOGUE_LAN
            )

            // Step 3: Connect WebSocket and execute Noise IK Handshake
            val wsUrl = "ws://$host:$port/api/v2/stream"
            println("[Monitor] [WebSocket] Connecting to $wsUrl ...")

            client.webSocket(urlString = wsUrl) {
                println("[Monitor] [WebSocket] WebSocket connected! Initiating Noise IK Handshake ...")

                // Send Handshake Message 1
                val msg1 = handshake.writeMessage1()
                println("[Monitor] [NoiseChannel] Sending Handshake Message 1 (${msg1.size} bytes) ...")
                send(Frame.Binary(true, msg1))

                // Receive Handshake Message 2
                val msg2Frame = withTimeout(10_000) {
                    while (true) {
                        val frame = incoming.receive()
                        if (frame is Frame.Binary) return@withTimeout frame
                    }
                }
                val msg2Bytes = (msg2Frame as Frame.Binary).readBytes()
                println("[Monitor] [NoiseChannel] Received Handshake Message 2 (${msg2Bytes.size} bytes)")

                val handshakeResult = handshake.readMessage2(msg2Bytes)
                val session = handshakeResult.session
                println("[Monitor] [NoiseChannel] Noise IK Handshake SUCCEEDED! Session ID: ${session.sessionId}, Trust Tier: ${handshakeResult.trustTier}")
                assertNotNull(session)

                // Step 4: Receive initial encrypted Telemetry Snapshot frame
                val telemetryFrame = withTimeout(10_000) {
                    while (true) {
                        val frame = incoming.receive()
                        if (frame is Frame.Binary) return@withTimeout frame
                    }
                }
                val telemetryCiphertext = (telemetryFrame as Frame.Binary).readBytes()
                val telemetryEnvelope = session.decryptEnvelope(telemetryCiphertext)
                println("[Monitor] [WebSocket] Decrypted Server Frame: stream=${telemetryEnvelope.stream}, type=${telemetryEnvelope.type}, payload=${telemetryEnvelope.payload}")

                assertTrue(telemetryEnvelope.stream == StreamKind.TELEMETRY)
                assertNotNull(telemetryEnvelope.payload)

                val payloadObj = telemetryEnvelope.payload.toString()
                println("[Monitor] [NoiseChannel] Successfully validated end-to-end encrypted LAN frames! Payload preview: ${payloadObj.take(120)}...")
            }
        } finally {
            client.close()
        }
    }
}
