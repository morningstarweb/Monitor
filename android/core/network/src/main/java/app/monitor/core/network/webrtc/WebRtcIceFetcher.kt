package app.monitor.core.network.webrtc

import app.monitor.core.network.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import org.webrtc.PeerConnection

object WebRtcIceFetcher {
    private val fallbackServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    suspend fun fetch(
        client: HttpClient,
        json: Json,
        tokenStorage: TokenStorage,
        brokerUrl: String
    ): List<PeerConnection.IceServer> {
        return try {
            val token = tokenStorage.getCloudSessionToken() ?: return fallbackServers
            val endpoint = "$brokerUrl/api/v2/webrtc/turn-credentials"
            val text = client.post(endpoint) {
                header("Authorization", "Bearer $token")
                timeout { requestTimeoutMillis = 6000 }
            }.bodyAsText()
            val resp = json.decodeFromString(TurnCredentialsResponse.serializer(), text)
            if (resp.success && resp.ice_servers.isNotEmpty()) {
                resp.ice_servers.flatMap { s ->
                    s.urls.map { url ->
                        val b = PeerConnection.IceServer.builder(url)
                        if (!s.username.isNullOrBlank()) b.setUsername(s.username)
                        if (!s.credential.isNullOrBlank()) b.setPassword(s.credential)
                        b.createIceServer()
                    }
                }
            } else fallbackServers
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { fallbackServers }
    }
}
