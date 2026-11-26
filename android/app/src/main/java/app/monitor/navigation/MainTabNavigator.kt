package app.monitor.navigation

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.platform.LocalContext
import app.monitor.core.common.DestructiveActionGate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import app.monitor.core.designsystem.components.MonitorTab
import app.monitor.core.network.TokenStorage
import app.monitor.feature.dashboard.DashboardUiState
import app.monitor.feature.dashboard.DashboardViewModel
import app.monitor.feature.dashboard.HomeScreen
import app.monitor.feature.dashboard.SettingsScreen
import app.monitor.feature.dashboard.SystemScreen
import app.monitor.feature.dashboard.createManualCheckpoint
import app.monitor.feature.dashboard.killAgentSession
import app.monitor.feature.dashboard.rollbackToCheckpoint
import app.monitor.feature.dashboard.toggleCheckpointsSheet
import app.monitor.feature.gitworkspace.*
import app.monitor.feature.gitworkspace.model.GitWorkspaceUiState

@Composable
fun MainTabNavigator(
    targetTab: MonitorTab,
    dashboardUiState: DashboardUiState,
    dashboardViewModel: DashboardViewModel,
    gitUiState: GitWorkspaceUiState,
    gitViewModel: GitWorkspaceViewModel,
    repoDisplayName: String,
    tokenStorage: TokenStorage,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateTab: (MonitorTab) -> Unit,
    onSelectRepo: (String) -> Unit,
    onViewDiff: (String) -> Unit,
    onUnpair: () -> Unit,
    isSessionMonitorActive: Boolean = false,
    onToggleSessionMonitor: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    fun guardDestructive(title: String, subtitle: String, action: () -> Unit) {
        DestructiveActionGate.runGuarded(
            activity = activity,
            appLockEnabled = tokenStorage.isAppLockEnabled(),
            gateDestructiveOps = tokenStorage.isDestructiveOpsBiometricGated(),
            title = title,
            subtitle = subtitle,
            onDenied = { reason ->
                if (reason.isNotBlank()) {
                    Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                }
            },
            onAuthorized = action
        )
    }

    AnimatedContent(
        targetState = targetTab,
        transitionSpec = {
            val isForward = targetState.ordinal >= initialState.ordinal
            val enterOffset = if (isForward) 0.16f else -0.16f
            val exitOffset = if (isForward) -0.16f else 0.16f

            (slideInHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
                initialOffsetX = { fullWidth -> (fullWidth * enterOffset).toInt() }
            ) + fadeIn(
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            ) + scaleIn(
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                initialScale = 0.97f
            )).togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> (fullWidth * exitOffset).toInt() }
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 180)
                ) + scaleOut(
                    animationSpec = tween(durationMillis = 200),
                    targetScale = 0.97f
                )
            )
        },
        label = "TabSpatialGlassTransition"
    ) { activeTab ->
        when (activeTab) {
            MonitorTab.Home -> {
                HomeScreen(
                    uiState = dashboardUiState,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onProjectClick = { repoId ->
                        onSelectRepo(repoId)
                        onNavigateTab(MonitorTab.Projects)
                    },
                    onViewAllProjects = { onNavigateTab(MonitorTab.Projects) },
                    onRefresh = {
                        dashboardViewModel.reconnect()
                        dashboardViewModel.loadDashboardData()
                    },
                    onPairDevice = onUnpair,
                    onAlertsClick = { dashboardViewModel.toggleAlertsSheet(true) },
                    onAckAlert = { alertId -> dashboardViewModel.ackAlert(alertId) },
                    onSnoozeAlert = { alertId -> dashboardViewModel.snoozeAlert(alertId, 3600) },
                    onDismissAlerts = { dashboardViewModel.toggleAlertsSheet(false) },
                    onCheckpointsClick = { repoPath -> dashboardViewModel.toggleCheckpointsSheet(true, repoPath) },
                    onCreateCheckpoint = { repoPath -> dashboardViewModel.createManualCheckpoint(repoPath) },
                    onRollbackCheckpoint = { chkId, repoPath ->
                        guardDestructive("Confirm Checkpoint Rollback", "Authenticate to restore checkpoint") {
                            dashboardViewModel.rollbackToCheckpoint(chkId, repoPath)
                        }
                    },
                    onDismissCheckpoints = { dashboardViewModel.toggleCheckpointsSheet(false) },
                    onKillSession = { sessionId ->
                        guardDestructive("Confirm Emergency Stop", "Authenticate to stop active agent session") {
                            dashboardViewModel.killAgentSession(sessionId)
                        }
                    },
                    onSettingsClick = { onNavigateTab(MonitorTab.Settings) },
                    isSessionMonitorActive = isSessionMonitorActive,
                    onToggleSessionMonitor = onToggleSessionMonitor
                )
            }

            MonitorTab.Projects -> {
                ProjectWorkspaceScreen(
                    repoDisplayName = repoDisplayName,
                    uiState = gitUiState,
                    allProjects = dashboardUiState.projects,
                    onSelectProject = onSelectRepo,
                    onBackClick = { onNavigateTab(MonitorTab.Home) },
                    onFileClick = { path, isStaged ->
                        gitViewModel.loadFileDiff(path, isStaged)
                        onViewDiff(path)
                    },
                    onCommitClick = { msg, paths -> gitViewModel.commit(msg, paths = paths) },
                    onCreateBranch = { branch -> gitViewModel.createBranch(branch) },
                    onSwitchBranch = { branch -> gitViewModel.switchBranch(branch) },
                    onDeleteBranch = { branch ->
                        guardDestructive("Confirm Branch Deletion", "Authenticate to delete '$branch'") {
                            gitViewModel.deleteBranch(branch)
                        }
                    },
                    onMergeBranch = { branch ->
                        guardDestructive("Confirm Branch Merge", "Authenticate to merge '$branch'") {
                            gitViewModel.mergeBranch(branch)
                        }
                    },
                    onLoadMoreCommits = { skip -> gitViewModel.loadGraph(skip) },
                    onPullClick = { gitViewModel.pull() },
                    onPushClick = { gitViewModel.push() },
                    onFetchClick = { gitViewModel.fetch() },
                    onToggleTheme = onToggleTheme,
                    onClearFeedback = { gitViewModel.clearFeedback() },
                    onSetGitIdentity = { name, email -> gitViewModel.setGitIdentity(name, email) },
                    onDismissIdentityDialog = { gitViewModel.toggleIdentityDialog(false) },
                    onOpenIdentityDialog = { gitViewModel.toggleIdentityDialog(true) },
                    onRunTerminalCommand = { cmd -> gitViewModel.runTerminal(cmd) },
                    onStopTerminalCommand = { gitViewModel.stopTerminalCommand() },
                    onClearTerminalLogs = { gitViewModel.clearTerminalLogs() },
                    onSelectTerminalSession = { id -> gitViewModel.selectTerminalSession(id) },
                    onCloseTerminalSession = { id -> gitViewModel.closeTerminalSession(id) },
                    onNewTerminalSession = { gitViewModel.createTerminalSession() },
                    onToggleTerminal = { show -> gitViewModel.toggleTerminal(show) }
                )
            }

            MonitorTab.Git -> {
                GitGraphScreen(
                    uiState = gitUiState,
                    repoDisplayName = repoDisplayName,
                    allProjects = dashboardUiState.projects,
                    onSelectProject = onSelectRepo,
                    onBackClick = { onNavigateTab(MonitorTab.Home) },
                    onCreateBranch = { branch -> gitViewModel.createBranch(branch) },
                    onSwitchBranch = { branch -> gitViewModel.switchBranch(branch) },
                    onDeleteBranch = { branch ->
                        guardDestructive("Confirm Branch Deletion", "Authenticate to delete '$branch'") {
                            gitViewModel.deleteBranch(branch)
                        }
                    },
                    onMergeBranch = { branch ->
                        guardDestructive("Confirm Branch Merge", "Authenticate to merge '$branch'") {
                            gitViewModel.mergeBranch(branch)
                        }
                    },
                    onMergeAllBranches = { branches ->
                        guardDestructive("Confirm Merge All", "Authenticate to merge all branches") {
                            gitViewModel.mergeAllBranches(branches)
                        }
                    },
                    onMergePr = { prNumber ->
                        guardDestructive("Confirm PR Merge", "Authenticate to merge PR #$prNumber") {
                            gitViewModel.mergePullRequest(prNumber)
                        }
                    },
                    onPullClick = { gitViewModel.pull() },
                    onPushClick = { gitViewModel.push() },
                    onFetchClick = { gitViewModel.fetch() },
                    onFileClick = { path, staged, commit -> gitViewModel.loadFileDiff(path, staged, commit) },
                    onCommitSelect = { commitOid -> gitViewModel.loadCommitFiles(commitOid) },
                    onViewDiff = onViewDiff,
                    onRefresh = { gitViewModel.refreshAll() },
                    onLoadMore = { skip -> gitViewModel.loadGraph(skip) },
                    onClearFeedback = { gitViewModel.clearFeedback() }
                )
            }

            MonitorTab.System -> {
                SystemScreen(
                    uiState = dashboardUiState,
                    onBackClick = { onNavigateTab(MonitorTab.Home) },
                    onRefresh = { dashboardViewModel.loadDashboardData() },
                    onPairDevice = onUnpair
                )
            }

            MonitorTab.Settings -> {
                SettingsScreen(
                    uiState = dashboardUiState,
                    tokenStorage = tokenStorage,
                    onPairDevice = {
                        tokenStorage.clearAccountData()
                        onUnpair()
                    },
                    onSignOut = {
                        tokenStorage.clearAccountData()
                        onUnpair()
                    },
                    onBackClick = { onNavigateTab(MonitorTab.Home) },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
        }
    }
}
