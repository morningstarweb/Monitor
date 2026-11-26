package app.monitor.feature.gitworkspace.connection

import app.monitor.core.model.BranchInfo
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitFileChange
import app.monitor.core.model.GitGraphPage
import app.monitor.core.model.GitIdentity
import app.monitor.core.model.GitOperationRequest
import app.monitor.core.model.GitOperationResult
import app.monitor.core.model.GitWorkspaceSnapshot
import app.monitor.core.model.MergePrResponse
import app.monitor.core.model.PullRequestSummary
import app.monitor.core.model.TerminalCommandResult
import app.monitor.core.model.TerminalStopResult
import app.monitor.core.network.MonitorApiClient
import app.monitor.core.network.executeOperation
import app.monitor.core.network.getBranches
import app.monitor.core.network.getCommitFiles
import app.monitor.core.network.getFileDiff
import app.monitor.core.network.getGitIdentity
import app.monitor.core.network.getGraph
import app.monitor.core.network.getPullRequests
import app.monitor.core.network.getWorkspace
import app.monitor.core.network.mergePullRequest
import app.monitor.core.network.runTerminalCommandApi
import app.monitor.core.network.setGitIdentity
import app.monitor.core.network.stopTerminalCommandApi

interface WorkspaceConnection {
    suspend fun workspace(repoId: String): Result<GitWorkspaceSnapshot>
    suspend fun graph(repoId: String, skip: Int): Result<GitGraphPage>
    suspend fun branches(repoId: String): Result<List<BranchInfo>>
    suspend fun diff(repoId: String, path: String, staged: Boolean, commit: String? = null): Result<FileDiffResponse>
    suspend fun commitFiles(repoId: String, commitOid: String): Result<List<GitFileChange>> = Result.success(emptyList())
    suspend fun execute(repoId: String, request: GitOperationRequest): Result<GitOperationResult>
    suspend fun pullRequests(repoId: String): Result<List<PullRequestSummary>> = Result.success(emptyList())
    suspend fun mergePullRequest(repoId: String, prNumber: Long, method: String = "merge"): Result<MergePrResponse> = Result.success(MergePrResponse(true, "Merged"))
    suspend fun getGitIdentity(): Result<GitIdentity> = Result.success(GitIdentity())
    suspend fun setGitIdentity(name: String, email: String): Result<GitIdentity> = Result.success(GitIdentity(name, email, true))
    suspend fun runTerminal(repoId: String, command: String, shell: String? = null, sessionId: String = "1"): Result<TerminalCommandResult> = Result.success(TerminalCommandResult(0, "", "", 0))
    suspend fun stopTerminal(repoId: String, sessionId: String = "1"): Result<TerminalStopResult> = Result.success(TerminalStopResult(true, null))
}

class LiveWorkspaceConnection(private val client: MonitorApiClient) : WorkspaceConnection {
    override suspend fun workspace(repoId: String) = client.getWorkspace(repoId)
    override suspend fun graph(repoId: String, skip: Int) = client.getGraph(repoId, skip = skip, limit = 80)
    override suspend fun branches(repoId: String) = client.getBranches(repoId)
    override suspend fun diff(repoId: String, path: String, staged: Boolean, commit: String?) = client.getFileDiff(repoId, path, staged, commit)
    override suspend fun commitFiles(repoId: String, commitOid: String) = client.getCommitFiles(repoId, commitOid)
    override suspend fun execute(repoId: String, request: GitOperationRequest) = client.executeOperation(repoId, request)
    override suspend fun pullRequests(repoId: String) = client.getPullRequests(repoId)
    override suspend fun mergePullRequest(repoId: String, prNumber: Long, method: String) = client.mergePullRequest(repoId, prNumber, method)
    override suspend fun getGitIdentity() = client.getGitIdentity()
    override suspend fun setGitIdentity(name: String, email: String) = client.setGitIdentity(name, email)
    override suspend fun runTerminal(repoId: String, command: String, shell: String?, sessionId: String) = client.runTerminalCommandApi(repoId, command, shell, sessionId)
    override suspend fun stopTerminal(repoId: String, sessionId: String) = client.stopTerminalCommandApi(repoId, sessionId)
}
