package app.monitor.feature.gitworkspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.monitor.core.model.GitOperation
import app.monitor.core.model.GitOperationRequest
import app.monitor.core.model.OperationStatus
import app.monitor.core.network.MonitorApiClient
import app.monitor.feature.gitworkspace.connection.LiveWorkspaceConnection
import app.monitor.feature.gitworkspace.connection.WorkspaceConnection
import app.monitor.feature.gitworkspace.model.GitWorkspaceUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class GitWorkspaceViewModel(repoId: String, internal val connection: WorkspaceConnection) : ViewModel() {
    constructor(repoId: String, apiClient: MonitorApiClient) : this(repoId, LiveWorkspaceConnection(apiClient))

    companion object {
        internal val repoWorkspaceCache = java.util.concurrent.ConcurrentHashMap<String, app.monitor.core.model.GitWorkspaceSnapshot>()
        internal val repoGraphCache = java.util.concurrent.ConcurrentHashMap<String, app.monitor.core.model.GitGraphPage>()
        internal val repoBranchesCache = java.util.concurrent.ConcurrentHashMap<String, List<app.monitor.core.model.BranchInfo>>()

        fun clearCache() {
            repoWorkspaceCache.clear()
            repoGraphCache.clear()
            repoBranchesCache.clear()
        }
    }

    internal var currentRepoId: String = repoId
    internal val _uiState = MutableStateFlow(createInitialState(repoId))
    val uiState = _uiState.asStateFlow()
    private var graphJob: Job? = null
    private var diffJob: Job? = null

    init {
        if (currentRepoId.isNotBlank()) refreshAll()
    }

    private fun createInitialState(repo: String): GitWorkspaceUiState {
        val cachedSnap = repoWorkspaceCache[repo]
        val cachedGraph = repoGraphCache[repo]
        val cachedBranches = repoBranchesCache[repo].orEmpty()
        val hasCached = cachedSnap != null || cachedGraph != null
        return GitWorkspaceUiState(
            repoId = repo,
            isLoading = repo.isNotBlank() && !hasCached,
            snapshot = cachedSnap,
            graphPage = cachedGraph,
            branches = cachedBranches
        )
    }

    fun selectProject(newRepoId: String) {
        if (newRepoId.isBlank()) return
        currentRepoId = newRepoId
        graphJob?.cancel()
        diffJob?.cancel()
        val cachedSnap = repoWorkspaceCache[newRepoId]
        val cachedGraph = repoGraphCache[newRepoId]
        val cachedBranches = repoBranchesCache[newRepoId].orEmpty()
        val hasCached = cachedSnap != null || cachedGraph != null
        _uiState.update {
            it.copy(
                repoId = newRepoId,
                isLoading = !hasCached,
                snapshot = cachedSnap,
                graphPage = cachedGraph,
                branches = cachedBranches,
                selectedDiff = null,
                selectedFileForDiff = null,
                errorMessage = null
            )
        }
        refreshAll()
    }

    fun refreshAll() { loadWorkspace(); loadGraph(); loadBranches(); loadPullRequests(); loadGitIdentity() }

    fun loadWorkspace() {
        if (currentRepoId.isBlank()) return
        viewModelScope.launch { refreshWorkspace() }
    }

    fun updateWorkspaceSnapshot(repoId: String, snapshot: app.monitor.core.model.GitWorkspaceSnapshot) {
        if (repoId.isNotBlank() && currentRepoId.isNotBlank() && repoId != currentRepoId) return
        if (currentRepoId.isBlank() && repoId.isNotBlank()) {
            currentRepoId = repoId
        }
        if (repoId.isNotBlank()) {
            repoWorkspaceCache[repoId] = snapshot
        }
        val currentPath = _uiState.value.selectedFileForDiff
        val selected = snapshot.files.firstOrNull { it.path == currentPath } ?: snapshot.files.firstOrNull()
        val fileChanged = selected?.path != currentPath
        _uiState.update {
            it.copy(
                repoId = repoId.ifBlank { it.repoId },
                isLoading = false,
                snapshot = snapshot,
                selectedDiff = if (fileChanged) null else it.selectedDiff,
                selectedFileForDiff = selected?.path,
                errorMessage = null
            )
        }
        selected?.let { loadFileDiff(it.path, it.staged) }
    }

    private suspend fun refreshWorkspace() {
        if (_uiState.value.snapshot == null) {
            _uiState.update { it.copy(isLoading = true) }
        }
        connection.workspace(currentRepoId).fold(
            onSuccess = { snapshot ->
                repoWorkspaceCache[currentRepoId] = snapshot
                val currentPath = _uiState.value.selectedFileForDiff
                val selected = snapshot.files.firstOrNull { it.path == currentPath } ?: snapshot.files.firstOrNull()
                val keepExistingDiff = selected != null && selected.path == currentPath && _uiState.value.selectedDiff != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snapshot = snapshot,
                        selectedDiff = if (keepExistingDiff) it.selectedDiff else null,
                        selectedFileForDiff = selected?.path
                    )
                }
                if (!keepExistingDiff) {
                    selected?.let { loadFileDiff(it.path, it.staged) }
                }
            },
            onFailure = { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Cannot load this workspace.") }
            }
        )
    }

    fun loadGraph(skip: Int = 0) {
        if (currentRepoId.isBlank()) return
        if (skip > 0 && graphJob?.isActive == true) return
        graphJob?.cancel()
        _uiState.update { it.copy(isLoadingMore = skip > 0) }
        graphJob = viewModelScope.launch {
            connection.graph(currentRepoId, skip).fold(
                onSuccess = { page ->
                    _uiState.update { state ->
                        val commits = if (skip > 0) (state.graphPage?.commits.orEmpty() + page.commits).distinctBy { it.oid }
                        else page.commits
                        val updatedPage = page.copy(commits = commits)
                        repoGraphCache[currentRepoId] = updatedPage
                        state.copy(graphPage = updatedPage, isLoadingMore = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoadingMore = false, errorMessage = error.message ?: "Cannot load commit history.") }
                }
            )
        }
    }

    fun loadBranches() {
        if (currentRepoId.isBlank()) return
        viewModelScope.launch {
            connection.branches(currentRepoId).fold(
                onSuccess = { b ->
                    repoBranchesCache[currentRepoId] = b
                    _uiState.update { it.copy(branches = b) }
                },
                onFailure = { e -> _uiState.update { it.copy(errorMessage = e.message ?: "Cannot load branches.") } }
            )
        }
    }

    fun loadPullRequests() {
        if (currentRepoId.isBlank()) return
        viewModelScope.launch {
            connection.pullRequests(currentRepoId).fold(
                onSuccess = { prs -> _uiState.update { it.copy(pullRequests = prs) } },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message ?: "Failed to load pull requests.") } }
            )
        }
    }

    fun mergePullRequest(prNumber: Long, method: String = "merge") {
        if (currentRepoId.isBlank() || prNumber <= 0) return
        _uiState.update { it.copy(isOperating = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                connection.mergePullRequest(currentRepoId, prNumber, method).fold(
                    onSuccess = { res ->
                        _uiState.update { if (!res.success) it.copy(errorMessage = res.message) else it.copy(userMessage = "PR #$prNumber merged!") }
                        refreshAll()
                    },
                    onFailure = { e -> _uiState.update { it.copy(errorMessage = e.message ?: "Failed to merge PR.") } }
                )
            } finally { _uiState.update { it.copy(isOperating = false) } }
        }
    }

    fun loadFileDiff(filePath: String, isStaged: Boolean, commit: String? = null) {
        diffJob?.cancel()
        _uiState.update { it.copy(selectedFileForDiff = filePath, selectedDiff = null) }
        diffJob = viewModelScope.launch {
            connection.diff(currentRepoId, filePath, isStaged, commit).fold(
                onSuccess = { diff -> _uiState.update { it.copy(selectedDiff = diff) } },
                onFailure = { error -> _uiState.update { it.copy(errorMessage = error.message ?: "Cannot load this diff.") } }
            )
        }
    }

    fun loadCommitFiles(commitOid: String) {
        if (_uiState.value.commitFilesMap.containsKey(commitOid)) return
        viewModelScope.launch {
            connection.commitFiles(currentRepoId, commitOid).fold(
                onSuccess = { files ->
                    _uiState.update { state ->
                        state.copy(commitFilesMap = state.commitFilesMap + (commitOid to files))
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = "Could not load commit files: ${error.message}") }
                }
            )
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    fun toggleTerminal(show: Boolean) = _uiState.update { it.copy(showTerminal = show) }

    internal fun operate(operation: GitOperation) {
        if (currentRepoId.isBlank()) return
        val prevState = _uiState.value
        val revision = prevState.snapshot?.revision
        if (revision == null) {
            _uiState.update { it.copy(errorMessage = "Connecting to repository workspace… please wait a moment.") }
            refreshAll()
            return
        }
        if (prevState.isOperating || prevState.isCommitting || prevState.isLoading) return

        // 0ms Zero-Latency Optimistic UI Mutation
        val optimisticState = GitOptimisticMutations.applyOptimistic(prevState, operation)
        _uiState.value = optimisticState.copy(isOperating = true, isCommitting = operation is GitOperation.Commit, errorMessage = null)

        viewModelScope.launch {
            try {
                val req = GitOperationRequest(UUID.randomUUID().toString(), revision, operation)
                connection.execute(currentRepoId, req).fold(
                    onSuccess = { res ->
                        if (res.status == OperationStatus.Succeeded) {
                            val msg = when (operation) {
                                is GitOperation.CreateBranch -> "Branch '${operation.branchName}' created!"
                                is GitOperation.SwitchBranch -> "Switched to '${operation.branchName}'!"
                                is GitOperation.DeleteBranch -> "Branch '${operation.branchName}' deleted!"
                                is GitOperation.MergeBranch -> "Merged '${operation.sourceBranch}'!"
                                is GitOperation.MergeAllBranches -> if (res.userMessage.isNotBlank()) res.userMessage else "Merged all branches!"
                                is GitOperation.Commit -> "Committed: ${operation.messageSummary}"
                                else -> res.userMessage
                            }
                            _uiState.update {
                                it.copy(
                                    errorMessage = null,
                                    userMessage = msg,
                                    showIdentityDialog = false,
                                    snapshot = it.snapshot?.copy(revision = res.newRepoRevision)
                                )
                            }
                            refreshAll()
                        } else {
                            val isIdentErr = res.userMessage.contains("identity", true) || res.userMessage.contains("author", true)
                            _uiState.update {
                                prevState.copy(
                                    errorMessage = res.userMessage.ifBlank { "Git operation failed." },
                                    userMessage = null,
                                    showIdentityDialog = isIdentErr
                                )
                            }
                        }
                    },
                    onFailure = { e ->
                        // Graceful Rollback on Remote Failure
                        val isIdentErr = (e.message ?: "").contains("identity", true) || (e.message ?: "").contains("author", true)
                        _uiState.update { prevState.copy(errorMessage = e.message ?: "Operation failed.", showIdentityDialog = isIdentErr) }
                    }
                )
            } finally { _uiState.update { it.copy(isOperating = false, isCommitting = false) } }
        }
    }
}
