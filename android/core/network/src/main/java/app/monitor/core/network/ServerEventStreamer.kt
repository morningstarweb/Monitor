package app.monitor.core.network

import app.monitor.core.model.ServerEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.IOException

object ServerEventStreamer {
    fun stream(
        client: HttpClient,
        json: Json,
        tokenStorage: TokenStorage,
        ticket: String
    ): Flow<ServerEvent> = flow {
        if (ticket == "cloud_sync") {
            val cloudToken = tokenStorage.getCloudSessionToken() ?: return@flow
            var ticks = 0
            while (true) {
                try {
                    val data = CloudTelemetryClient.fetch(client, json, tokenStorage, cloudToken)
                    if (data.snapshot != null) emit(ServerEvent.TelemetryTick(data.snapshot, isOnline = data.isOnline))
                    for (project in data.projects) emit(ServerEvent.ProjectUpdated(project))
                } catch (_: Exception) {}
                ticks++
                if (ticks % 4 == 0) {
                    val raw = tokenStorage.getServerHost().trim()
                    if (raw.isNotBlank() && raw != "127.0.0.1" &&
                        LanEndpointPolicy.isAllowedCleartextEndpoint(raw, tokenStorage.getServerPort())
                    ) {
                        val canReachLan = runCatching {
                            client.get("http://$raw:${tokenStorage.getServerPort()}/api/v1/health") {
                                timeout { connectTimeoutMillis = 600 }
                            }
                        }.isSuccess
                        if (canReachLan) break // Return to ultra-low latency Direct LAN
                    }
                }
                delay(3_000)
            }
        } else {
            val rawHost = tokenStorage.getServerHost().trim().ifBlank { "10.0.2.2" }
            val port = tokenStorage.getServerPort()
            val host = if (rawHost == "127.0.0.1" || rawHost == "localhost") "10.0.2.2" else rawHost
            LanEndpointPolicy.requireAllowedCleartextEndpoint(host, port)
            val wsUrl = "ws://$host:$port/api/v1/stream?ticket=$ticket"

            client.webSocket(urlString = wsUrl) {
                while (true) {
                    val received = withTimeoutOrNull(60_000) { incoming.receiveCatching() }
                        ?: throw IOException("Desktop stopped responding. Reconnecting…")
                    val frame = received.getOrNull() ?: break
                    when (frame) {
                        is Frame.Text -> {
                            json.decodeServerEvent(frame.readText())?.let { emit(it) }
                        }
                        is Frame.Ping -> {
                            send(Frame.Pong(frame.data))
                        }
                        is Frame.Close -> break
                        else -> Unit
                    }
                }
            }
        }
    }

}
