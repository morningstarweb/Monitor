package app.monitor.feature.gitworkspace.dialogs

import androidx.compose.runtime.Composable
import app.monitor.core.model.CommitNode
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitFileChange
import app.monitor.core.model.PullRequestSummary
import app.monitor.core.model.RepoSummary

/**
 * Git Graph Dialog Host (< 120 lines).
 */
@Composable
fun GitGraphDialogHost(
    showProjectSelector: Boolean,
    onDismissProjectSelector: () -> Unit,
    allProjects: List<RepoSummary>,
    currentRepoDisplayName: String,
    currentRepoId: String,
    onSelectProject: (String) -> Unit,
    showCreateBranchDialog: Boolean,
    onDismissCreateBranchDialog: () -> Unit,
    isOperating: Boolean,
    onCreateBranch: (String) -> Unit,
    branchToSwitch: String?,
    onDismissSwitchBranchDialog: () -> Unit,
    onConfirmSwitchBranch: (String) -> Unit,
    branchToDelete: String? = null,
    onDismissDeleteBranchDialog: () -> Unit = {},
    onConfirmDeleteBranch: (String) -> Unit = {},
    selectedCommitForDetails: CommitNode?,
    selectedDiff: FileDiffResponse? = null,
    onFileClick: ((GitFileChange, String) -> Unit)? = null,
    selectedPrForDetails: PullRequestSummary? = null,
    onDismissPrDetails: () -> Unit = {},
    onMergePr: ((PullRequestSummary) -> Unit)? = null,
    workspaceFiles: List<GitFileChange> = emptyList(),
    onViewFullDiff: ((String) -> Unit)? = null,
    onDismissCommitDetails: () -> Unit,
    showCompareSheet: Boolean,
    onDismissCompareSheet: () -> Unit,
    currentBranch: String,
    aheadCount: Int,
    behindCount: Int
) {
    if (showProjectSelector) {
        ProjectSelectorBottomSheet(
            allProjects = allProjects,
            currentRepoDisplayName = currentRepoDisplayName,
            currentRepoId = currentRepoId,
            onSelectProject = onSelectProject,
            onDismiss = onDismissProjectSelector
        )
    }

    if (showCreateBranchDialog) {
        CreateBranchDialog(
            isOperating = isOperating,
            onCreateBranch = onCreateBranch,
            onDismiss = onDismissCreateBranchDialog
        )
    }

    branchToSwitch?.let { target ->
        SwitchBranchDialog(
            branchName = target,
            isOperating = isOperating,
            onConfirm = { onConfirmSwitchBranch(target) },
            onDismiss = onDismissSwitchBranchDialog
        )
    }

    branchToDelete?.let { target ->
        DeleteBranchDialog(
            branchName = target,
            isOperating = isOperating,
            onConfirm = { onConfirmDeleteBranch(target) },
            onDismiss = onDismissDeleteBranchDialog
        )
    }

    selectedCommitForDetails?.let { commit ->
        CommitDetailsBottomSheet(
            commit = commit,
            workspaceFiles = workspaceFiles,
            selectedDiff = selectedDiff,
            onFileClick = onFileClick,
            onViewFullDiff = onViewFullDiff,
            onDismiss = onDismissCommitDetails
        )
    }

    selectedPrForDetails?.let { pr ->
        PullRequestDetailsBottomSheet(
            pr = pr,
            workspaceFiles = workspaceFiles,
            onMergePr = {
                onMergePr?.invoke(pr)
            },
            onViewFullDiff = onViewFullDiff,
            onDismiss = onDismissPrDetails
        )
    }

    if (showCompareSheet) {
        BranchCompareBottomSheet(
            currentBranch = currentBranch,
            aheadCount = aheadCount,
            behindCount = behindCount,
            onDismiss = onDismissCompareSheet
        )
    }
}
