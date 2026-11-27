package app.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.monitor.core.designsystem.components.BottomNavigationBar
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas
import app.monitor.core.designsystem.components.MonitorTab
import app.monitor.core.common.AlertNotifier
import app.monitor.core.designsystem.theme.MonitorTheme
import app.monitor.core.network.MonitorApiClient
import app.monitor.core.network.TokenStorage
import app.monitor.feature.dashboard.DashboardViewModel
import app.monitor.feature.gitworkspace.GitWorkspaceViewModel
import app.monitor.feature.gitworkspace.handleTerminalOutput
import app.monitor.feature.onboarding.OnboardingScreen
import app.monitor.feature.onboarding.OnboardingViewModel
import app.monitor.navigation.MainTabNavigator
import app.monitor.navigation.ObserveDesktopConnection
import app.monitor.security.AppLockGate
import app.monitor.service.ActiveSessionController
import app.monitor.service.SessionMonitorPrefs

class MainActivity : ComponentActivity() {
    private val monitorApp get() = application as MonitorApp

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            postInitialCompanionStatusIfNeeded()
        }
    }

    private fun postInitialCompanionStatusIfNeeded() {
        val ts = monitorApp.tokenStorage
        if (!ts.isAuthenticated()) {
            val signedInEmail = ts.getUserEmail()
            val message = if (!signedInEmail.isNullOrBlank()) {
                "Signed in as $signedInEmail. Open Monitor Tray -> Mobile & Devices on your Windows PC and verify the 6-digit OTP or QR."
            } else {
                "Monitor Android is ready. Sign in with Google or pair with your Windows laptop app to monitor live sessions."
            }
            app.monitor.notification.AlertNotificationManager.showCompanionStatusNotification(
                context = this,
                title = "Connect Your Windows PC",
                message = message
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.monitor.notification.AlertNotificationManager.createChannels(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                postInitialCompanionStatusIfNeeded()
            }
        } else {
            postInitialCompanionStatusIfNeeded()
        }

        setContent {
            var themeMode by rememberSaveable { mutableStateOf(monitorApp.tokenStorage.getThemeMode()) }
            val isDark = themeMode != "light"

            val insetsController = remember(window) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            }
            LaunchedEffect(isDark) {
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }

            MonitorTheme(darkTheme = isDark) {
                AppLockGate(activity = this@MainActivity, tokenStorage = monitorApp.tokenStorage) {
                    MonitorAppContent(
                        tokenStorage = monitorApp.tokenStorage,
                        apiClient = monitorApp.apiClient,
                        alertNotifier = monitorApp.alertNotifier,
                        isDarkTheme = isDark,
                        onToggleTheme = {
                            val next = if (isDark) "light" else "dark"
                            themeMode = next
                            monitorApp.tokenStorage.saveThemeMode(next)
                        }
                    )
                }
            }
        }
    }

    // POST_NOTIFICATIONS result needs no callback here: channels are created in
    // onCreate, and AndroidAlertNotifier checks permission state before posting.
}

