package app.monitor.feature.gitworkspace.model

import app.monitor.core.model.BranchInfo
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitFileChange
import app.monitor.core.model.GitGraphPage
import app.monitor.core.model.GitWorkspaceSnapshot
import app.monitor.core.model.PullRequestSummary
import app.monitor.core.model.TerminalLogEntry
import app.monitor.core.model.TerminalSession

data class GitWorkspaceUiState(
    val repoId: String,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val snapshot: GitWorkspaceSnapshot? = null,
    val graphPage: GitGraphPage? = null,
    val branches: List<BranchInfo> = emptyList(),
    val pullRequests: List<PullRequestSummary> = emptyList(),
    val selectedDiff: FileDiffResponse? = null,
    val selectedFileForDiff: String? = null,
    val commitFilesMap: Map<String, List<GitFileChange>> = emptyMap(),
    val isCommitting: Boolean = false,
    val isOperating: Boolean = false,
    val gitIdentity: app.monitor.core.model.GitIdentity? = null,
    val showIdentityDialog: Boolean = false,
    val showTerminal: Boolean = false,
    val terminalSessions: List<TerminalSession> = listOf(TerminalSession(id = "1", title = "Term 1")),
    val activeSessionId: String = "1",
    val isRunningTerminal: Boolean = false,
    val errorMessage: String? = null,
    val userMessage: String? = null
) {
    val activeSession: TerminalSession
        get() = terminalSessions.find { it.id == activeSessionId } ?: terminalSessions.firstOrNull() ?: TerminalSession("1", "Term 1")
    val terminalLogs: List<TerminalLogEntry>
        get() = activeSession.logs
}
