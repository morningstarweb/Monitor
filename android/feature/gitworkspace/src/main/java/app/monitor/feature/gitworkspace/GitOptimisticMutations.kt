package app.monitor.feature.gitworkspace

import app.monitor.core.model.BranchInfo
import app.monitor.core.model.CommitNode
import app.monitor.core.model.GitOperation
import app.monitor.feature.gitworkspace.model.GitWorkspaceUiState
import java.util.UUID

/**
 * Enterprise Optimistic UI Mutator for Instant Zero-Latency Interactions (< 120 lines).
 * Immediately reflects Git mutations in memory before remote sync round-trips.
 */
object GitOptimisticMutations {
    fun applyOptimistic(state: GitWorkspaceUiState, operation: GitOperation): GitWorkspaceUiState {
        val snapshot = state.snapshot ?: return state
        return when (operation) {
            is GitOperation.Commit -> {
                val committed = if (operation.paths.isNotEmpty()) operation.paths.toSet()
                                else snapshot.files.map { it.path }.toSet()
                val remaining = snapshot.files.filterNot { it.path in committed }
                val optSnapshot = snapshot.copy(aheadCount = snapshot.aheadCount + 1, files = remaining)
                val optCommit = CommitNode(
                    oid = "optimistic-${UUID.randomUUID().toString().take(7)}",
                    shortOid = "syncing",
                    authorName = state.gitIdentity?.name?.ifBlank { "You" } ?: "You",
                    authorEmail = state.gitIdentity?.email ?: "",
                    timestampUtcMs = System.currentTimeMillis(),
                    summary = operation.messageSummary,
                    parentOids = state.graphPage?.commits?.firstOrNull()?.let { listOf(it.oid) } ?: emptyList(),
                    row = 0,
                    lane = 0,
                    colorIndex = 0,
                    edges = emptyList<app.monitor.core.model.GraphEdge>()
                )
                val updatedCommits = listOf(optCommit) + state.graphPage?.commits.orEmpty()
                state.copy(
                    snapshot = optSnapshot,
                    graphPage = state.graphPage?.copy(commits = updatedCommits),
                    userMessage = "✓ Commit created • Syncing...",
                    isCommitting = false
                )
            }
            is GitOperation.SwitchBranch -> {
                val optSnapshot = snapshot.copy(currentBranch = operation.branchName)
                val optBranches = state.branches.map { it.copy(isCurrent = it.name == operation.branchName) }
                state.copy(snapshot = optSnapshot, branches = optBranches, userMessage = "✓ Switched to '${operation.branchName}' • Syncing...")
            }
            is GitOperation.CreateBranch -> {
                val newB = BranchInfo(name = operation.branchName, isCurrent = false, isRemote = false)
                state.copy(branches = state.branches + newB, userMessage = "✓ Branch '${operation.branchName}' created • Syncing...")
            }
            is GitOperation.DeleteBranch -> {
                state.copy(branches = state.branches.filterNot { it.name == operation.branchName }, userMessage = "✓ Branch '${operation.branchName}' deleted • Syncing...")
            }
            is GitOperation.StageFiles -> {
                val set = operation.paths.toSet()
                state.copy(snapshot = snapshot.copy(files = snapshot.files.map { if (it.path in set) it.copy(staged = true) else it }))
            }
            is GitOperation.UnstageFiles -> {
                val set = operation.paths.toSet()
                state.copy(snapshot = snapshot.copy(files = snapshot.files.map { if (it.path in set) it.copy(staged = false) else it }))
            }
            is GitOperation.StageAll -> {
                state.copy(snapshot = snapshot.copy(files = snapshot.files.map { it.copy(staged = true) }))
            }
            is GitOperation.UnstageAll -> {
                state.copy(snapshot = snapshot.copy(files = snapshot.files.map { it.copy(staged = false) }))
            }
            is GitOperation.MergeBranch -> {
                val optBranches = state.branches.map {
                    if (it.name == operation.sourceBranch) it.copy(isMerged = true, unmergedCommits = 0) else it
                }
                state.copy(branches = optBranches, userMessage = "✓ Merged '${operation.sourceBranch}' • Syncing...")
            }
            is GitOperation.MergeAllBranches -> {
                val targets = operation.sourceBranches.toSet()
                val optBranches = state.branches.map {
                    if (!it.isCurrent && !it.isRemote && (targets.isEmpty() || it.name in targets)) {
                        it.copy(isMerged = true, unmergedCommits = 0)
                    } else {
                        it
                    }
                }
                state.copy(branches = optBranches, userMessage = "✓ Merged all branches • Syncing...")
            }
            else -> state
        }
    }
}
