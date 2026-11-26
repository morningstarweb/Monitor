package app.monitor.core.network.webrtc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebRtcSignalMessage(
    val signal: String,
    @SerialName("viewer_session_id") val viewerSessionId: String? = null,
    val sdp: String? = null,
    val sdp_type: String? = null,
    val candidate: IceCandidatePayload? = null
)

@Serializable
data class IceCandidatePayload(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0
)

@Serializable
data class TurnCredentialsResponse(
    val success: Boolean = false,
    val ice_servers: List<IceServerConfig> = emptyList(),
    val ttl_seconds: Long = 86400L
)

@Serializable
data class IceServerConfig(
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null
)
