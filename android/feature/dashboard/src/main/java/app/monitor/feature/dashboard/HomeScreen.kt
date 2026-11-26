package app.monitor.feature.dashboard

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.components.AgentStatusCard
import app.monitor.feature.dashboard.components.ApprovedProjectCard
import app.monitor.feature.dashboard.components.DashboardBackgroundDecorations
import app.monitor.feature.dashboard.components.LiveCpuGraphCard
import app.monitor.feature.dashboard.components.LiveFanIndicatorCard
import app.monitor.feature.dashboard.components.resolveCoolingSnapshot
import app.monitor.feature.dashboard.dialogs.AlertsBottomSheet
import app.monitor.feature.dashboard.dialogs.CheckpointsBottomSheet
import app.monitor.feature.dashboard.sections.ConnectedDeviceCard
import app.monitor.feature.dashboard.sections.HomeHeaderSection
import app.monitor.feature.dashboard.sections.ProjectsSectionHeader
import app.monitor.feature.dashboard.sections.ThreeStatMetricRow

/**
 * Clean Composition Root for Dashboard Home Screen (< 200 lines).
 * Displays rich active telemetry and automatically falls back to real desktop data when connected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: DashboardUiState,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onProjectClick: (String) -> Unit,
    onViewAllProjects: () -> Unit = {},
    onRefresh: () -> Unit,
    onPairDevice: () -> Unit,
    onAlertsClick: () -> Unit = {},
    onAckAlert: (String) -> Unit = {},
    onSnoozeAlert: (String) -> Unit = {},
    onDismissAlerts: () -> Unit = {},
    onCheckpointsClick: (String) -> Unit = {},
    onCreateCheckpoint: (String) -> Unit = {},
    onRollbackCheckpoint: (String, String) -> Unit = { _, _ -> },
    onDismissCheckpoints: () -> Unit = {},
    onKillSession: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isSessionMonitorActive: Boolean = false,
    onToggleSessionMonitor: (Boolean) -> Unit = {}
) {
    val snapshot = uiState.systemSnapshot
    val activeAgents = remember(uiState.agentSessions) {
        uiState.agentSessions.filter { !it.state.equals("Ended", ignoreCase = true) }
    }
    var selectedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeAgent = remember(activeAgents, selectedSessionId) {
        activeAgents.firstOrNull { it.sessionId == selectedSessionId } ?: activeAgents.firstOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF07080E) else Color.Transparent)
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeHeaderSection(
                    uiState = uiState,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onAlertsClick = onAlertsClick
                )
            }

            item {
                ConnectedDeviceCard(
                    snapshot = snapshot,
                    isOnline = uiState.isOnline,
                    requiresPairing = uiState.requiresPairing,
                    errorMessage = uiState.errorMessage,
                    onPairDevice = onPairDevice,
                    onRefresh = onRefresh
                )
            }

            if (activeAgent != null) {
                item {
                    AgentStatusCard(
                        session = activeAgent,
                        sessions = activeAgents,
                        onSelectSession = { selectedSessionId = it },
                        onClick = {
                            val repo = activeAgent.repoPath?.takeIf { it.isNotBlank() }
                                ?: uiState.projects.firstOrNull()?.path.orEmpty()
                            onCheckpointsClick(repo)
                        },
                        onEmergencyStop = { onKillSession(activeAgent.sessionId) },
                        isSessionMonitorActive = isSessionMonitorActive,
                        onToggleSessionMonitor = onToggleSessionMonitor
                    )
                }
            }

            item {
                ThreeStatMetricRow(snapshot = snapshot)
            }

            item {
                LiveCpuGraphCard(snapshot = snapshot)
            }

            val cooling = resolveCoolingSnapshot(snapshot)
            if (cooling != null && cooling.hasFan && !cooling.isFanless) {
                item {
                    LiveFanIndicatorCard(cooling = cooling)
                }
            }

            item {
                ProjectsSectionHeader(
                    projectCount = uiState.projects.size,
                    onAddClick = onViewAllProjects
                )
            }

            if (uiState.projects.isEmpty()) {
                item {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDarkTheme) Color(0xFF0C0E1A).copy(alpha = 0.94f)
                                else Color.White.copy(alpha = 0.92f),
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                if (isDarkTheme) Color(0x35818CF8) else Color(0x30CBD5E1),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isDarkTheme) Color(0xFF1A1E38) else Color(0xFFEEF2FF),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "No projects synced yet",
                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                            fontSize = 13.5.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            items(uiState.projects, key = { it.repoId }) { project ->
                val isActive = uiState.projects.firstOrNull()?.repoId == project.repoId
                ApprovedProjectCard(
                    project = project,
                    isActive = isActive,
                    onClick = { onProjectClick(project.repoId) }
                )
            }
        }
        }

        if (uiState.showAlertsSheet) {
            AlertsBottomSheet(
                alerts = uiState.alerts,
                onDismiss = onDismissAlerts,
                onAck = onAckAlert,
                onSnooze = onSnoozeAlert
            )
        }

        if (uiState.showCheckpointsSheet) {
            val targetRepo = uiState.selectedRepoForCheckpoints?.takeIf { it.isNotBlank() }
                ?: uiState.projects.firstOrNull()?.path.orEmpty()
            CheckpointsBottomSheet(
                checkpoints = uiState.checkpoints,
                activeSession = activeAgent,
                onRollback = { chkId -> onRollbackCheckpoint(chkId, targetRepo) },
                onCreateCheckpoint = { onCreateCheckpoint(targetRepo) },
                errorMessage = uiState.errorMessage,
                onEmergencyStop = { onKillSession(activeAgent?.sessionId ?: "") },
                onDismiss = onDismissCheckpoints
            )
        }
    }
}
