package app.monitor.feature.dashboard.settings.dialogs

import androidx.compose.runtime.Composable
import app.monitor.core.network.TokenStorage
import app.monitor.feature.dashboard.dialogs.AboutAppDialog
import app.monitor.feature.dashboard.dialogs.AppearanceDialog
import app.monitor.feature.dashboard.dialogs.ManagePairingBottomSheet
import app.monitor.feature.dashboard.dialogs.PrivacyPolicyDialog
import app.monitor.feature.dashboard.dialogs.ProfileBottomSheet
import app.monitor.feature.dashboard.dialogs.SignOutConfirmationDialog
import app.monitor.feature.dashboard.dialogs.TermsOfServiceDialog
import app.monitor.feature.dashboard.dialogs.UnpairConfirmationDialog
import app.monitor.feature.dashboard.settings.model.BrokerSyncFrequency
import app.monitor.feature.dashboard.settings.model.ProcessFilterMode
import app.monitor.feature.dashboard.settings.model.SamplingInterval
import app.monitor.feature.dashboard.settings.model.SettingsDialog
import app.monitor.feature.dashboard.settings.model.SettingsUiState
import app.monitor.feature.dashboard.settings.model.TemperatureUnit
import app.monitor.feature.dashboard.settings.model.ThemeMode

@Composable
fun SettingsModalHost(
    activeDialog: SettingsDialog?,
    uiState: SettingsUiState,
    tokenStorage: TokenStorage?,
    onDismiss: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onConfirmUnpair: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectSampling: (SamplingInterval) -> Unit,
    onSelectTemperature: (TemperatureUnit) -> Unit,
    onSelectFilter: (ProcessFilterMode) -> Unit,
    onSelectFrequency: (BrokerSyncFrequency) -> Unit,
    onManageUnpairRequested: () -> Unit
) {
    when (activeDialog) {
        null -> Unit
        SettingsDialog.SignOutConfirm -> {
            SignOutConfirmationDialog(
                onConfirm = {
                    onDismiss()
                    onConfirmSignOut()
                },
                onDismiss = onDismiss
            )
        }
        SettingsDialog.UnpairConfirm -> {
            UnpairConfirmationDialog(
                hostname = uiState.pairing.hostname,
                onConfirm = {
                    onDismiss()
                    onConfirmUnpair()
                },
                onDismiss = onDismiss
            )
        }
        SettingsDialog.ProfileDetailsSheet -> {
            ProfileBottomSheet(
                hasGoogleAuth = uiState.account.hasGoogleAuth,
                displayName = uiState.account.displayName,
                userEmail = uiState.account.userEmail,
                tokenStorage = tokenStorage,
                onDismiss = onDismiss
            )
        }
        SettingsDialog.ManagePairingSheet -> {
            ManagePairingBottomSheet(
                hostname = uiState.pairing.hostname,
                uiState = app.monitor.feature.dashboard.DashboardUiState(
                    isOnline = uiState.pairing.isOnline,
                    permissions = uiState.pairing.permissions,
                    activeRoute = uiState.pairing.connectionType
                ),
                tokenStorage = tokenStorage,
                onUnpairClick = {
                    onDismiss()
                    onManageUnpairRequested()
                },
                onDismiss = onDismiss
            )
        }
        SettingsDialog.AppearancePicker -> {
            AppearanceDialog(
                selectedThemeName = uiState.preferences.themeMode.title,
                onThemeSelect = { name ->
                    val mode = ThemeMode.entries.find { it.title == name } ?: ThemeMode.SYSTEM_DEFAULT
                    onSelectTheme(mode)
                },
                onDismiss = onDismiss
            )
        }
        SettingsDialog.TermsViewer -> {
            TermsOfServiceDialog(onDismiss = onDismiss)
        }
        SettingsDialog.PrivacyPolicyViewer -> {
            PrivacyPolicyDialog(onDismiss = onDismiss)
        }
        SettingsDialog.AboutAppDialog -> {
            AboutAppDialog(
                installedVersion = uiState.legal.appVersion,
                onDismiss = onDismiss
            )
        }
        SettingsDialog.SamplingIntervalPicker -> {
            // Dismissed by direct selection in panel
            onDismiss()
        }
        SettingsDialog.TemperatureUnitPicker -> {
            // Dismissed by direct selection in panel
            onDismiss()
        }
        SettingsDialog.ProcessFilterPicker -> {
            // Dismissed by direct selection in panel
            onDismiss()
        }
        SettingsDialog.BrokerFrequencyPicker -> {
            // Dismissed by direct selection in panel
            onDismiss()
        }
    }
}
