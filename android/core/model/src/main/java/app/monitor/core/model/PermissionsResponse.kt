package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PermissionsResponse(
    @SerialName("git_access_allowed") val gitAccessAllowed: Boolean = true,
    @SerialName("delete_access_allowed") val deleteAccessAllowed: Boolean = false,
    @SerialName("cloud_access_allowed") val cloudAccessAllowed: Boolean = true,
    @SerialName("local_access_allowed") val localAccessAllowed: Boolean = true
)
