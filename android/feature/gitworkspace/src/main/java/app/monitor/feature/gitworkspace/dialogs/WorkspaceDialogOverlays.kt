package app.monitor.feature.gitworkspace.dialogs

import androidx.compose.runtime.Composable
import app.monitor.core.model.GitFileChange
import app.monitor.feature.gitworkspace.model.GitWorkspaceUiState

@Composable
fun WorkspaceDialogOverlays(
    uiState: GitWorkspaceUiState,
    repoDisplayName: String,
    repoPath: String,
    inspectedFile: GitFileChange?,
    onDismissInspectedFile: () -> Unit,
    onSetGitIdentity: (String, String) -> Unit,
    onDismissIdentityDialog: () -> Unit,
    onSelectTerminalSession: (String) -> Unit,
    onCloseTerminalSession: (String) -> Unit,
    onNewTerminalSession: () -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    onStopTerminalCommand: () -> Unit,
    onClearTerminalLogs: () -> Unit,
    onToggleTerminal: (Boolean) -> Unit
) {
    inspectedFile?.let { file ->
        FileDiffInspectorBottomSheet(
            file = file,
            diff = uiState.selectedDiff?.takeIf { it.path == file.path },
            onDismiss = onDismissInspectedFile
        )
    }

    if (uiState.showIdentityDialog) {
        GitAuthorIdentityDialog(
            initialName = uiState.gitIdentity?.name.orEmpty(),
            initialEmail = uiState.gitIdentity?.email.orEmpty(),
            isSaving = uiState.isOperating,
            onSave = onSetGitIdentity,
            onDismiss = onDismissIdentityDialog
        )
    }

    if (uiState.showTerminal) {
        ProjectTerminalBottomSheet(
            repoDisplayName = repoDisplayName,
            repoPath = repoPath,
            sessions = uiState.terminalSessions,
            activeSessionId = uiState.activeSessionId,
            onSelectSession = onSelectTerminalSession,
            onCloseSession = onCloseTerminalSession,
            onNewSession = onNewTerminalSession,
            onRunCommand = onRunTerminalCommand,
            onStopCommand = onStopTerminalCommand,
            onClearLogs = onClearTerminalLogs,
            onDismiss = { onToggleTerminal(false) }
        )
    }
}
