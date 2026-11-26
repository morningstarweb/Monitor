package app.monitor.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool

private val httpPools = Collections.synchronizedMap(WeakHashMap<HttpClient, ConnectionPool>())

/** Evicts pooled idle TCP/HTTP2 sockets after an OS network route transition (Wi-Fi <-> 5G). */
fun evictMonitorHttpPool(client: HttpClient) {
    runCatching { httpPools[client]?.evictAll() }
}

class MonitorApiException(val status: Int, message: String) : Exception(message)

suspend fun <T> apiResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

fun createMonitorHttpClient(json: Json): HttpClient {
    val pool = ConnectionPool(5, 30, TimeUnit.SECONDS)
    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                connectionPool(pool)
                pingInterval(15, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
            addInterceptor { chain ->
                val req = chain.request()
                if (!req.isHttps) {
                    LanEndpointPolicy.requireAllowedCleartextEndpoint(req.url.host, req.url.port)
                }
                chain.proceed(req)
            }
        }
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = 3_000
            requestTimeoutMillis = 60_000
        }
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value !in 200..299 && response.status != HttpStatusCode.SwitchingProtocols) {
                    val payload = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
                    val message = payload?.get("message")?.jsonPrimitive?.content
                        ?: "Desktop returned HTTP ${response.status.value}"
                    throw MonitorApiException(response.status.value, message)
                }
            }
        }
    }
    httpPools[client] = pool
    return client
}
