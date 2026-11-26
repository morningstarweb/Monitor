package app.monitor.core.network

import androidx.test.platform.app.InstrumentationRegistry
import app.monitor.core.model.PairingRequest
import app.monitor.core.model.ServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Compiled Android APK <-> Compiled Windows Rust (`monitor-transport`) live
 * integration test running inside Android ART VM (`10.0.2.2:9485` host loopback).
 *
 * Skips cleanly when instrumentation argument `bridge_pairing_code` is absent.
 */
@RunWith(JUnit4::class)
class WindowsBridgeE2EAndroidTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun compiledAndroidPairsCompletesNoiseIkAndExecutesRpcAgainstWindowsHost() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val pairingCode = args.getString("bridge_pairing_code")?.trim().orEmpty()
        assumeTrue(
            "Skipping live Windows bridge test without -e bridge_pairing_code",
            pairingCode.isNotEmpty()
        )

        val host = args.getString("bridge_host")?.trim()?.ifEmpty { null } ?: "10.0.2.2"
        val port = args.getString("bridge_port")?.toIntOrNull() ?: 9485
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = TokenStorage(context)
        storage.clearAll()

        val apiClient = MonitorApiClient(storage)
        val client = createMonitorHttpClient(json)
        val lanTransport = LanTransportV2(
            client = client,
            json = json,
            tokenStorage = storage
        )
        val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val health = apiClient.getHealth(host, port).getOrThrow()
            assertEquals("Monitor", health.appName)

            val viewerNoisePair = storage.getOrCreateNoiseStaticKeyPair()
            val viewerPublicHex = viewerNoisePair.publicKey.joinToString("") { "%02x".format(it) }

            val paired = apiClient.pair(
                host,
                port,
                PairingRequest(
                    pairingCode = pairingCode,
                    deviceName = "Android-ART-Bridge",
                    publicKey = viewerPublicHex
                )
            ).getOrThrow()
            assertTrue(paired.deviceToken.startsWith("mon_dev_"))
            storage.savePairing(host, port, paired.deviceToken)

            val snapshot = apiClient.getSystemSnapshot().getOrThrow()
            assertTrue(snapshot.hostname.isNotBlank())
            val projects = apiClient.getProjects().getOrThrow()
            assertTrue(projects.isEmpty())
            val perms = apiClient.getPermissions().getOrThrow()
            assertTrue(perms.localAccessAllowed)

            var receivedTelemetry = false
            val job = streamScope.launch {
                lanTransport.streamEvents(host, port).collect { event ->
                    if (event is ServerEvent.TelemetryTick) {
                        receivedTelemetry = true
                    }
                }
            }

            withTimeout(10_000) {
                while (!lanTransport.isConnected || !receivedTelemetry) {
                    delay(100)
                }
            }
            val session = lanTransport.session
            assertNotNull(session)
            session!!

            assertNotNull(storage.getHostNoiseKey(host, port))

            val pollReq = lanTransport.rpc.begin(
                sessionId = session.sessionId,
                epoch = session.epoch,
                commandType = "telemetry.poll",
                payload = JsonObject(emptyMap())
            )
            try {
                lanTransport.sendRequest(pollReq)
                val pollResp = lanTransport.rpc.await(pollReq, 5_000L)
                assertNotNull(pollResp)
            } finally {
                lanTransport.rpc.abandon(pollReq)
            }

            val cmdReq = lanTransport.rpc.begin(
                sessionId = session.sessionId,
                epoch = session.epoch,
                commandType = "projects.list",
                payload = JsonObject(emptyMap())
            )
            try {
                lanTransport.sendRequest(cmdReq)
                val cmdResp = lanTransport.rpc.await(cmdReq, 5_000L)
                assertNotNull(cmdResp)
            } finally {
                lanTransport.rpc.abandon(cmdReq)
            }
            job.cancel()
        } finally {
            streamScope.cancel()
            lanTransport.resetSession()
            runCatching { client.close() }
            apiClient.close()
        }
    }
}
