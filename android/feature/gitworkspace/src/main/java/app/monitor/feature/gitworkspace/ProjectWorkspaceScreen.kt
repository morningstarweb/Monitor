package app.monitor.feature.gitworkspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.RepoSummary
import app.monitor.feature.gitworkspace.components.ActiveRepoHeroCard
import app.monitor.feature.gitworkspace.components.CleanWorkingTreeCard
import app.monitor.feature.gitworkspace.components.CommitChangesCard
import app.monitor.feature.gitworkspace.components.FileItemRow
import app.monitor.feature.gitworkspace.components.FoldedFilesCard
import app.monitor.feature.gitworkspace.components.GitFeedbackPill
import app.monitor.feature.gitworkspace.components.LiveGitVelocityCard
import app.monitor.feature.gitworkspace.components.WorkspaceBackgroundDecorations
import app.monitor.feature.gitworkspace.dialogs.FileDiffInspectorBottomSheet
import app.monitor.feature.gitworkspace.dialogs.ProjectSelectorBottomSheet
import app.monitor.feature.gitworkspace.dialogs.SwitchBranchDialog
import app.monitor.feature.gitworkspace.model.GitWorkspaceUiState
import app.monitor.feature.gitworkspace.sections.ChangedFilesSectionHeader
import app.monitor.feature.gitworkspace.sections.WorkspaceHeaderSection
import app.monitor.feature.gitworkspace.sections.WorkspaceMetricRow

/**
 * Focused Working Tree & Workspace Screen (< 170 lines).
 * 1:1 visual cohesion with HomeScreen: header, hero card, 3-stat row, live velocity HUD, and clean files list.
 */
