package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitIdentity(
    @SerialName("name") val name: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("is_configured") val isConfigured: Boolean = false
)

@Serializable
data class SetGitIdentityRequest(
    @SerialName("name") val name: String,
    @SerialName("email") val email: String
)
