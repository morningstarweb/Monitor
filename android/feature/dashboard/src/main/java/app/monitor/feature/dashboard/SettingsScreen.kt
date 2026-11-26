package app.monitor.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.monitor.core.network.TokenStorage
import app.monitor.feature.dashboard.components.SettingsBackgroundDecorations
import app.monitor.feature.dashboard.sections.SettingsHeaderSection
import app.monitor.feature.dashboard.settings.*
import app.monitor.feature.dashboard.settings.dialogs.SettingsModalHost
import app.monitor.feature.dashboard.settings.model.*
import app.monitor.feature.dashboard.settings.panels.*

@Composable
fun SettingsScreen(
    uiState: DashboardUiState,
    onPairDevice: () -> Unit,
    onBackClick: () -> Unit = {},
    tokenStorage: TokenStorage? = null,
    onSignOut: () -> Unit = onPairDevice,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context, tokenStorage))

    LaunchedEffect(uiState) { viewModel.syncDashboardState(uiState) }

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(if (isDarkTheme) Color(0xFF07080E) else Color.Transparent)) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            snackbarHost = {
                androidx.compose.material3.SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 96.dp)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 126.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { SettingsHeaderSection(state.pairing.isOnline, isDarkTheme, onToggleTheme) }

                // Panel 1: Account & Profile (Google Cloud Identity)
                item {
                    AccountProfilePanel(
                        state = state.account,
                        isExpanded = state.expandedPanels.contains(SettingsPanel.ACCOUNT),
                        onToggleExpand = { viewModel.togglePanel(SettingsPanel.ACCOUNT) },
                        onSignOutClick = { viewModel.openDialog(SettingsDialog.SignOutConfirm) }
                    )
                }

                // Panel 2: Station Device (Apple Dark Glass)
                item {
                    DeviceStationPanel(
                        state = state.pairing,
                        isExpanded = state.expandedPanels.contains(SettingsPanel.DEVICE),
                        onToggleExpand = { viewModel.togglePanel(SettingsPanel.DEVICE) },
                        onManagePairingClick = { viewModel.openDialog(SettingsDialog.ManagePairingSheet) },
                        onUnpairClick = { viewModel.openDialog(SettingsDialog.UnpairConfirm) }
                    )
                }

                // Panel 2: Cloudflare Relay
                item {
                    CloudflareBrokerSyncPanel(
                        state = state.broker,
                        isExpanded = state.expandedPanels.contains(SettingsPanel.BROKER_SYNC),
                        isCloudSyncEnabled = state.preferences.liveUpdatesEnabled,
                        onToggleExpand = { viewModel.togglePanel(SettingsPanel.BROKER_SYNC) },
                        onUrlChange = { viewModel.setBrokerUrl(it) },
                        onToggleCloudSync = { viewModel.setLiveUpdates(it) },
                        selectedFrequency = state.preferences.brokerSyncFrequency,
                        onFrequencySelected = { viewModel.setBrokerSyncFrequency(it) },
                        onPingClick = { viewModel.testBrokerPing() }
                    )
                }

                // Panel 3: Telemetry Engine
                item {
                    TelemetryPreferencesPanel(
                        preferences = state.preferences,
                        isExpanded = state.expandedPanels.contains(SettingsPanel.TELEMETRY_PREFS),
                        onToggleExpand = { viewModel.togglePanel(SettingsPanel.TELEMETRY_PREFS) },
                        onSamplingRateChange = { viewModel.setSamplingInterval(it) },
                        onTemperatureUnitChange = { viewModel.setTemperatureUnit(it) },
                        onProcessFilterChange = { viewModel.setProcessFilterMode(it) },
                        onShowFanChange = { viewModel.setShowFanImpeller(it) }
                    )
                }

                // Panel 4: Alerts & Thresholds
                item {
                    NotificationsThresholdsPanel(
                        preferences = state.preferences,
                        isExpanded = state.expandedPanels.contains(SettingsPanel.NOTIFICATIONS),
                        onToggleExpand = { viewModel.togglePanel(SettingsPanel.NOTIFICATIONS) },
                        onToggleMaster = { viewModel.setNotificationsEnabled(it) },
                        onCpuThresholdSelected = { viewModel.setCpuThreshold(it) },
                        onRamThresholdSelected = { viewModel.setRamThreshold(it) },
                        onBatteryThresholdSelected = { viewModel.setBatteryLowThreshold(it) },
                        onToggleBackgroundSync = { viewModel.setBackgroundSyncEnabled(it) }
                    )
                }

                // Panel 5: Biometric Security
                item {
                    BiometricSecurityPanel(
                        preferences = state.preferences,
                        securityState = state.security,
                        isExpanded = state.expandedPanels.contains(SettingsPanel.SECURITY),
                        onToggleExpand = { viewModel.togglePanel(SettingsPanel.SECURITY) },
                        onToggleAppLock = { enabled ->
                            requireBiometricConfirmation(
                                context, true,
                                if (enabled) "Enable App Lock" else "Disable App Lock",
                                "Verify identity with Face, Fingerprint, or PIN"
                            ) { tokenStorage?.setAppLockEnabled(enabled); viewModel.setAppLockEnabled(enabled) }
                        },
                        onToggleBiometricOnResume = { enabled ->
                            val locked = tokenStorage?.isAppLockEnabled() == true || state.preferences.appLockEnabled
                            requireBiometricConfirmation(context, !enabled && locked, "Disable Lock on Resume", "Verify identity before weakening lock policy") {
                                viewModel.setBiometricOnResume(enabled)
                            }
                        },
                        onToggleDestructiveGating = { enabled ->
                            val locked = tokenStorage?.isAppLockEnabled() == true || state.preferences.appLockEnabled
                            requireBiometricConfirmation(context, !enabled && locked, "Disable Destructive Gating", "Verify identity before removing confirmation on destructive operations") {
                                viewModel.setBiometricDestructiveOps(enabled)
                            }
                        },
                        onToggleRemoteTerminal = { enabled ->
                            val locked = tokenStorage?.isAppLockEnabled() == true || state.preferences.appLockEnabled
                            requireBiometricConfirmation(context, enabled && locked, "Enable Remote Terminal", "Verify identity to enable remote terminal execution") {
                                viewModel.setRemoteTerminalEnabled(enabled)
                            }
                        }
                    )
                }

                // Panel 6: Legal & Support
                item {
                    StatutoryLegalPanel(
                        legalState = state.legal,
                        isExpanded = state.expandedPanels.contains(SettingsPanel.LEGAL),
                        onToggleExpand = { viewModel.togglePanel(SettingsPanel.LEGAL) },
                        onOpenTerms = { viewModel.openDialog(SettingsDialog.TermsViewer) },
                        onOpenPrivacy = { viewModel.openDialog(SettingsDialog.PrivacyPolicyViewer) }
                    )
                }
            }
        }

        SettingsModalHost(
            activeDialog = state.activeDialog, uiState = state, tokenStorage = tokenStorage,
            onDismiss = { viewModel.dismissDialog() },
            onConfirmSignOut = { viewModel.revokeSessionAndSignOut(onSignOut) },
            onConfirmUnpair = { viewModel.revokeSessionAndSignOut(onPairDevice) },
            onSelectTheme = { mode -> viewModel.setThemeMode(mode); onToggleTheme() },
            onSelectSampling = { viewModel.setSamplingInterval(it) },
            onSelectTemperature = { viewModel.setTemperatureUnit(it) },
            onSelectFilter = { viewModel.setProcessFilterMode(it) },
            onSelectFrequency = { viewModel.setBrokerSyncFrequency(it) },
            onManageUnpairRequested = { viewModel.openDialog(SettingsDialog.UnpairConfirm) }
        )
    }
}
