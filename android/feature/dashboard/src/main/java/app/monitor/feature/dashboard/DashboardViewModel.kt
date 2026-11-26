package app.monitor.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.monitor.core.common.AlertNotifier
import app.monitor.core.common.ReconnectPolicy
import app.monitor.core.model.AgentSessionSummary
import app.monitor.core.model.AlertItem
import app.monitor.core.model.PermissionsResponse
import app.monitor.core.model.RepoSummary
import app.monitor.core.model.ServerEvent
import app.monitor.core.model.SystemSnapshot
import app.monitor.core.network.DashboardConnection
import app.monitor.core.network.MonitorApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isOnline: Boolean = false,
    val activeRoute: String = "Direct LAN Stream",
    val systemSnapshot: SystemSnapshot? = null,
    val projects: List<RepoSummary> = emptyList(),
    val permissions: PermissionsResponse? = null,
    val errorMessage: String? = null,
    val requiresPairing: Boolean = false,
    val activity: List<String> = emptyList(),
    val reconnectAttempt: Int = 0,
    val alerts: List<AlertItem> = emptyList(),
    val agentSessions: List<AgentSessionSummary> = emptyList(),
    val showAlertsSheet: Boolean = false,
    val checkpoints: List<app.monitor.core.model.CheckpointItem> = emptyList(),
    val showCheckpointsSheet: Boolean = false,
    val selectedRepoForCheckpoints: String? = null
)

