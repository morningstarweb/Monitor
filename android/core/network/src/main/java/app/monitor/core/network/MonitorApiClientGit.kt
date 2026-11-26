package app.monitor.core.network

import app.monitor.core.model.BranchInfo
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitFileChange
import app.monitor.core.model.GitGraphPage
import app.monitor.core.model.GitOperationRequest
import app.monitor.core.model.GitOperationResult
import app.monitor.core.model.GitWorkspaceSnapshot
import app.monitor.core.model.MergePrRequest
import app.monitor.core.model.MergePrResponse
import app.monitor.core.model.PullRequestSummary
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun MonitorApiClient.getWorkspace(repoId: String): Result<GitWorkspaceSnapshot> = apiResult {
    v2RequestIfCloud("git.workspace", buildJsonObject { put("repo_id", repoId) }) {
        localJsonRequest { base, token -> client.get("$base/projects/$repoId/workspace") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.get("$base/projects/$repoId/workspace") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }.body()
    }
}
suspend fun MonitorApiClient.getFileDiff(
    repoId: String,
    file: String,
    staged: Boolean = false,
    commit: String? = null
): Result<FileDiffResponse> = apiResult {
    v2RequestIfCloud("git.diff", buildJsonObject {
        put("repo_id", repoId); put("file", file); put("staged", staged)
        if (!commit.isNullOrBlank()) put("commit", commit)
    }) {
        localJsonRequest { base, token -> client.get("$base/projects/$repoId/diff") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
            parameter("file", file); parameter("staged", staged)
            if (!commit.isNullOrBlank()) parameter("commit", commit)
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.get("$base/projects/$repoId/diff") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
            parameter("file", file)
            parameter("staged", staged)
            if (!commit.isNullOrBlank()) {
                parameter("commit", commit)
            }
        }.body()
    }
}

suspend fun MonitorApiClient.getCommitFiles(
    repoId: String,
    commitOid: String
): Result<List<GitFileChange>> = apiResult {
    v2RequestIfCloud("git.commit_files", buildJsonObject {
        put("repo_id", repoId); put("commit_oid", commitOid)
    }) {
        localJsonRequest { base, token -> client.get("$base/projects/$repoId/commits/$commitOid/files") {
            timeout { connectTimeoutMillis = 5_000 }; header("Authorization", "Bearer $token")
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.get("$base/projects/$repoId/commits/$commitOid/files") {
            timeout { connectTimeoutMillis = 5_000 }
            header("Authorization", "Bearer $token")
        }.body()
    }
}

suspend fun MonitorApiClient.getBranches(repoId: String): Result<List<BranchInfo>> = apiResult {
    v2RequestIfCloud("git.branches", buildJsonObject { put("repo_id", repoId) }) {
        localJsonRequest { base, token -> client.get("$base/projects/$repoId/branches") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.get("$base/projects/$repoId/branches") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }.body()
    }
}

suspend fun MonitorApiClient.getGraph(repoId: String, skip: Int = 0, limit: Int = 80): Result<GitGraphPage> = apiResult {
    v2RequestIfCloud("git.graph", buildJsonObject {
        put("repo_id", repoId); put("skip", skip); put("limit", limit)
    }) {
        localJsonRequest { base, token -> client.get("$base/projects/$repoId/graph") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
            parameter("skip", skip); parameter("limit", limit)
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.get("$base/projects/$repoId/graph") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
            parameter("skip", skip)
            parameter("limit", limit)
        }.body()
    }
}

suspend fun MonitorApiClient.executeOperation(repoId: String, request: GitOperationRequest): Result<GitOperationResult> = apiResult {
    val operationPayload = json.encodeToJsonElement(GitOperationRequest.serializer(), request)
    val operationType = when (request.operation) {
        is app.monitor.core.model.GitOperation.StageFiles,
        app.monitor.core.model.GitOperation.StageAll -> "git.stage"
        is app.monitor.core.model.GitOperation.UnstageFiles,
        app.monitor.core.model.GitOperation.UnstageAll -> "git.unstage"
        is app.monitor.core.model.GitOperation.DiscardFile -> "git.discard_unstaged"
        is app.monitor.core.model.GitOperation.Commit,
        is app.monitor.core.model.GitOperation.CommitAndPush -> "git.commit"
        is app.monitor.core.model.GitOperation.Fetch -> "git.fetch"
        is app.monitor.core.model.GitOperation.Pull -> "git.pull"
        is app.monitor.core.model.GitOperation.Push -> "git.push"
        is app.monitor.core.model.GitOperation.CreateBranch -> "git.create_branch"
        is app.monitor.core.model.GitOperation.SwitchBranch -> "git.switch_branch"
        is app.monitor.core.model.GitOperation.DeleteBranch -> "git.delete_branch"
        is app.monitor.core.model.GitOperation.MergeBranch -> "git.merge_branch"
        is app.monitor.core.model.GitOperation.MergeAllBranches -> "git.merge_all_branches"
    }
    v2RequestIfCloud(operationType, buildJsonObject {
        put("repo_id", repoId); put("operation", operationPayload)
    }) {
        localJsonRequest { base, token -> client.post("$base/projects/$repoId/operations") {
            timeout { requestTimeoutMillis = 150_000 }; header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json); setBody(request)
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.post("$base/projects/$repoId/operations") {
            timeout { requestTimeoutMillis = 150_000 }
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}

suspend fun MonitorApiClient.getPullRequests(repoId: String): Result<List<PullRequestSummary>> = apiResult {
    v2RequestIfCloud("git.pull_requests", buildJsonObject { put("repo_id", repoId) }) {
        localJsonRequest { base, token -> client.get("$base/projects/$repoId/pulls") {
            timeout { connectTimeoutMillis = 3_000 }; header("Authorization", "Bearer $token")
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let {
        return@apiResult decodeV2Result(it)
    }
    gitRequest { base, token ->
        client.get("$base/projects/$repoId/pulls") {
            timeout { connectTimeoutMillis = 3_000 }
            header("Authorization", "Bearer $token")
        }.body()
    }
}

suspend fun MonitorApiClient.mergePullRequest(
    repoId: String,
    prNumber: Long,
    method: String = "merge"
): Result<MergePrResponse> = apiResult {
    v2RequestIfCloud("merge_pr", buildJsonObject {
        put("repo_id", repoId); put("pr_number", prNumber); put("method", method)
    }) {
        localJsonRequest { base, token -> client.post("$base/projects/$repoId/pulls/$prNumber/merge") {
            timeout { requestTimeoutMillis = 60_000 }; header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json); setBody(MergePrRequest(method = method))
        }.body<kotlinx.serialization.json.JsonElement>() }
    }?.let { return@apiResult decodeV2Result(it) }
    gitRequest { base, token ->
        client.post("$base/projects/$repoId/pulls/$prNumber/merge") {
            timeout { requestTimeoutMillis = 60_000 }
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(MergePrRequest(method = method))
        }.body()
    }
}
