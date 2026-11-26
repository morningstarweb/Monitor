package app.monitor.feature.gitworkspace

import app.monitor.core.model.GitOperation
import kotlinx.coroutines.flow.update

fun GitWorkspaceViewModel.stageAll() = operate(GitOperation.StageAll)
fun GitWorkspaceViewModel.stageFiles(paths: List<String>) = if (paths.isNotEmpty()) operate(GitOperation.StageFiles(paths)) else Unit
fun GitWorkspaceViewModel.unstageFiles(paths: List<String>) = if (paths.isNotEmpty()) operate(GitOperation.UnstageFiles(paths)) else Unit
fun GitWorkspaceViewModel.unstageAll() = operate(GitOperation.UnstageAll)
fun GitWorkspaceViewModel.discardFile(path: String) = _uiState.value.snapshot?.revision?.let { r -> operate(GitOperation.DiscardFile(path, "discard:$path:$r")) } ?: Unit
fun GitWorkspaceViewModel.commit(summary: String, body: String? = null, paths: List<String> = emptyList()) = if (summary.isNotBlank()) operate(GitOperation.Commit(summary.trim(), body, stageAllTracked = paths.isEmpty(), paths = paths)) else Unit
fun GitWorkspaceViewModel.pull(remote: String? = null, branch: String? = null) = operate(GitOperation.Pull(remote, branch))
fun GitWorkspaceViewModel.push(remote: String? = null, branch: String? = null, setUpstream: Boolean = false) = operate(GitOperation.Push(remote, branch, setUpstream))
fun GitWorkspaceViewModel.fetch(remote: String? = null) = operate(GitOperation.Fetch(remote))
fun GitWorkspaceViewModel.createBranch(name: String) = if (name.isNotBlank()) operate(GitOperation.CreateBranch(app.monitor.feature.gitworkspace.dialogs.sanitizeBranchName(name))) else Unit
fun GitWorkspaceViewModel.switchBranch(name: String) = if (name.isNotBlank()) operate(GitOperation.SwitchBranch(name.trim())) else Unit
fun GitWorkspaceViewModel.deleteBranch(name: String, force: Boolean = false) {
    val t = name.trim()
    if (t.equals("main", true) || t.equals("master", true)) { _uiState.update { it.copy(errorMessage = "Protected branch: '$t'") }; return }
    if (t.isNotBlank()) operate(GitOperation.DeleteBranch(t, force))
}
fun GitWorkspaceViewModel.mergeBranch(source: String) = if (source.isNotBlank()) operate(GitOperation.MergeBranch(source.trim())) else Unit
fun GitWorkspaceViewModel.mergeAllBranches(branches: List<String> = emptyList()) =
    operate(GitOperation.MergeAllBranches(branches.map { it.trim() }.filter { it.isNotBlank() }))
