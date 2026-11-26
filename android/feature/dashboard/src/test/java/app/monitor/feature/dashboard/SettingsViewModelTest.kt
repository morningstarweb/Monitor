package app.monitor.feature.dashboard

import app.monitor.core.common.BrandConfig

import app.monitor.feature.dashboard.settings.SettingsViewModel
import app.monitor.feature.dashboard.settings.data.SettingsRepository
import app.monitor.feature.dashboard.settings.model.BrokerHealthTier
import app.monitor.feature.dashboard.settings.model.BrokerSyncFrequency
import app.monitor.feature.dashboard.settings.model.ProcessFilterMode
import app.monitor.feature.dashboard.settings.model.SamplingInterval
import app.monitor.feature.dashboard.settings.model.SettingsDialog
import app.monitor.feature.dashboard.settings.model.SettingsPanel
import app.monitor.feature.dashboard.settings.model.SettingsPreferences
import app.monitor.feature.dashboard.settings.model.TemperatureUnit
import app.monitor.feature.dashboard.settings.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeRepo = FakeSettingsRepository()
        viewModel = SettingsViewModel(
            settingsRepository = fakeRepo,
            tokenStorage = null,
            isBiometricAvailable = true,
            installedVersion = "v${BrandConfig.APP_VERSION}",
            ioDispatcher = dispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateReflectsDefaultPreferences() = runTest(dispatcher) {
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(SamplingInterval.FAST_1S, state.preferences.samplingInterval)
        assertEquals(TemperatureUnit.CELSIUS, state.preferences.temperatureUnit)
        assertEquals(85, state.preferences.cpuThresholdPercent)
        assertEquals("Morning Star Web", state.legal.studioEntity)
        assertEquals("v${BrandConfig.APP_VERSION}", state.legal.appVersion)
        assertTrue(state.security.isBiometricHardwareAvailable)
    }

    @Test
    fun initialBrokerState_defaultsToStandbyWithCleanTelemetry() = runTest(dispatcher) {
        runCurrent()
        val state = viewModel.uiState.value
        assertNull(state.broker.latencyMs)
        assertNull(state.broker.edgeColo)
        assertEquals(BrokerHealthTier.Idle, state.broker.healthTier)
        assertNull(state.broker.lastSyncUtcMs)
        assertFalse(state.broker.isSyncing)
        assertNull(state.account.daysUntilExpiration)
        assertFalse(state.account.isSessionActive)
    }

    @Test
    fun togglePanelExpandsAndCollapsesCorrectly() = runTest(dispatcher) {
        runCurrent()
        assertTrue(viewModel.uiState.value.expandedPanels.contains(SettingsPanel.ACCOUNT))
        viewModel.togglePanel(SettingsPanel.ACCOUNT)
        assertFalse(viewModel.uiState.value.expandedPanels.contains(SettingsPanel.ACCOUNT))
        viewModel.togglePanel(SettingsPanel.ACCOUNT)
        assertTrue(viewModel.uiState.value.expandedPanels.contains(SettingsPanel.ACCOUNT))
    }

    @Test
    fun dialogLifecycleManagesActiveDialogState() = runTest(dispatcher) {
        runCurrent()
        assertNull(viewModel.uiState.value.activeDialog)
        viewModel.openDialog(SettingsDialog.SignOutConfirm)
        assertEquals(SettingsDialog.SignOutConfirm, viewModel.uiState.value.activeDialog)
        viewModel.dismissDialog()
        assertNull(viewModel.uiState.value.activeDialog)
    }

    @Test
    fun updatingPreferencesPropagatesThroughFlow() = runTest(dispatcher) {
        runCurrent()
        viewModel.setSamplingInterval(SamplingInterval.ECO_5S)
        runCurrent()
        assertEquals(SamplingInterval.ECO_5S, viewModel.uiState.value.preferences.samplingInterval)

        viewModel.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        runCurrent()
        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.uiState.value.preferences.temperatureUnit)

        viewModel.setCpuThreshold(70)
        runCurrent()
        assertEquals(70, viewModel.uiState.value.preferences.cpuThresholdPercent)

        viewModel.setAppLockEnabled(true)
        runCurrent()
        assertTrue(viewModel.uiState.value.preferences.appLockEnabled)
        assertTrue(viewModel.uiState.value.security.isAppLockActive)
    }

    @Test
    fun syncDashboardStateUpdatesHostAndOnlineStatus() = runTest(dispatcher) {
        runCurrent()
        val dash = DashboardUiState(isOnline = true)
        viewModel.syncDashboardState(dash)
        assertTrue(viewModel.uiState.value.pairing.isOnline)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SettingsPreferences())
        override val preferencesFlow: Flow<SettingsPreferences> = state.asStateFlow()

        override suspend fun updateThemeMode(mode: ThemeMode) { state.update { it.copy(themeMode = mode) } }
        override suspend fun updateLiveUpdates(enabled: Boolean) { state.update { it.copy(liveUpdatesEnabled = enabled) } }
        override suspend fun updateSamplingInterval(interval: SamplingInterval) { state.update { it.copy(samplingInterval = interval) } }
        override suspend fun updateTemperatureUnit(unit: TemperatureUnit) { state.update { it.copy(temperatureUnit = unit) } }
        override suspend fun updateProcessFilterMode(mode: ProcessFilterMode) { state.update { it.copy(processFilterMode = mode) } }
        override suspend fun updateShowFanImpeller(show: Boolean) { state.update { it.copy(showFanImpeller = show) } }
        override suspend fun updateNotifications(enabled: Boolean) { state.update { it.copy(notificationsEnabled = enabled) } }
        override suspend fun updateCpuThreshold(threshold: Int) { state.update { it.copy(cpuThresholdPercent = threshold.coerceIn(0, 100)) } }
        override suspend fun updateRamThreshold(threshold: Int) { state.update { it.copy(ramThresholdPercent = threshold.coerceIn(0, 100)) } }
        override suspend fun updateBatteryLowThreshold(threshold: Int) { state.update { it.copy(batteryLowThresholdPercent = threshold.coerceIn(0, 100)) } }
        override suspend fun updateBackgroundSync(enabled: Boolean) { state.update { it.copy(backgroundSyncEnabled = enabled) } }
        override suspend fun updateGitActivityAlerts(enabled: Boolean) { state.update { it.copy(gitActivityAlertsEnabled = enabled) } }
        override suspend fun updateBrokerSyncFrequency(frequency: BrokerSyncFrequency) { state.update { it.copy(brokerSyncFrequency = frequency) } }
        override suspend fun updateAutoReconnect(enabled: Boolean) { state.update { it.copy(autoReconnectEnabled = enabled) } }
        override suspend fun updateAppLock(enabled: Boolean) { state.update { it.copy(appLockEnabled = enabled) } }
        override suspend fun updateBiometricOnResume(enabled: Boolean) { state.update { it.copy(biometricPromptOnResume = enabled) } }
        override suspend fun updateBiometricDestructiveOps(enabled: Boolean) { state.update { it.copy(biometricGatingDestructiveOps = enabled) } }
        override suspend fun updateBrokerUrl(url: String) { state.update { it.copy(brokerUrl = url) } }
        override suspend fun resetDefaults() { state.value = SettingsPreferences() }
    }
}