@Composable
fun MonitorAppContent(
    tokenStorage: TokenStorage,
    apiClient: MonitorApiClient,
    alertNotifier: AlertNotifier = AlertNotifier.NoOp,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    var isPaired by rememberSaveable { mutableStateOf(tokenStorage.isAuthenticated()) }
    var currentTab by rememberSaveable { mutableStateOf(MonitorTab.Home) }
    var viewingRepoId by rememberSaveable { mutableStateOf<String?>(null) }
    var isNavBarVisible by rememberSaveable { mutableStateOf(true) }

    val appContext = LocalContext.current.applicationContext
    // User opt-in for the active-session foreground service; not a credential.
    var sessionMonitorEnabled by rememberSaveable {
        mutableStateOf(SessionMonitorPrefs.isEnabled(appContext))
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f && isNavBarVisible) isNavBarVisible = false
                else if (available.y > 14f && !isNavBarVisible) isNavBarVisible = true
                return Offset.Zero
            }
        }
    }

    BackHandler(enabled = isPaired && currentTab != MonitorTab.Home) {
        currentTab = MonitorTab.Home
        isNavBarVisible = true
    }

    if (!isPaired) {
        val onboardingViewModel: OnboardingViewModel = viewModel {
            OnboardingViewModel(apiClient = apiClient, tokenStorage = tokenStorage)
        }
        OnboardingScreen(
            viewModel = onboardingViewModel,
            onPairingSuccess = { isPaired = true }
        )
    } else {
        val dashboardViewModel: DashboardViewModel = viewModel {
            DashboardViewModel(apiClient = apiClient, alertNotifier = alertNotifier)
        }
        val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
        ObserveDesktopConnection(dashboardViewModel)

        val activeSessionCount = dashboardUiState.agentSessions.count { session ->
            session.state.equals("Active", ignoreCase = true) ||
                session.state.equals("Stalled", ignoreCase = true)
        }
        val monitorRunning = sessionMonitorEnabled && activeSessionCount > 0

        // The foreground service exists only while the user opted in AND at
        // least one session is active. Idle/disconnect stops it immediately.
        LaunchedEffect(monitorRunning, activeSessionCount) {
            if (monitorRunning) {
                ActiveSessionController.start(appContext, activeSessionCount)
            } else {
                ActiveSessionController.stop(appContext)
            }
        }

        LaunchedEffect(
            dashboardUiState.isOnline,
            dashboardUiState.systemSnapshot?.hostname,
            dashboardUiState.activeRoute,
            activeSessionCount
        ) {
            val hostname = dashboardUiState.systemSnapshot?.hostname
            val friendlyRoute = when {
                dashboardUiState.activeRoute.contains("LAN", ignoreCase = true) -> "Direct Wi-Fi"
                dashboardUiState.activeRoute.contains("TURN", ignoreCase = true) -> "WebRTC Relay"
                dashboardUiState.activeRoute.contains("WebRTC", ignoreCase = true) -> "WebRTC P2P"
                dashboardUiState.activeRoute.contains("Cloud", ignoreCase = true) -> "Cloud Relay"
                else -> dashboardUiState.activeRoute.ifBlank { "Encrypted Link" }
            }
            val rightValue = if (activeSessionCount > 0) {
                "$activeSessionCount Agent(s) Active"
            } else {
                "Noise E2EE"
            }
            val spec = when {
                dashboardUiState.isOnline -> app.monitor.notification.MonitorNotifSpec(
                    title = hostname ?: "Windows PC",
                    subtitle = "Live telemetry & repo sync active",
                    badgeText = "LIVE",
                    leftChipLabel = "ROUTE",
                    leftChipValue = friendlyRoute,
                    rightChipLabel = if (activeSessionCount > 0) "AGENTS" else "SECURITY",
                    rightChipValue = rightValue,
                    tone = app.monitor.notification.MonitorNotifTone.LIVE
                )
                hostname != null -> app.monitor.notification.MonitorNotifSpec(
                    title = hostname,
                    subtitle = "Host offline · open Monitor on your PC",
                    badgeText = "OFFLINE",
                    leftChipLabel = "ROUTE",
                    leftChipValue = "Standby",
                    rightChipLabel = "STATUS",
                    rightChipValue = "Waiting",
                    tone = app.monitor.notification.MonitorNotifTone.WARNING
                )
                else -> app.monitor.notification.MonitorNotifSpec(
                    title = "Monitor Workstation",
                    subtitle = "Linking securely with your PC...",
                    badgeText = "SYNCING",
                    leftChipLabel = "ROUTE",
                    leftChipValue = "Connecting",
                    rightChipLabel = "STATUS",
                    rightChipValue = "Handshaking",
                    tone = app.monitor.notification.MonitorNotifTone.VIOLET
                )
            }
            app.monitor.notification.AlertNotificationManager.showCompanionStatusNotification(
                context = appContext,
                title = spec.title,
                message = spec.subtitle,
                badgeText = spec.badgeText,
                leftChipLabel = spec.leftChipLabel,
                leftChipValue = spec.leftChipValue,
                rightChipLabel = spec.rightChipLabel,
                rightChipValue = spec.rightChipValue,
                tone = spec.tone
            )
        }

        // Disconnect action on the ongoing notification performs a real unpair.
        LaunchedEffect(dashboardViewModel) {
            (appContext as? MonitorApp)?.disconnectRequests?.collect {
                sessionMonitorEnabled = false
                SessionMonitorPrefs.setEnabled(appContext, false)
                ActiveSessionController.stop(appContext)
                app.monitor.notification.AlertNotificationManager.cancelCompanionNotification(appContext)
                dashboardViewModel.stop()
                tokenStorage.clearAccountData()
                isPaired = false
            }
        }

        val activeRepoId = viewingRepoId ?: dashboardUiState.projects.firstOrNull()?.repoId.orEmpty()
        val activeProject = dashboardUiState.projects.find { it.repoId == activeRepoId }
        val repoDisplayName = activeProject?.displayName ?: "monitor-android"
        val gitViewModel: GitWorkspaceViewModel = viewModel(key = "workspace:$activeRepoId") {
            GitWorkspaceViewModel(repoId = activeRepoId, apiClient = apiClient)
        }
        val gitUiState by gitViewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(activeRepoId) {
            if (activeRepoId.isNotBlank()) gitViewModel.selectProject(activeRepoId)
        }

        LaunchedEffect(gitViewModel) {
            dashboardViewModel.workspaceEvents.collect { event ->
                gitViewModel.updateWorkspaceSnapshot(event.repoId, event.snapshot)
            }
        }

        LaunchedEffect(gitViewModel) {
            dashboardViewModel.terminalEvents.collect { output ->
                gitViewModel.handleTerminalOutput(output.repoId, output.sessionId, output.text)
            }
        }

        LaunchedEffect(
            activeProject?.lastActivityUtcMs,
            activeProject?.currentBranch,
            activeProject?.modifiedCount,
            activeProject?.untrackedCount,
            activeProject?.stagedCount
        ) {
            if (activeRepoId.isNotBlank()) gitViewModel.refreshAll()
        }

        // Edge-to-edge container: allows animated background waves to flow seamlessly under the floating dock
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF07080E) else Color(0xFFF3F6F8))
                .nestedScroll(nestedScrollConnection)
        ) {
                MonitorGlobalBackgroundCanvas(isDarkTheme = isDarkTheme)

                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                    label = "TabContent",
                    modifier = Modifier.fillMaxSize()
                ) { targetTab ->
                    MainTabNavigator(
                        targetTab = targetTab,
                        dashboardUiState = dashboardUiState,
                        dashboardViewModel = dashboardViewModel,
                        gitUiState = gitUiState,
                        gitViewModel = gitViewModel,
                        repoDisplayName = repoDisplayName,
                        tokenStorage = tokenStorage,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onNavigateTab = {
                            currentTab = it
                            isNavBarVisible = true
                        },
                        onSelectRepo = { viewingRepoId = it },
                        onViewDiff = {},
                        onUnpair = {
                            sessionMonitorEnabled = false
                            SessionMonitorPrefs.setEnabled(appContext, false)
                            ActiveSessionController.stop(appContext)
                            app.monitor.notification.AlertNotificationManager.cancelCompanionNotification(appContext)
                            isPaired = false
                        },
                        isSessionMonitorActive = monitorRunning,
                        onToggleSessionMonitor = { enabled ->
                            sessionMonitorEnabled = enabled
                            SessionMonitorPrefs.setEnabled(appContext, enabled)
                            if (!enabled) ActiveSessionController.stop(appContext)
                        }
                    )
                }

                // YouTube-style dynamic animated slide-down / slide-up navigation dock
                AnimatedVisibility(
                    visible = isNavBarVisible,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(durationMillis = 260)) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    BottomNavigationBar(currentTab = currentTab, onTabSelected = { currentTab = it; isNavBarVisible = true })
                }
            }
        }
    }
