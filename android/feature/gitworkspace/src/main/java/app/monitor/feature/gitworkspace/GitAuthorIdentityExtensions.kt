package app.monitor.feature.gitworkspace

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun GitWorkspaceViewModel.loadGitIdentity() = viewModelScope.launch {
    connection.getGitIdentity().onSuccess { id -> _uiState.update { it.copy(gitIdentity = id) } }
}

fun GitWorkspaceViewModel.toggleIdentityDialog(show: Boolean) {
    if (show) loadGitIdentity()
    _uiState.update { it.copy(showIdentityDialog = show) }
}

fun GitWorkspaceViewModel.setGitIdentity(name: String, email: String, onDone: () -> Unit = {}) = viewModelScope.launch {
    connection.setGitIdentity(name, email).fold(
        onSuccess = { id ->
            _uiState.update { it.copy(gitIdentity = id, showIdentityDialog = false, userMessage = "Git author updated!") }
            onDone()
        },
        onFailure = { e ->
            _uiState.update { it.copy(errorMessage = e.message ?: "Cannot save identity") }
        }
    )
}