class DashboardViewModel(
    internal val apiClient: DashboardConnection,
    private val alertNotifier: AlertNotifier = AlertNotifier.NoOp
) : ViewModel() {
    /** Bounded 60 s backoff; reset on every successful ticket or server event. */
    private val reconnectPolicy = ReconnectPolicy()

    private val _uiState = MutableStateFlow(DashboardUiState(projects = cachedProjects, isLoading = cachedProjects.isEmpty()))
    val uiState = _uiState.asStateFlow()
    val terminalEvents = kotlinx.coroutines.flow.MutableSharedFlow<ServerEvent.TerminalOutput>(extraBufferCapacity = 128)
    val workspaceEvents = kotlinx.coroutines.flow.MutableSharedFlow<ServerEvent.WorkspaceUpdated>(extraBufferCapacity = 64)
    private var connectionJob: Job? = null
    private var refreshJob: Job? = null
    private var sessionSyncJob: Job? = null

    companion object {
        @Volatile
        internal var cachedProjects: List<RepoSummary> = emptyList()
    }

    internal fun updateUiState(transform: (DashboardUiState) -> DashboardUiState) = _uiState.update(transform)

    fun start() {
        if (connectionJob?.isActive == true) return
        _uiState.update { it.copy(requiresPairing = false, reconnectAttempt = 0) }
        reconnectPolicy.reset()
        loadDashboardData()
        sessionSyncJob?.cancel()
        sessionSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                if (_uiState.value.isOnline) {
                    val sRes = apiClient.getAgentSessions()
                    if (sRes.isSuccess) _uiState.update { it.copy(agentSessions = sRes.getOrThrow()) }
                }
            }
        }
        connectionJob = viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    val ticket = apiClient.acquireWsTicket().getOrThrow()
                    reconnectPolicy.onSuccess()
                    _uiState.update { it.copy(activeRoute = apiClient.activeRoute) }
                    apiClient.streamServerEvents(ticket).collect { event ->
                        attempt = 0
                        reconnectPolicy.onSuccess()
                        val currentRoute = apiClient.activeRoute
                        _uiState.update { it.copy(requiresPairing = false, reconnectAttempt = 0, activeRoute = currentRoute) }
                        when (event) {
                            is ServerEvent.TelemetryTick -> _uiState.update {
                                it.copy(isLoading = false, isOnline = event.isOnline, systemSnapshot = event.snapshot,
                                    errorMessage = if (event.isOnline) null else "Desktop is offline", requiresPairing = false, activeRoute = currentRoute)
                            }
                            is ServerEvent.ProjectUpdated -> _uiState.update { state ->
                                val updated = (state.projects.filterNot { it.repoId == event.summary.repoId } + event.summary)
                                    .sortedBy { it.displayName.lowercase() }
                                cachedProjects = updated
                                state.copy(projects = updated, isLoading = false)
                            }
                            is ServerEvent.WorkspaceUpdated -> {
                                workspaceEvents.tryEmit(event)
                            }
                            is ServerEvent.TerminalOutput -> {
                                terminalEvents.tryEmit(event)
                            }
                            is ServerEvent.OperationProgress -> _uiState.update {
                                it.copy(activity = (listOf("${event.stage}: ${event.message}") + it.activity).take(50))
                            }
                            is ServerEvent.AlertTriggered -> {
                                // Post a generic notification (no repo/terminal payload).
                                alertNotifier.postAlert(event.alertId, event.severity)
                                _uiState.update {
                                    it.copy(activity = (listOf("${event.ruleName}: ${event.message}") + it.activity).take(50))
                                }
                            }
                            is ServerEvent.ResyncRequired -> loadDashboardData()
                            else -> Unit
                        }
                    }
                    disconnected("Connection closed. Reconnecting…")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    disconnected(error.message ?: "Cannot reach desktop. Reconnecting…", error)
                }
                _uiState.update { it.copy(reconnectAttempt = attempt + 1) }
                delay(reconnectPolicy.nextDelayMs())
                attempt++
                loadDashboardData()
            }
        }
    }

    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        refreshJob?.cancel()
        refreshJob = null
        sessionSyncJob?.cancel()
        sessionSyncJob = null
        _uiState.update { it.copy(isOnline = false) }
    }

    /** The OS reports a new default route after Wi-Fi, VPN or mobile-data changes. */
    fun reconnect() {
        apiClient.notifyNetworkChanged()
        if (connectionJob?.isActive == true && _uiState.value.isOnline) {
            loadDashboardData()
        } else {
            stop()
            start()
        }
    }

    fun loadDashboardData() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.systemSnapshot == null) }
            val snapshotRequest = async { apiClient.getSystemSnapshot() }
            val projectsRequest = async { apiClient.getProjects() }
            val permissionsRequest = async { apiClient.getPermissions() }
            val alertsRequest = async { apiClient.getAlerts() }
            val sessionsRequest = async { apiClient.getAgentSessions() }
            val snapshot = snapshotRequest.await()
            val projects = projectsRequest.await()
            val permissions = permissionsRequest.await()
            val alerts = alertsRequest.await()
            val sessions = sessionsRequest.await()
            val failures = listOfNotNull(snapshot.exceptionOrNull(), projects.exceptionOrNull())
            val authError = failures.firstOrNull { (it as? MonitorApiException)?.status == 401 }
            val error = authError ?: failures.firstOrNull()
            val unauthorized = authError != null
            _uiState.update {
                val requiresPairing = unauthorized || it.requiresPairing
                val freshSnapshot = snapshot.getOrNull()?.takeIf { incoming ->
                    it.systemSnapshot == null || incoming.observedAtUtcMs >= it.systemSnapshot.observedAtUtcMs
                }
                val liveSnapshotSuccess = !requiresPairing && snapshot.isSuccess
                // A failed parallel HTTP refresh must not mark a healthy live stream offline,
                // and a live snapshot response confirms desktop reachability after handover.
                val resolvedProjects = projects.getOrNull() ?: it.projects
                if (resolvedProjects.isNotEmpty()) cachedProjects = resolvedProjects
                val nextOnline = if (requiresPairing) false else (liveSnapshotSuccess || it.isOnline)
                it.copy(isLoading = false, isOnline = nextOnline,
                    activeRoute = apiClient.activeRoute,
                    systemSnapshot = freshSnapshot ?: it.systemSnapshot,
                    projects = resolvedProjects,
                    permissions = permissions.getOrNull() ?: it.permissions,
                    alerts = alerts.getOrNull() ?: it.alerts,
                    agentSessions = sessions.getOrNull() ?: it.agentSessions,
                    errorMessage = if (nextOnline && !unauthorized) null else error?.message,
                    requiresPairing = requiresPairing)
            }
            if (unauthorized) { connectionJob?.cancel(); sessionSyncJob?.cancel() }
        }
    }

    fun toggleAlertsSheet(show: Boolean) {
        _uiState.update { it.copy(showAlertsSheet = show) }
        if (show) loadDashboardData()
    }

    fun ackAlert(alertId: String) = viewModelScope.launch {
        apiClient.ackAlert(alertId)
        val updated = apiClient.getAlerts()
        if (updated.isSuccess) _uiState.update { it.copy(alerts = updated.getOrThrow()) }
    }

    fun snoozeAlert(alertId: String, durationS: Long = 3600) = viewModelScope.launch {
        apiClient.snoozeAlert(alertId, durationS)
        val updated = apiClient.getAlerts()
        if (updated.isSuccess) _uiState.update { it.copy(alerts = updated.getOrThrow()) }
    }

    fun killProcess(pid: Long, onResult: (Result<Boolean>) -> Unit = {}) = viewModelScope.launch {
        val result = apiClient.killProcess(pid)
        if (result.isSuccess) loadDashboardData()
        onResult(result)
    }

    private fun disconnected(message: String, error: Throwable? = null) {
        val unauthorized = (error as? MonitorApiException)?.status == 401
        _uiState.update { it.copy(isLoading = false, isOnline = false, errorMessage = message,
            requiresPairing = unauthorized || it.requiresPairing) }
        if (unauthorized) { connectionJob?.cancel(); sessionSyncJob?.cancel() }
    }
}

/**
 * Legacy equal-jitter helper retained for existing regression tests. The live
 * stream now uses [ReconnectPolicy], which adds a hard 60 s cap and explicit
 * reset-on-success. Kept unchanged so prior test expectations still hold.
 */
internal fun reconnectDelayMillis(attempt: Int, jitter: Double = Math.random()): Long {
    val c = (1_000L shl attempt.coerceIn(0, 4)).coerceAtMost(15_000L)
    return (c / 2 + (c / 2 * jitter.coerceIn(0.0, 1.0)).toLong())
}

