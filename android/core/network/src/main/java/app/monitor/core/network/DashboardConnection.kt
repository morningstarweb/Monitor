package app.monitor.core.network

import app.monitor.core.model.AgentSessionSummary
import app.monitor.core.model.AlertItem
import app.monitor.core.model.PermissionsResponse
import app.monitor.core.model.RepoSummary
import app.monitor.core.model.ServerEvent
import app.monitor.core.model.SystemSnapshot
import kotlinx.coroutines.flow.Flow

interface DashboardConnection {
    val activeRoute: String get() = "Direct LAN Stream"
    fun notifyNetworkChanged() {}
    suspend fun acquireWsTicket(): Result<String>
    suspend fun getSystemSnapshot(): Result<SystemSnapshot>
    suspend fun getProjects(): Result<List<RepoSummary>>
    suspend fun getPermissions(): Result<PermissionsResponse> = Result.success(PermissionsResponse())
    suspend fun killProcess(pid: Long): Result<Boolean> = Result.success(true)
    suspend fun getAlerts(limit: Int = 50, activeOnly: Boolean = false): Result<List<AlertItem>> = Result.success(emptyList())
    suspend fun ackAlert(alertId: String): Result<Boolean> = Result.success(true)
    suspend fun snoozeAlert(alertId: String, durationS: Long = 3600): Result<Boolean> = Result.success(true)
    suspend fun getAgentSessions(): Result<List<AgentSessionSummary>> = Result.success(emptyList())
    suspend fun getCheckpoints(repoPath: String, limit: Int = 50): Result<List<app.monitor.core.model.CheckpointItem>> = Result.success(emptyList())
    suspend fun createCheckpoint(repoPath: String, description: String? = null): Result<app.monitor.core.model.CheckpointItem?> = Result.success(null)
    suspend fun rollbackCheckpoint(checkpointId: String): Result<app.monitor.core.model.RollbackResponse?> = Result.success(null)
    suspend fun killAgentSession(sessionId: String): Result<Boolean> = Result.success(true)
    fun streamServerEvents(ticket: String): Flow<ServerEvent>
}
