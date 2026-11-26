package app.monitor.feature.dashboard

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

fun DashboardViewModel.toggleCheckpointsSheet(show: Boolean, repoPath: String? = null) {
    updateUiState {
        it.copy(
            showCheckpointsSheet = show,
            selectedRepoForCheckpoints = repoPath ?: it.selectedRepoForCheckpoints,
            errorMessage = if (show) null else it.errorMessage
        )
    }
    if (show) loadCheckpoints(repoPath.orEmpty())
}

fun DashboardViewModel.loadCheckpoints(repoPath: String) {
    viewModelScope.launch {
        val res = apiClient.getCheckpoints(repoPath)
        if (res.isSuccess) updateUiState { it.copy(checkpoints = res.getOrThrow()) }
    }
}

fun DashboardViewModel.createManualCheckpoint(repoPath: String) {
    viewModelScope.launch {
        val target = repoPath.ifBlank { uiState.value.projects.firstOrNull()?.path.orEmpty() }
        val res = apiClient.createCheckpoint(target, "Manual Phone Snapshot")
        if (res.isSuccess) {
            updateUiState { it.copy(errorMessage = null) }
            loadCheckpoints(target)
        } else {
            val msg = res.exceptionOrNull()?.message ?: "Unable to create checkpoint"
            updateUiState {
                it.copy(
                    errorMessage = if (msg.contains("403") || msg.contains("forbidden", true)) {
                        "Enable 'Safety Checkpoints' in Desktop Tray -> Permissions first."
                    } else {
                        msg
                    }
                )
            }
        }
    }
}

fun DashboardViewModel.rollbackToCheckpoint(checkpointId: String, repoPath: String) {
    viewModelScope.launch {
        val res = apiClient.rollbackCheckpoint(checkpointId)
        if (res.isSuccess) {
            updateUiState { it.copy(errorMessage = null) }
            loadCheckpoints(repoPath)
            loadDashboardData()
        } else {
            val msg = res.exceptionOrNull()?.message ?: "Rollback failed"
            updateUiState {
                it.copy(
                    errorMessage = if (msg.contains("403") || msg.contains("forbidden", true)) {
                        "Enable 'Safety Checkpoints' in Desktop Tray -> Permissions to rollback."
                    } else {
                        msg
                    }
                )
            }
        }
    }
}

fun DashboardViewModel.killAgentSession(sessionId: String) {
    updateUiState { it.copy(agentSessions = it.agentSessions.filter { s -> s.sessionId != sessionId }) }
    viewModelScope.launch {
        apiClient.killAgentSession(sessionId)
        val updated = apiClient.getAgentSessions()
        if (updated.isSuccess) updateUiState { it.copy(agentSessions = updated.getOrThrow()) }
    }
}
