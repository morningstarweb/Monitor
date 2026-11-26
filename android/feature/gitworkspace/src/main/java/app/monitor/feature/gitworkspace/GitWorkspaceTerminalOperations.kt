package app.monitor.feature.gitworkspace

import androidx.lifecycle.viewModelScope
import app.monitor.core.model.TerminalCommandResult
import app.monitor.core.model.TerminalLogEntry
import app.monitor.core.model.TerminalSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

private val sessionJobsByVm = Collections.synchronizedMap(
    WeakHashMap<GitWorkspaceViewModel, ConcurrentHashMap<String, Job>>()
)

private fun GitWorkspaceViewModel.sessionJobs(): ConcurrentHashMap<String, Job> =
    sessionJobsByVm.getOrPut(this) { ConcurrentHashMap() }

private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;?]*[A-Za-z]|\u001B\][^\u0007]*\u0007""")
private val ACTIVE_PID_MARKER_REGEX = Regex("""\[Process active · PID \d+]""")
private val PROCESS_EXITED_REGEX = Regex("""\[Process exited with code (-?\d+)]""")

internal fun sanitizeTerminalText(raw: String): String =
    raw.replace(ANSI_ESCAPE_REGEX, "").replace("\r", "")

fun GitWorkspaceViewModel.createTerminalSession() {
    val current = uiState.value.terminalSessions
    val newId = (current.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: 0) + 1
    val newSession = TerminalSession(id = newId.toString(), title = "Term $newId")
    _uiState.update { it.copy(terminalSessions = it.terminalSessions + newSession, activeSessionId = newSession.id) }
}

fun GitWorkspaceViewModel.closeTerminalSession(id: String) {
    val current = uiState.value.terminalSessions
    if (current.size <= 1) return
    sessionJobs().remove(id)?.cancel()
    viewModelScope.launch { connection.stopTerminal(currentRepoId, id) }
    val remaining = current.filterNot { it.id == id }
    val newActiveId = if (uiState.value.activeSessionId == id) remaining.first().id else uiState.value.activeSessionId
    _uiState.update {
        it.copy(
            terminalSessions = remaining,
            activeSessionId = newActiveId,
            isRunningTerminal = remaining.any { s -> s.isRunning }
        )
    }
}

fun GitWorkspaceViewModel.selectTerminalSession(id: String) {
    _uiState.update { it.copy(activeSessionId = id) }
}

fun GitWorkspaceViewModel.clearTerminalLogs() {
    val activeId = uiState.value.activeSessionId
    _uiState.update { state ->
        val updated = state.terminalSessions.map { s ->
            if (s.id == activeId) s.copy(logs = emptyList()) else s
        }
        state.copy(terminalSessions = updated)
    }
}

fun GitWorkspaceViewModel.stopTerminalCommand() {
    val activeId = uiState.value.activeSessionId
    sessionJobs().remove(activeId)?.cancel()
    viewModelScope.launch { connection.stopTerminal(currentRepoId, activeId) }
    _uiState.update { state ->
        val updated = state.terminalSessions.map { s ->
            if (s.id == activeId) {
                val updatedLogs = s.logs.map { l ->
                    if (l.isRunning) {
                        val nextText = (l.streamingText + "\n^C [Terminated]").trim()
                        l.copy(
                            isRunning = false,
                            streamingText = nextText,
                            result = TerminalCommandResult(exitCode = 130, durationMs = System.currentTimeMillis() - l.timestampMs)
                        )
                    } else l
                }
                s.copy(logs = updatedLogs, isRunning = false)
            } else s
        }
        state.copy(terminalSessions = updated, isRunningTerminal = updated.any { it.isRunning })
    }
}

fun GitWorkspaceViewModel.runTerminal(command: String, shell: String? = null) {
    val cmd = command.trim()
    if (cmd.isBlank()) return
    if (cmd.equals("clear", ignoreCase = true) || cmd.equals("cls", ignoreCase = true)) {
        clearTerminalLogs()
        return
    }
    val targetRepoId = currentRepoId.ifBlank {
        uiState.value.repoId.ifBlank { "default" }
    }
    val activeId = uiState.value.activeSessionId
    val entry = TerminalLogEntry(command = cmd, result = null, isRunning = true)
    val entryId = entry.id
    _uiState.update { state ->
        val updated = state.terminalSessions.map { s ->
            if (s.id == activeId) {
                val history = if (s.commandHistory.lastOrNull() == cmd) s.commandHistory else (s.commandHistory + cmd).takeLast(50)
                s.copy(logs = s.logs + entry, isRunning = true, activeCommand = cmd, commandHistory = history)
            } else s
        }
        state.copy(terminalSessions = updated, isRunningTerminal = true)
    }
    sessionJobs()[activeId]?.cancel()
    val job = viewModelScope.launch {
        try {
            connection.runTerminal(targetRepoId, cmd, shell, activeId).fold(
                onSuccess = { res ->
                    val stillRunning = ACTIVE_PID_MARKER_REGEX.containsMatchIn(res.stdout)
                    val cleanStdout = sanitizeTerminalText(res.stdout).trimEnd()
                    val cleanStderr = sanitizeTerminalText(res.stderr).trimEnd()
                    _uiState.update { state ->
                        val updated = state.terminalSessions.map { s ->
                            if (s.id == activeId) {
                                val uLogs = s.logs.map { l ->
                                    if (l.id == entryId) {
                                        val mergedStream = if (l.streamingText.isBlank()) {
                                            listOfNotNull(
                                                cleanStdout.takeIf { it.isNotBlank() },
                                                cleanStderr.takeIf { it.isNotBlank() }
                                            ).joinToString("\n")
                                        } else {
                                            l.streamingText
                                        }
                                        if (stillRunning) {
                                            l.copy(streamingText = mergedStream, isRunning = true, result = null)
                                        } else {
                                            l.copy(
                                                streamingText = mergedStream,
                                                result = res.copy(stdout = cleanStdout, stderr = cleanStderr),
                                                isRunning = false
                                            )
                                        }
                                    } else l
                                }
                                s.copy(logs = uLogs, isRunning = uLogs.any { it.isRunning })
                            } else s
                        }
                        state.copy(terminalSessions = updated, isRunningTerminal = updated.any { it.isRunning })
                    }
                },
                onFailure = { err ->
                    val errRes = TerminalCommandResult(exitCode = -1, stderr = err.message ?: "Execution failed")
                    _uiState.update { state ->
                        val updated = state.terminalSessions.map { s ->
                            if (s.id == activeId) {
                                val uLogs = s.logs.map { l ->
                                    if (l.id == entryId && l.isRunning) l.copy(result = errRes, isRunning = false) else l
                                }
                                s.copy(logs = uLogs, isRunning = uLogs.any { it.isRunning })
                            } else s
                        }
                        state.copy(terminalSessions = updated, isRunningTerminal = updated.any { it.isRunning })
                    }
                }
            )
        } finally {
            _uiState.update { it.copy(isRunningTerminal = it.terminalSessions.any { s -> s.isRunning }) }
        }
    }
    sessionJobs()[activeId] = job
}

fun GitWorkspaceViewModel.handleTerminalOutput(repoId: String, sessionId: String, text: String) {
    val matchesRepo = repoId.isBlank() ||
        currentRepoId.isBlank() ||
        repoId == currentRepoId ||
        repoId == uiState.value.repoId ||
        repoId == "default"
    if (!matchesRepo) return

    val cleanLine = sanitizeTerminalText(text)
    if (cleanLine.isBlank()) return

    val exitMatch = PROCESS_EXITED_REGEX.find(cleanLine)
    val terminatedByUser = cleanLine.contains("[Process terminated by user]")

    _uiState.update { state ->
        val updated = state.terminalSessions.map { s ->
            if (s.id == sessionId) {
                val lastLog = s.logs.lastOrNull()
                if (lastLog != null && lastLog.isRunning) {
                    val updatedText = if (lastLog.streamingText.isEmpty()) cleanLine else "${lastLog.streamingText}\n$cleanLine"
                    val updatedLog = when {
                        exitMatch != null -> {
                            val code = exitMatch.groupValues[1].toIntOrNull() ?: 0
                            lastLog.copy(
                                streamingText = updatedText,
                                isRunning = false,
                                result = TerminalCommandResult(
                                    exitCode = code,
                                    durationMs = System.currentTimeMillis() - lastLog.timestampMs
                                )
                            )
                        }
                        terminatedByUser -> {
                            lastLog.copy(
                                streamingText = updatedText,
                                isRunning = false,
                                result = TerminalCommandResult(
                                    exitCode = 130,
                                    durationMs = System.currentTimeMillis() - lastLog.timestampMs
                                )
                            )
                        }
                        else -> lastLog.copy(streamingText = updatedText)
                    }
                    s.copy(logs = s.logs.dropLast(1) + updatedLog, isRunning = updatedLog.isRunning)
                } else s
            } else s
        }
        state.copy(terminalSessions = updated, isRunningTerminal = updated.any { it.isRunning })
    }
}
