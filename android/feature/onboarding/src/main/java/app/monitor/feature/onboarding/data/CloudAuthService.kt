package app.monitor.feature.onboarding.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.monitor.core.network.TokenStorage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CloudAuthResult(
    val token: String,
    val email: String,
    val name: String,
    val detectedHost: String?,
    val detectedPort: Int?,
    val picture: String? = null,
    val detectedDesktopDeviceId: String? = null,
    val detectedDesktopName: String? = null
)

private data class DetectedDesktop(
    val host: String?,
    val port: Int?,
    val deviceId: String?,
    val name: String?
)

object CloudAuthService {
    private const val BROKER_URL = "https://your-monitor-broker.workers.dev"

    suspend fun verifyGoogleToken(idToken: String, deviceName: String, tokenStorage: TokenStorage): Result<CloudAuthResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BROKER_URL/api/v1/auth/google-verify")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true

            val deviceId = tokenStorage.getOrCreateCloudDeviceId()
            val noisePublicKey = tokenStorage.getOrCreateNoiseStaticKeyPair().publicKey
                .joinToString("") { "%02x".format(it) }
            val jsonBody = JSONObject().apply {
                put("id_token", idToken)
                put("device_id", deviceId)
                put("device_name", deviceName)
                put("platform", "android")
                put("role", "client")
                put("noise_public_key", noisePublicKey)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bodyStr = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                val respJson = JSONObject(bodyStr)
                val token = respJson.getString("token")
                val user = respJson.getJSONObject("user")
                val email = user.getString("email")
                val name = user.optString("name", email.substringBefore("@"))
                val picture = user.optString("picture").takeIf { it.isNotBlank() }

                val desktop = queryDevices(token)

                Result.success(
                    CloudAuthResult(
                        token = token,
                        email = email,
                        name = name,
                        detectedHost = desktop.host,
                        detectedPort = desktop.port,
                        picture = picture,
                        detectedDesktopDeviceId = desktop.deviceId,
                        detectedDesktopName = desktop.name
                    )
                )
            } else {
                val errJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                val errMsg = errJson?.optString("error") ?: "Cloud verification failed ($code)"
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryDevices(token: String): DetectedDesktop {
        return try {
            val devUrl = URL("$BROKER_URL/api/v1/devices")
            val devConn = devUrl.openConnection() as HttpURLConnection
            devConn.setRequestProperty("Authorization", "Bearer $token")
            devConn.connectTimeout = 8000
            devConn.readTimeout = 8000
            var fallback: DetectedDesktop? = null
            if (devConn.responseCode in 200..299) {
                val devBody = devConn.inputStream.bufferedReader().use { it.readText() }
                val devObj = JSONObject(devBody)
                val devArr = devObj.optJSONArray("devices")
                if (devArr != null) {
                    for (i in 0 until devArr.length()) {
                        val d = devArr.getJSONObject(i)
                        if (d.optString("role") == "desktop") {
                            val lanIp = d.optString("lan_ip").takeIf { it.isNotBlank() && it != "null" }
                            val lanPort = d.optInt("lan_port", 9480).takeIf { it > 0 }
                            val id = d.optString("id").takeIf { it.isNotBlank() && it != "null" }
                            val name = d.optString("name").takeIf { it.isNotBlank() && it != "null" }
                            val candidate = DetectedDesktop(lanIp, lanPort, id, name)
                            val isOnline = d.optInt("is_online", 0) == 1 || d.optBoolean("is_online", false)
                            if (isOnline && lanIp != null) return candidate
                            if (isOnline && fallback == null) fallback = candidate
                            if (fallback == null) fallback = candidate
                        }
                    }
                }
            }
            fallback ?: DetectedDesktop(null, null, null, null)
        } catch (_: Exception) {
            DetectedDesktop(null, null, null, null)
        }
    }
}
