package app.monitor.feature.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas
import app.monitor.core.model.ChargingState
import app.monitor.feature.dashboard.model.LogLevel
import app.monitor.feature.dashboard.model.RealAuditLog
import app.monitor.feature.dashboard.model.formatLogTime
import app.monitor.feature.dashboard.sections.LogsListCard
import app.monitor.feature.dashboard.sections.LogsStreamHeaderBanner
import app.monitor.feature.dashboard.sections.ProcessesListCard
import app.monitor.feature.dashboard.sections.ProcessesTabHeaderBanner
import app.monitor.feature.dashboard.sections.SystemHeaderSection
import app.monitor.feature.dashboard.sections.SystemOverviewTab
import app.monitor.feature.dashboard.sections.SystemTabsRow

/**
 * Brand-New Redesigned Executive System Telemetry Hub.
 * Features 3 seamlessly synchronized tabs: Overview, Processes, and Logs.
 * 100% real system telemetry animations with zero fake counters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(
    uiState: DashboardUiState,
    onBackClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onPairDevice: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snapshot = uiState.systemSnapshot

    var processSearchQuery by remember { mutableStateOf("") }
    var isProcessSearchActive by remember { mutableStateOf(false) }

    var logSearchQuery by remember { mutableStateOf("") }
    var isLogSearchActive by remember { mutableStateOf(false) }
    var isLogsPaused by remember { mutableStateOf(false) }
    var selectedLogLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    val auditLogs = remember { mutableStateListOf<RealAuditLog>() }

    // Live System Telemetry Synchronization Loop (Real broker heartbeat)
    LaunchedEffect(snapshot?.observedAtUtcMs) {
        if (!isLogsPaused && snapshot != null) {
            val timeStr = formatLogTime(snapshot.observedAtUtcMs)
            val routeLabel = uiState.activeRoute.ifBlank { "Desktop Agent" }
            auditLogs.add(0, RealAuditLog("hb_${snapshot.observedAtUtcMs}", timeStr, "Telemetry heartbeat synced via $routeLabel", LogLevel.INFO))
            auditLogs.add(1, RealAuditLog("metric_${snapshot.observedAtUtcMs}", timeStr, "CPU: ${snapshot.cpu.totalUsagePercent.toInt()}% · RAM: ${snapshot.ram.usagePercent.toInt()}%", LogLevel.METRIC))
            auditLogs.add(2, RealAuditLog("proc_${snapshot.observedAtUtcMs}", timeStr, "Processes updated (${snapshot.cpu.topProcesses.size} active monitored)", LogLevel.INFO))

            if (snapshot.battery.hasBattery) {
                val chargingDesc = when (snapshot.battery.chargingState) {
                    ChargingState.Charging -> "Charging"
                    ChargingState.Discharging -> "Discharging"
                    ChargingState.Full -> "Full"
                    else -> "Connected"
                }
                auditLogs.add(3, RealAuditLog("batt_${snapshot.observedAtUtcMs}", timeStr, "Battery: ${snapshot.battery.percentage?.toInt() ?: 0}% ($chargingDesc)", LogLevel.INFO))
            }
            while (auditLogs.size > 80) auditLogs.removeAt(auditLogs.lastIndex)
        }
    }

    LaunchedEffect(uiState.activity.firstOrNull()) {
        uiState.activity.firstOrNull()?.let { msg ->
            val level = if (msg.contains("error", true) || msg.contains("critical", true) || msg.contains("failed", true)) LogLevel.ERROR else LogLevel.WARNING
            auditLogs.add(0, RealAuditLog("act_${System.currentTimeMillis()}", formatLogTime(System.currentTimeMillis()), msg, level))
        }
    }

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark

    Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF07080E) else Color.Transparent)) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding().imePadding()) {
                // 1. Executive Minimalist Header (Matching Home & Git Graph 1:1)
                SystemHeaderSection(
                    isOnline = uiState.isOnline,
                    hostname = snapshot?.hostname ?: "Desktop Workstation",
                    onRefresh = onRefresh,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                )

                // 2. Minimalist 3-Segment Control (Overview, Processes, Logs)
                SystemTabsRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    processCount = snapshot?.cpu?.topProcesses?.size ?: 0,
                    logCount = auditLogs.size,
                    isLive = uiState.isOnline && !isLogsPaused
                )

                // 3. Unboxed, Clean Telemetry Stream
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 126.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stable keys keep each tab's item identity across telemetry
                    // refreshes, avoiding needless recomposition/animation restarts.
                    when (selectedTab) {
                        0 -> item(key = "system-overview") {
                            SystemOverviewTab(
                                snapshot = snapshot,
                                isOnline = uiState.isOnline,
                                projects = uiState.projects
                            )
                        }
                        1 -> {
                            item(key = "processes-header") {
                                ProcessesTabHeaderBanner(
                                    isOnline = uiState.isOnline,
                                    hostname = snapshot?.hostname ?: "Desktop Workstation",
                                    onRefresh = onRefresh
                                )
                            }
                            item(key = "processes-list") {
                                ProcessesListCard(
                                    processes = snapshot?.cpu?.topProcesses.orEmpty(),
                                    isSearchActive = isProcessSearchActive,
                                    onToggleSearch = { isProcessSearchActive = !isProcessSearchActive },
                                    searchQuery = processSearchQuery,
                                    onSearchQueryChange = { processSearchQuery = it }
                                )
                            }
                        }
                        2 -> {
                            item(key = "logs-header") {
                                LogsStreamHeaderBanner(
                                    isOnline = uiState.isOnline,
                                    isPaused = isLogsPaused,
                                    onTogglePause = { isLogsPaused = !isLogsPaused },
                                    isSearchActive = isLogSearchActive,
                                    onToggleSearch = { isLogSearchActive = !isLogSearchActive },
                                    searchQuery = logSearchQuery,
                                    onSearchQueryChange = { logSearchQuery = it },
                                    selectedFilter = selectedLogLevelFilter,
                                    onSelectFilter = {
                                        selectedLogLevelFilter = if (selectedLogLevelFilter == it) null else it
                                    },
                                    totalLogsCount = auditLogs.size
                                )
                            }
                            item(key = "logs-list") {
                                LogsListCard(
                                    logs = auditLogs.filter { log ->
                                        val matchesSearch = logSearchQuery.isBlank() ||
                                            log.message.contains(logSearchQuery, ignoreCase = true) ||
                                            log.timestamp.contains(logSearchQuery)
                                        val matchesFilter = selectedLogLevelFilter == null || log.level == selectedLogLevelFilter
                                        matchesSearch && matchesFilter
                                    }
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
