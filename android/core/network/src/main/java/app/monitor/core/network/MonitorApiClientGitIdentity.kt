package app.monitor.core.network

import app.monitor.core.model.GitIdentity
import app.monitor.core.model.SetGitIdentityRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun MonitorApiClient.getGitIdentity(): Result<GitIdentity> = apiResult {
    v2RequestIfCloud("git.identity.get", buildJsonObject {}) {
        localJsonRequest { base, token -> client.get("$base/git/identity") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
        }.body<JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token -> client.get("$base/git/identity") {
        timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
    }.body() }
}

suspend fun MonitorApiClient.setGitIdentity(name: String, email: String): Result<GitIdentity> = apiResult {
    v2RequestIfCloud("git.identity.set", buildJsonObject { put("name", name); put("email", email) }) {
        localJsonRequest { base, token -> client.post("$base/git/identity") {
            timeout { requestTimeoutMillis = 5_000 }; header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json); setBody(SetGitIdentityRequest(name, email))
        }.body<JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token -> client.post("$base/git/identity") {
        timeout { requestTimeoutMillis = 5_000 }; header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json); setBody(SetGitIdentityRequest(name, email))
    }.body() }
}
