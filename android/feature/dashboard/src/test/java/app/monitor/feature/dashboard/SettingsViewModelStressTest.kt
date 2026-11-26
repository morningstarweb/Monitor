package app.monitor.feature.dashboard

import app.monitor.core.common.BrandConfig

import app.monitor.feature.dashboard.settings.SettingsViewModel
import app.monitor.feature.dashboard.settings.data.BrokerProbeClient
import app.monitor.feature.dashboard.settings.data.SettingsRepository
import app.monitor.feature.dashboard.settings.model.BrokerHealthTier
import app.monitor.feature.dashboard.settings.model.BrokerSyncFrequency
import app.monitor.feature.dashboard.settings.model.ProcessFilterMode
import app.monitor.feature.dashboard.settings.model.SamplingInterval
import app.monitor.feature.dashboard.settings.model.SettingsPreferences
import app.monitor.feature.dashboard.settings.model.TemperatureUnit
import app.monitor.feature.dashboard.settings.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelStressTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: StressTestSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = StressTestSettingsRepository()
        viewModel = SettingsViewModel(
            settingsRepository = repo,
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
    fun rapidConcurrentPreferenceUpdates_remainsThreadSafeAndConsistent() = runTest(dispatcher) {
        runCurrent()
        val jobs = (0 until 100).map { i ->
            async {
                viewModel.setCpuThreshold(i % 100)
                viewModel.setRamThreshold(i % 100)
                viewModel.setBatteryLowThreshold(i % 30)
                viewModel.setSamplingInterval(if (i % 2 == 0) SamplingInterval.FAST_1S else SamplingInterval.ECO_5S)
                viewModel.setTemperatureUnit(if (i % 2 == 0) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT)
                viewModel.setAppLockEnabled(i % 2 == 0)
                viewModel.setBrokerUrl("https://edge-$i.monitor.internal")
            }
        }
        jobs.awaitAll()
        runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.preferences)
        assertTrue(state.preferences.cpuThresholdPercent in 0..100)
        assertTrue(state.preferences.ramThresholdPercent in 0..100)
        assertTrue(state.preferences.brokerUrl.startsWith("https://edge-"))
    }

    @Test
    fun extremeThresholdValues_handledGracefullyWithoutCrash() = runTest(dispatcher) {
        runCurrent()
        viewModel.setCpuThreshold(0)
        viewModel.setRamThreshold(0)
        viewModel.setBatteryLowThreshold(0)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.preferences.cpuThresholdPercent)
        assertEquals(0, viewModel.uiState.value.preferences.ramThresholdPercent)
        assertEquals(0, viewModel.uiState.value.preferences.batteryLowThresholdPercent)

        viewModel.setCpuThreshold(100)
        viewModel.setRamThreshold(100)
        viewModel.setBatteryLowThreshold(100)
        runCurrent()
        assertEquals(100, viewModel.uiState.value.preferences.cpuThresholdPercent)
        assertEquals(100, viewModel.uiState.value.preferences.ramThresholdPercent)
        assertEquals(100, viewModel.uiState.value.preferences.batteryLowThresholdPercent)

        viewModel.setCpuThreshold(-50)
        viewModel.setRamThreshold(-1)
        viewModel.setBatteryLowThreshold(9999)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.preferences.cpuThresholdPercent)
        assertEquals(0, viewModel.uiState.value.preferences.ramThresholdPercent)
        assertEquals(100, viewModel.uiState.value.preferences.batteryLowThresholdPercent)
    }

    @Test
    fun brokenBrokerUrls_pingFailsGracefullyWithoutThrowing() = runTest(dispatcher) {
        val brokenUrls = listOf(
            "",
            "invalid_not_a_url",
            "://malformed",
            "http://127.0.0.1:59999",
            "ftp://unsupported.scheme"
        )
        for (url in brokenUrls) {
            val res = BrokerProbeClient.ping(url)
            assertTrue("Expected failure for broken URL: $url", res.isFailure)
        }
    }

    @Test
    fun viewModelBrokerPingWithBrokenUrl_setsHealthTierToOffline() = runTest(dispatcher) {
        runCurrent()
        viewModel.setBrokerUrl("http://unreachable.localhost.invalid:59999")
        runCurrent()
        viewModel.testBrokerPing()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.broker.isSyncing)
        assertEquals(BrokerHealthTier.Offline, state.broker.healthTier)
        assertNotNull(state.broker.errorMessage)
    }

    @Test
    fun sessionRevocationWithUnreachableBroker_stillClearsAndInvokesCallback() = runTest(dispatcher) {
        runCurrent()
        viewModel.setBrokerUrl("http://127.0.0.1:59999")
        runCurrent()
        var callbackInvoked = false
        viewModel.revokeSessionAndSignOut { callbackInvoked = true }
        runCurrent()

        assertTrue(callbackInvoked)
        assertFalse(viewModel.uiState.value.account.isRevokingSession)
    }

    @Test
    fun biometricNoHardwareSensor_reflectsInUiStateGracefully() = runTest(dispatcher) {
        val noBioVm = SettingsViewModel(
            settingsRepository = repo,
            tokenStorage = null,
            isBiometricAvailable = false,
            installedVersion = "v${BrandConfig.APP_VERSION}",
            ioDispatcher = dispatcher
        )
        runCurrent()
        assertFalse(noBioVm.uiState.value.security.isBiometricHardwareAvailable)
        noBioVm.setAppLockEnabled(true)
        runCurrent()
        assertTrue(noBioVm.uiState.value.security.isAppLockActive)
    }

    private class StressTestSettingsRepository : SettingsRepository {
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
