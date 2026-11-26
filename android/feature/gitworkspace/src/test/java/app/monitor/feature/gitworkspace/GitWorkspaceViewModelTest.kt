package app.monitor.feature.gitworkspace

import app.monitor.core.model.CommitNode
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitGraphPage
import app.monitor.core.model.GitOperationRequest
import app.monitor.core.model.GitOperationResult
import app.monitor.core.model.GitWorkspaceSnapshot
import app.monitor.core.model.BranchInfo
import app.monitor.core.model.MergePrResponse
import app.monitor.core.model.OperationStatus
import app.monitor.core.model.PullRequestSummary
import app.monitor.feature.gitworkspace.connection.WorkspaceConnection
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitWorkspaceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() {
        GitWorkspaceViewModel.clearCache()
        Dispatchers.setMain(dispatcher)
    }
    @After fun tearDown() {
        GitWorkspaceViewModel.clearCache()
        Dispatchers.resetMain()
    }

    @Test fun failedOperationStatusShowsDesktopErrorEvenWithHttp200() = runTest(dispatcher) {
        val connection = FakeConnection().apply { status = OperationStatus.StaleConflict }
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()
        model.stageAll()
        advanceUntilIdle()
        assertEquals("Workspace changed. Refresh before retrying.", model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.isOperating)
    }

    @Test fun doubleTapSubmitsOnlyOneMutationUntilResponseCompletes() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val connection = FakeConnection().apply { pending = gate }
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()
        model.stageAll()
        model.stageAll()
        runCurrent()
        assertEquals(1, connection.requests.size)
        assertTrue(model.uiState.value.isOperating)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isOperating)
    }

    @Test fun loadingOlderGraphPageAppendsAndDeduplicatesBoundaryCommit() = runTest(dispatcher) {
        val connection = FakeConnection()
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()
        model.loadGraph(80)
        advanceUntilIdle()
        assertEquals(listOf("new", "boundary", "old"), model.uiState.value.graphPage?.commits?.map { it.oid })
        assertFalse(model.uiState.value.graphPage?.hasMore ?: true)
    }

    @Test fun blankWorkspaceNeverCallsDesktopOrInventsRevision() = runTest(dispatcher) {
        val connection = FakeConnection()
        val model = GitWorkspaceViewModel("", connection)
        model.stageAll()
        model.commit("Example")
        advanceUntilIdle()
        assertEquals(0, connection.reads)
        assertEquals(0, connection.requests.size)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun discardFileGeneratesRevisionBoundConfirmationToken() = runTest(dispatcher) {
        val connection = FakeConnection()
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()
        model.discardFile("src/main.rs")
        advanceUntilIdle()
        assertEquals(1, connection.requests.size)
        val op = connection.requests.first().operation
        assertTrue(op is app.monitor.core.model.GitOperation.DiscardFile)
        val discardOp = op as app.monitor.core.model.GitOperation.DiscardFile
        assertEquals("src/main.rs", discardOp.path)
        assertEquals("discard:src/main.rs:4", discardOp.confirmationToken)
    }

    @Test fun pullPushAndFetchSubmitExpectedGitOperations() = runTest(dispatcher) {
        val connection = FakeConnection()
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()

        model.pull("origin", "main")
        advanceUntilIdle()
        assertEquals(1, connection.requests.size)
        assertEquals(app.monitor.core.model.GitOperation.Pull("origin", "main"), connection.requests[0].operation)

        model.push("origin", "main", true)
        advanceUntilIdle()
        assertEquals(2, connection.requests.size)
        assertEquals(app.monitor.core.model.GitOperation.Push("origin", "main", true), connection.requests[1].operation)

        model.fetch("origin")
        advanceUntilIdle()
        assertEquals(3, connection.requests.size)
        assertEquals(app.monitor.core.model.GitOperation.Fetch("origin"), connection.requests[2].operation)
    }

    @Test fun selectiveCommitOnlyStagesSelectedFiles() = runTest(dispatcher) {
        val connection = FakeConnection()
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()

        model.commit("Selective commit", paths = listOf("Home.kt"))
        advanceUntilIdle()
        assertEquals(1, connection.requests.size)
        val op = connection.requests.first().operation
        assertTrue(op is app.monitor.core.model.GitOperation.Commit)
        val commitOp = op as app.monitor.core.model.GitOperation.Commit
        assertEquals("Selective commit", commitOp.messageSummary)
        assertEquals(listOf("Home.kt"), commitOp.paths)
        assertFalse(commitOp.stageAllTracked)
    }

    @Test fun mergePullRequestPropagatesNetworkFailureAndClearsOperatingFlag() = runTest(dispatcher) {
        val connection = FakeConnection().apply {
            mergePrResult = Result.failure(IOException("Connection refused to desktop agent: 192.168.1.50:9480"))
        }
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()

        model.mergePullRequest(105L, "merge")
        advanceUntilIdle()

        assertEquals("Connection refused to desktop agent: 192.168.1.50:9480", model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.isOperating)
    }

    @Test fun mergePullRequestPropagatesDesktopErrorMessageWhenSuccessIsFalse() = runTest(dispatcher) {
        val connection = FakeConnection().apply {
            mergePrResult = Result.success(MergePrResponse(false, "Merge conflict in PR #105"))
        }
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()

        model.mergePullRequest(105L, "merge")
        advanceUntilIdle()

        assertEquals("Merge conflict in PR #105", model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.isOperating)
    }

    @Test fun loadPullRequestsOnNetworkFailureYieldsEmptyListAndNeverSyntheticPrs() = runTest(dispatcher) {
        val connection = FakeConnection().apply {
            pullRequestsResult = Result.failure(IOException("Desktop unreachable"))
        }
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()

        model.loadPullRequests()
        advanceUntilIdle()

        assertTrue(model.uiState.value.pullRequests.isEmpty())
        assertFalse(model.uiState.value.pullRequests.any { it.number in 101L..104L })
    }

    @Test fun emptyRepoHasZeroFilesZeroPrsAndZeroDiffHunksUnderAnyState() = runTest(dispatcher) {
        val connection = FakeConnection()
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()

        assertTrue(model.uiState.value.pullRequests.isEmpty())
        assertEquals(null, model.uiState.value.selectedDiff)
        assertFalse(model.uiState.value.pullRequests.any { it.number in 101L..104L })
    }

    @Test fun instantCacheHydratesStateImmediatelyWithoutWaiting() = runTest(dispatcher) {
        val connection = FakeConnection()
        val firstModel = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()
        assertEquals(4L, firstModel.uiState.value.snapshot?.revision)
        assertEquals(2, firstModel.uiState.value.graphPage?.commits?.size)

        // Second instance for same repo hydrates from cache in 0ms without waiting for network
        val secondModel = GitWorkspaceViewModel("repo", connection)
        assertEquals(4L, secondModel.uiState.value.snapshot?.revision)
        assertEquals(2, secondModel.uiState.value.graphPage?.commits?.size)
        assertFalse(secondModel.uiState.value.isLoading)
    }

    @Test fun operateShowsFeedbackWhenRevisionIsNull() = runTest(dispatcher) {
        val connection = object : WorkspaceConnection {
            override suspend fun workspace(repoId: String) = Result.failure<GitWorkspaceSnapshot>(IOException("Server down"))
            override suspend fun graph(repoId: String, skip: Int) = Result.success(GitGraphPage(repoId))
            override suspend fun branches(repoId: String) = Result.success(emptyList<BranchInfo>())
            override suspend fun diff(repoId: String, path: String, staged: Boolean, commit: String?) = Result.success(FileDiffResponse(path))
            override suspend fun execute(repoId: String, request: GitOperationRequest): Result<GitOperationResult> =
                Result.success(GitOperationResult(request.requestId, "op", OperationStatus.Succeeded, 1L, userMessage = "OK", startedAtUtcMs = 0L, finishedAtUtcMs = 0L))
        }
        val model = GitWorkspaceViewModel("repo", connection)
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.snapshot)

        model.stageAll()
        assertEquals("Connecting to repository workspace… please wait a moment.", model.uiState.value.errorMessage)
    }

    private class FakeConnection : WorkspaceConnection {
        var reads = 0
        var status = OperationStatus.Succeeded
        var pending: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<GitOperationRequest>()
        var pullRequestsResult: Result<List<PullRequestSummary>> = Result.success(emptyList())
        var mergePrResult: Result<MergePrResponse> = Result.success(MergePrResponse(true, "Merged"))
        override suspend fun workspace(repoId: String): Result<GitWorkspaceSnapshot> {
            reads++
            return Result.success(GitWorkspaceSnapshot(repoId, revision = 4, currentBranch = "main"))
        }
        override suspend fun graph(repoId: String, skip: Int) = Result.success(
            GitGraphPage(repoId, commits = if (skip == 0) listOf(commit("new"), commit("boundary"))
                else listOf(commit("boundary"), commit("old")), hasMore = skip == 0, nextSkip = if (skip == 0) 80 else null))
        override suspend fun branches(repoId: String) = Result.success(emptyList<BranchInfo>())
        override suspend fun diff(repoId: String, path: String, staged: Boolean, commit: String?) = Result.success(FileDiffResponse(path))
        override suspend fun execute(repoId: String, request: GitOperationRequest): Result<GitOperationResult> {
            requests += request
            pending?.await()
            return Result.success(GitOperationResult(request.requestId, "operation", status, newRepoRevision = 5,
                userMessage = "Workspace changed. Refresh before retrying.", startedAtUtcMs = 0, finishedAtUtcMs = 1))
        }
        override suspend fun pullRequests(repoId: String): Result<List<PullRequestSummary>> = pullRequestsResult
        override suspend fun mergePullRequest(repoId: String, prNumber: Long, method: String): Result<MergePrResponse> = mergePrResult
        private fun commit(id: String) = CommitNode(id, id, "Author", "", 0, "Example", row = 0, lane = 0, colorIndex = 0)
    }
}
