package app.monitor.feature.dashboard.settings.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * NEW-07: validates a user-configured broker URL before any request is made.
 *
 * Authenticated broker calls carry a Bearer token, so only HTTPS is accepted.
 * The parsed URI is checked (not a string prefix) to prevent userinfo tricks,
 * malformed URLs and deceptive hostnames from being used as an endpoint.
 */
fun validateBrokerUrl(rawBrokerUrl: String): String {
    val trimmed = rawBrokerUrl.trim().trimEnd('/')
    require(trimmed.isNotEmpty()) { "Broker URL is empty" }
    val uri = runCatching { URI(trimmed) }.getOrNull()
        ?: throw IllegalArgumentException("Malformed broker URL")
    require(uri.scheme?.equals("https", ignoreCase = true) == true) {
        "Broker URL must use HTTPS"
    }
    require(uri.userInfo == null) { "Broker URL must not contain credentials" }
    require(!uri.host.isNullOrBlank()) { "Broker URL is missing a host" }
    require(uri.port == -1 || uri.port in 1..65535) { "Broker URL has an invalid port" }
    require(uri.fragment == null) { "Broker URL must not contain a fragment" }
    return trimmed
}

data class BrokerProbeResult(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val edgeColo: String? = null,
    val errorMessage: String? = null
)

interface IBrokerProbeClient {
    suspend fun ping(rawBrokerUrl: String): Result<BrokerProbeResult>
    suspend fun logoutSession(rawBrokerUrl: String, sessionToken: String): Result<Boolean>
}

open class DefaultBrokerProbeClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IBrokerProbeClient {

    override suspend fun ping(rawBrokerUrl: String): Result<BrokerProbeResult> = withContext(ioDispatcher) {
        val start = System.currentTimeMillis()
        try {
            val cleanUrl = validateBrokerUrl(rawBrokerUrl)
            val target = if (cleanUrl.endsWith("/health") || cleanUrl.endsWith("/api/health")) {
                cleanUrl
            } else {
                "$cleanUrl/health"
            }
            val url = URL(target)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()

            val json = runCatching { JSONObject(body) }.getOrNull()
            val status = json?.optString("status") ?: ""
            val isHealthy = code in 200..299 && (status == "healthy" || status.isBlank())
            val region = json?.optString("region")?.takeIf { it.isNotBlank() }?.uppercase()

            Result.success(BrokerProbeResult(isHealthy = isHealthy, latencyMs = elapsed, edgeColo = region))
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            Result.failure(e)
        }
    }

    override suspend fun logoutSession(rawBrokerUrl: String, sessionToken: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val cleanUrl = validateBrokerUrl(rawBrokerUrl)
            val target = "$cleanUrl/api/v1/auth/logout"
            val url = URL(target)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $sessionToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                // Never follow a redirect that could move the Bearer header to
                // an attacker-controlled or downgraded endpoint (NEW-07).
                instanceFollowRedirects = false
                doOutput = true
            }
            conn.outputStream.use { os ->
                os.write("{}".toByteArray())
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("Cloudflare logout returned HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object BrokerProbeClient : DefaultBrokerProbeClient(Dispatchers.IO)
