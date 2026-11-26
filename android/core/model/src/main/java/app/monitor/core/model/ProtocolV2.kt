package app.monitor.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class StreamKind {
    @SerialName("control")
    CONTROL,
    @SerialName("telemetry")
    TELEMETRY,
    @SerialName("bulk")
    BULK
}

@Serializable
enum class MessageTypeV2 {
    HELLO,
    HELLO_ACK,
    CAPABILITIES,
    PING,
    PONG,
    SESSION_CLOSE,
    ERROR,
    CREDIT,
    SNAPSHOT,
    DELTA,
    RESYNC_REQUEST,
    WATCHDOG_ALERT,
    PROJECTS_REQUEST,
    PROJECTS_RESULT,
    GIT_GRAPH_REQUEST,
    GIT_GRAPH_RESULT,
    GIT_DIFF_REQUEST,
    GIT_DIFF_CHUNK,
    GIT_DIFF_END,
    COMMAND_REQUEST,
    COMMAND_ACCEPTED,
    COMMAND_REJECTED,
    COMMAND_PROGRESS,
    COMMAND_RESULT,
    COMMAND_STATUS_REQUEST,
    COMMAND_STATUS_RESULT,
    PATH_PREPARE,
    PATH_COMMIT
}

@Serializable
enum class OperationTier {
    @SerialName("T0")
    T0_MONITORING,
    @SerialName("T1")
    T1_READ_REPOSITORY,
    @SerialName("T2")
    T2_STATE_MUTATION,
    @SerialName("T3")
    T3_DANGEROUS
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EnvelopeV2(
    @EncodeDefault
    @SerialName("protocol_version")
    val protocolVersion: Int = 2,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("message_id")
    val messageId: String,
    val epoch: Long,
    val stream: StreamKind,
    val type: MessageTypeV2,
    val seq: Long,
    val ack: Long,
    @SerialName("deadline_ms")
    val deadlineMs: Long,
    @SerialName("command_id")
    val commandId: String? = null,
    val payload: JsonElement? = null
)
