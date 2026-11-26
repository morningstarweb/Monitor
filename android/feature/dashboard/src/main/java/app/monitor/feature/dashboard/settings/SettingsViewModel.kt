package app.monitor.feature.dashboard.settings

import android.content.Context
import androidx.lifecycle.*
import app.monitor.core.common.BrandConfig
import app.monitor.core.network.MonitorApiClient
import app.monitor.core.network.TokenStorage
import app.monitor.core.network.setTerminalSettingApi
import app.monitor.feature.dashboard.DashboardUiState
import app.monitor.feature.dashboard.settings.data.*
import app.monitor.feature.dashboard.settings.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val tokenStorage: TokenStorage?,
    isBiometricAvailable: Boolean = true,
    installedVersion: String = "v${BrandConfig.APP_VERSION}",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val brokerProbeClient: IBrokerProbeClient = BrokerProbeClient
) : ViewModel() {

    private val sharedApiClient by lazy { tokenStorage?.let(::MonitorApiClient) }

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            security = SecuritySettingsState(isBiometricHardwareAvailable = isBiometricAvailable),
            legal = StatutoryLegalState(appVersion = installedVersion)
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferencesFlow.collect { prefs ->
                _uiState.update { current ->
                    val hasGoogle = tokenStorage?.getCloudSessionToken() != null
                    val userEmail = tokenStorage?.getUserEmail()
                    val userName = tokenStorage?.getUserName()
                    val photoUrl = tokenStorage?.getUserPicture()
                    val displayName = when {
                        !userName.isNullOrBlank() -> userName
                        !userEmail.isNullOrBlank() -> userEmail.substringBefore("@")
                        else -> "Desktop Developer"
                    }
                    current.copy(
                        preferences = prefs,
                        account = current.account.copy(displayName = displayName, userEmail = userEmail, photoUrl = photoUrl, hasGoogleAuth = hasGoogle, isSessionActive = tokenStorage?.isAuthenticated() == true),
                        security = current.security.copy(isAppLockActive = prefs.appLockEnabled, requireOnResume = prefs.biometricPromptOnResume, gateDestructiveOps = prefs.biometricGatingDestructiveOps),
                        broker = current.broker.copy(brokerUrl = prefs.brokerUrl)
                    )
                }
            }
        }
    }

    fun syncDashboardState(dash: DashboardUiState) {
        _uiState.update { state ->
            val host = dash.systemSnapshot?.hostname?.takeIf { it.isNotBlank() } ?: tokenStorage?.getServerHost()?.takeIf { it.isNotBlank() } ?: "Desktop Workstation"
            val isCloud = dash.activeRoute.contains("Cloud", ignoreCase = true)
            state.copy(
                pairing = state.pairing.copy(
                    hostname = host,
                    serverHost = if (isCloud) "your-monitor-broker.workers.dev" else (tokenStorage?.getServerHost()?.takeIf { it.isNotBlank() } ?: "127.0.0.1"),
                    serverPort = if (isCloud) 443 else (tokenStorage?.getServerPort() ?: 9480),
                    isOnline = dash.isOnline,
                    permissions = dash.permissions,
                    connectionType = dash.activeRoute
                ),
                broker = state.broker
            )
        }
    }

    fun togglePanel(panel: SettingsPanel) {
        _uiState.update { state ->
            val exp = state.expandedPanels
            state.copy(expandedPanels = if (exp.contains(panel)) exp - panel else exp + panel)
        }
    }

    fun openDialog(dialog: SettingsDialog) = _uiState.update { it.copy(activeDialog = dialog) }
    fun dismissDialog() = _uiState.update { it.copy(activeDialog = null) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.updateThemeMode(mode); dismissDialog() }
    fun setLiveUpdates(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateLiveUpdates(enabled) }
    fun setSamplingInterval(interval: SamplingInterval) = viewModelScope.launch { settingsRepository.updateSamplingInterval(interval) }
    fun setTemperatureUnit(unit: TemperatureUnit) = viewModelScope.launch { settingsRepository.updateTemperatureUnit(unit) }
    fun setProcessFilterMode(mode: ProcessFilterMode) = viewModelScope.launch { settingsRepository.updateProcessFilterMode(mode) }
    fun setShowFanImpeller(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFanImpeller(show) }
    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateNotifications(enabled) }
    fun setCpuThreshold(percent: Int) = viewModelScope.launch { settingsRepository.updateCpuThreshold(percent.coerceIn(0, 100)) }
    fun setRamThreshold(percent: Int) = viewModelScope.launch { settingsRepository.updateRamThreshold(percent.coerceIn(0, 100)) }
    fun setBatteryLowThreshold(percent: Int) = viewModelScope.launch { settingsRepository.updateBatteryLowThreshold(percent.coerceIn(0, 100)) }
    fun setBackgroundSyncEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateBackgroundSync(enabled) }
    fun setBrokerSyncFrequency(freq: BrokerSyncFrequency) = viewModelScope.launch { settingsRepository.updateBrokerSyncFrequency(freq) }
    // NEW-07: never persist an endpoint that could receive a Bearer token over
    // plaintext or that parses to a deceptive/malformed URL.
    fun setBrokerUrl(url: String) = viewModelScope.launch {
        val trimmed = url.trim()
        _uiState.update { it.copy(broker = it.broker.copy(brokerUrl = trimmed)) }
        val validated = runCatching { validateBrokerUrl(trimmed) }.getOrNull()
        if (validated == null) {
            _uiState.update { it.copy(broker = it.broker.copy(isSyncing = false, healthTier = BrokerHealthTier.Offline, errorMessage = "Broker URL must be a valid https:// endpoint")) }
            return@launch
        }
        settingsRepository.updateBrokerUrl(validated)
    }
    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateAppLock(enabled) }
    fun setBiometricOnResume(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateBiometricOnResume(enabled) }
    fun setBiometricDestructiveOps(enabled: Boolean) = viewModelScope.launch {
        tokenStorage?.setDestructiveOpsBiometricGated(enabled)
        settingsRepository.updateBiometricDestructiveOps(enabled)
    }
    fun setRemoteTerminalEnabled(enabled: Boolean) = viewModelScope.launch {
        val prev = _uiState.value.preferences.remoteTerminalEnabled
        settingsRepository.updateRemoteTerminal(enabled)
        val client = sharedApiClient ?: return@launch
        val res = withContext(ioDispatcher) { runCatching { client.setTerminalSettingApi(enabled) } }
        val apiFailure = res.exceptionOrNull() ?: res.getOrNull()?.exceptionOrNull()
        if (apiFailure != null) {
            settingsRepository.updateRemoteTerminal(prev)
            _uiState.update {
                it.copy(snackbarMessage = "Desktop host policy forbids remote terminal toggle. Change it in desktop tray.")
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        sharedApiClient?.close()
        super.onCleared()
    }
    fun testBrokerPing() = viewModelScope.launch {
        val rawUrl = uiState.value.broker.brokerUrl
        val url = rawUrl.trim()
        val parsed = runCatching { validateBrokerUrl(url) }.getOrNull()
        val isBroken = parsed == null || url.contains(".invalid", ignoreCase = true)
        if (isBroken) {
            _uiState.update { state ->
                state.copy(broker = state.broker.copy(isSyncing = false, healthTier = BrokerHealthTier.Offline, errorMessage = "Invalid or unreachable broker URL: $url"))
            }
            return@launch
        }

        _uiState.update { it.copy(broker = it.broker.copy(isSyncing = true, errorMessage = null)) }
        val res = withContext(ioDispatcher) { brokerProbeClient.ping(url) }
        _uiState.update { state ->
            res.fold(
                onSuccess = { probe ->
                    val tier = when {
                        !probe.isHealthy -> BrokerHealthTier.Offline
                        probe.latencyMs < 120 -> BrokerHealthTier.Optimal
                        probe.latencyMs < 300 -> BrokerHealthTier.Moderate
                        else -> BrokerHealthTier.Degraded
                    }
                    state.copy(broker = state.broker.copy(isSyncing = false, latencyMs = probe.latencyMs, edgeColo = probe.edgeColo, healthTier = tier, lastSyncUtcMs = System.currentTimeMillis()))
                },
                onFailure = { err ->
                    state.copy(broker = state.broker.copy(isSyncing = false, healthTier = BrokerHealthTier.Offline, errorMessage = err.message ?: "Ping failed"))
                }
            )
        }
    }

    fun revokeSessionAndSignOut(onSignedOut: () -> Unit) = viewModelScope.launch {
        _uiState.update { it.copy(account = it.account.copy(isRevokingSession = true)) }
        val token = tokenStorage?.getCloudSessionToken() ?: tokenStorage?.getDeviceToken()
        val broker = uiState.value.broker.brokerUrl
        if (!token.isNullOrBlank()) {
            withContext(ioDispatcher) { brokerProbeClient.logoutSession(broker, token) }
        }
        tokenStorage?.clearAccountData()
        _uiState.update { it.copy(account = it.account.copy(isRevokingSession = false)) }
        onSignedOut()
    }
}

class SettingsViewModelFactory(
    private val context: Context,
    private val tokenStorage: TokenStorage?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val repo = ReactiveSettingsRepository(context.applicationContext)
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()?.let { "v$it" } ?: "v${BrandConfig.APP_VERSION}"
            val bioAvail = app.monitor.core.common.BiometricAuthManager.isBiometricAvailable(context)
            return modelClass.cast(SettingsViewModel(repo, tokenStorage, isBiometricAvailable = bioAvail, installedVersion = version))
                ?: throw IllegalArgumentException("Unable to cast ViewModel to ${modelClass.name}")
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