@Composable
fun ProjectWorkspaceScreen(
    repoDisplayName: String, uiState: GitWorkspaceUiState, allProjects: List<RepoSummary> = emptyList(),
    onSelectProject: (String) -> Unit = {}, onBackClick: () -> Unit = {},
    onFileClick: (String, Boolean) -> Unit = { _, _ -> }, onCommitClick: (String, List<String>) -> Unit = { _, _ -> },
    onCreateBranch: (String) -> Unit = {}, onSwitchBranch: (String) -> Unit = {}, onDeleteBranch: (String) -> Unit = {},
    onMergeBranch: (String) -> Unit = {}, onLoadMoreCommits: (Int) -> Unit = {}, onPullClick: () -> Unit = {},
    onPushClick: () -> Unit = {}, onFetchClick: () -> Unit = {}, onToggleTheme: () -> Unit = {}, onClearFeedback: () -> Unit = {},
    onSetGitIdentity: (String, String) -> Unit = { _, _ -> }, onDismissIdentityDialog: () -> Unit = {}, onOpenIdentityDialog: () -> Unit = {},
    onRunTerminalCommand: (String) -> Unit = {}, onStopTerminalCommand: () -> Unit = {}, onClearTerminalLogs: () -> Unit = {},
    onSelectTerminalSession: (String) -> Unit = {}, onCloseTerminalSession: (String) -> Unit = {}, onNewTerminalSession: () -> Unit = {},
    onToggleTerminal: (Boolean) -> Unit = {}
) {
    var commitMessage by remember { mutableStateOf("") }
    var showProjectSelector by remember { mutableStateOf(false) }
    var branchToSwitch by remember { mutableStateOf<String?>(null) }
    var inspectedFile by remember { mutableStateOf<app.monitor.core.model.GitFileChange?>(null) }
    var isFolded by remember { mutableStateOf(false) }

    val snapshot = uiState.snapshot
    val files = snapshot?.files.orEmpty()
    var selectedPaths by remember(files) { mutableStateOf(files.map { it.path }.toSet()) }
    val isAllSelected = files.isNotEmpty() && selectedPaths.size == files.size
    val projectSummary = allProjects.find { it.repoId == uiState.repoId }
    val repoPath = (projectSummary?.path ?: "D:/Monitor/$repoDisplayName").removePrefix("\\\\?\\")
    val isDark = LocalMonitorColors.current.isDark

    if (showProjectSelector) ProjectSelectorBottomSheet(allProjects, repoDisplayName, uiState.repoId, onSelectProject) { showProjectSelector = false }
    branchToSwitch?.let { name -> SwitchBranchDialog(name, uiState.isOperating, { onSwitchBranch(name); branchToSwitch = null }) { branchToSwitch = null } }

    Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF07080E) else Color.Transparent)) {
        WorkspaceBackgroundDecorations()

        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 144.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Redesigned Minimalist Header (Aligned 1:1 with HomeHeaderSection)
            item {
                WorkspaceHeaderSection(
                    onSearchClick = { showProjectSelector = true },
                    onProjectsMenuClick = { showProjectSelector = true },
                    onAuthorProfileClick = onOpenIdentityDialog
                )
            }

            // 2. Active Repository Hero Station Card (Mirrors ConnectedDeviceCard)
            item {
                ActiveRepoHeroCard(
                    repoDisplayName = repoDisplayName,
                    repoPath = repoPath,
                    snapshot = snapshot,
                    onSwitchRepoClick = { showProjectSelector = true }
                )
            }

            // 2b. Remote Project Terminal Bar (Antigravity/VS Code Station)
            item {
                app.monitor.feature.gitworkspace.sections.WorkspaceTerminalQuickCard(
                    activeSessionsCount = uiState.terminalSessions.size,
                    isRunning = uiState.isRunningTerminal,
                    onOpenTerminal = { onToggleTerminal(true) }
                )
            }

            // 3. 3-Stat Git Metric Row (Mirrors ThreeStatMetricRow)
            item {
                WorkspaceMetricRow(snapshot = snapshot)
            }

            // 4. Live Git Velocity Graph Card (Mirrors LiveCpuGraphCard)
            item {
                LiveGitVelocityCard(snapshot = snapshot)
            }

            // 5. Commit Changes Card (Placed right above files list so user never has to scroll down 128 items!)
            item {
                CommitChangesCard(
                    commitMessage = commitMessage,
                    onCommitMessageChange = { commitMessage = it },
                    selectedCount = selectedPaths.size,
                    isCommitting = uiState.isCommitting,
                    onCommit = {
                        val msg = commitMessage.trim()
                        if (msg.isNotEmpty()) {
                            onCommitClick(msg, selectedPaths.toList())
                            commitMessage = ""
                        }
                    }
                )
            }

            // 6. Spacious Changed Files Section Header with Folding / Collapse Toggle
            item {
                ChangedFilesSectionHeader(
                    filesCount = files.size,
                    isAllSelected = isAllSelected,
                    onToggleSelectAll = {
                        selectedPaths = if (isAllSelected) emptySet() else files.map { it.path }.toSet()
                    },
                    isFolded = isFolded,
                    onToggleFold = { isFolded = !isFolded }
                )
            }

            // 7. Changed Files List with Instant Folding
            if (uiState.isLoading && uiState.snapshot == null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp),
                            color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                        )
                    }
                }
            } else if (files.isEmpty()) {
                item {
                    CleanWorkingTreeCard(isDark = isDark)
                }
            } else if (isFolded) {
                item {
                    FoldedFilesCard(filesCount = files.size, isDark = isDark, onExpand = { isFolded = false })
                }
            } else {
                items(files, key = { it.path }) { file ->
                    val isChecked = selectedPaths.contains(file.path)
                    FileItemRow(
                        file = file,
                        isChecked = isChecked,
                        onToggleCheck = {
                            selectedPaths = if (isChecked) selectedPaths - file.path else selectedPaths + file.path
                        },
                        onClick = {
                            inspectedFile = file
                            onFileClick(file.path, file.staged)
                        }
                    )
                }
            }
        }

        // Dialog overlays (File Diff, Git Identity, Multi-Terminal Console)
        app.monitor.feature.gitworkspace.dialogs.WorkspaceDialogOverlays(
            uiState = uiState, repoDisplayName = repoDisplayName, repoPath = repoPath,
            inspectedFile = inspectedFile, onDismissInspectedFile = { inspectedFile = null },
            onSetGitIdentity = onSetGitIdentity, onDismissIdentityDialog = onDismissIdentityDialog,
            onSelectTerminalSession = onSelectTerminalSession, onCloseTerminalSession = onCloseTerminalSession,
            onNewTerminalSession = onNewTerminalSession, onRunTerminalCommand = onRunTerminalCommand,
            onStopTerminalCommand = onStopTerminalCommand, onClearTerminalLogs = onClearTerminalLogs,
            onToggleTerminal = onToggleTerminal
        )

        GitFeedbackPill(
            message = uiState.errorMessage ?: uiState.userMessage,
            onDismiss = onClearFeedback,
            isErrorHint = uiState.errorMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )
    }
}
