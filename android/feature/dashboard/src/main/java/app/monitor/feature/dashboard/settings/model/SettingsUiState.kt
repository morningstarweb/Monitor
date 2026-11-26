package app.monitor.feature.dashboard.settings.model

import app.monitor.core.common.BrandConfig

import app.monitor.core.model.PermissionsResponse

data class AccountProfileState(
    val displayName: String = "Desktop Developer",
    val userEmail: String? = null,
    val photoUrl: String? = null,
    val hasGoogleAuth: Boolean = false,
    val isSessionActive: Boolean = false,
    val authProviderName: String = "Direct Encrypted Keystore Token",
    val roleName: String = "Operator",
    val daysUntilExpiration: Int? = null,
    val isRevokingSession: Boolean = false
)

data class DevicePairingState(
    val hostname: String = "Desktop Workstation",
    val serverHost: String = "127.0.0.1",
    val serverPort: Int = 9480,
    val isOnline: Boolean = false,
    val permissions: PermissionsResponse? = null,
    val connectionType: String = "Direct LAN Stream"
)

enum class BrokerHealthTier {
    Optimal,
    Moderate,
    Degraded,
    Offline,
    Idle
}

data class BrokerSyncState(
    val brokerUrl: String = "https://your-monitor-broker.workers.dev",
    val latencyMs: Long? = null,
    val edgeColo: String? = null,
    val healthTier: BrokerHealthTier = BrokerHealthTier.Idle,
    val lastSyncUtcMs: Long? = null,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

data class SecuritySettingsState(
    val isBiometricHardwareAvailable: Boolean = true,
    val isAppLockActive: Boolean = false,
    val requireOnResume: Boolean = false,
    val gateDestructiveOps: Boolean = true
)

data class StatutoryLegalState(
    val studioEntity: String = "Morning Star Web",
    val headquarters: String = "Kolkata, West Bengal, India",
    val appVersion: String = "v${BrandConfig.APP_VERSION}",
    val statutoryCompliance: String = "India DPDP Act 2023 / EU GDPR Art. 5",
    val grievanceEmail: String = "contact@morningstarweb.space",
    val grievancePhone: String = "+91 78649 23844"
)

data class SettingsUiState(
    val preferences: SettingsPreferences = SettingsPreferences(),
    val account: AccountProfileState = AccountProfileState(),
    val pairing: DevicePairingState = DevicePairingState(),
    val broker: BrokerSyncState = BrokerSyncState(),
    val security: SecuritySettingsState = SecuritySettingsState(),
    val legal: StatutoryLegalState = StatutoryLegalState(),
    val expandedPanels: Set<SettingsPanel> = setOf(SettingsPanel.DEVICE, SettingsPanel.ACCOUNT, SettingsPanel.BROKER_SYNC),
    val activeDialog: SettingsDialog? = null,
    val snackbarMessage: String? = null
)
