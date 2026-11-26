package app.monitor.feature.dashboard

import app.monitor.core.common.BrandConfig

import app.monitor.core.model.BatterySnapshot
import app.monitor.core.model.CpuSnapshot
import app.monitor.core.model.MemorySnapshot
import app.monitor.core.model.RepoSummary
import app.monitor.core.model.ServerEvent
import app.monitor.core.model.SystemSnapshot
import app.monitor.core.network.DashboardConnection
import app.monitor.core.network.MonitorApiException
import app.monitor.feature.dashboard.settings.SettingsViewModel
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tier 5 White-Box Adversarial Hardening Suite for Android Client (:feature:dashboard).
 *
 * Empirically stress-tests:
 * 1. Exponential reconnect backoff jitter limits and zero-stampede bounds.
 * 2. Extreme notification threshold clamping [0, 100].
 * 3. Token expiration (401) during live telemetry and state recovery.
 * 4. Transient network failure recovery without unpairing.
 * 5. Clean empty states without synthetic or mock fallbacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Tier5AndroidAdversarialHardeningTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testReconnectDelayCalculationJitterAndCeiling() {
        // Attempt 0: range [500ms, 1000ms]
        val delay0Min = reconnectDelayMillis(0, 0.0)
        val delay0Max = reconnectDelayMillis(0, 1.0)
        assertEquals(500L, delay0Min)
        assertEquals(1000L, delay0Max)

        // Attempt 4+: capped at ceiling 15,000ms -> range [7500ms, 15000ms]
        val delay4Min = reconnectDelayMillis(4, 0.0)
        val delay4Max = reconnectDelayMillis(4, 1.0)
        assertEquals(7500L, delay4Min)
        assertEquals(15000L, delay4Max)

        // Extreme attempts (e.g. 100) must never overflow or exceed 15,000ms
        val delayExtremeMin = reconnectDelayMillis(100, 0.0)
        val delayExtremeMax = reconnectDelayMillis(100, 1.0)
        assertEquals(7500L, delayExtremeMin)
        assertEquals(15000L, delayExtremeMax)

        // Negative attempt must clamp to 0
        assertEquals(500L, reconnectDelayMillis(-5, 0.0))
        assertEquals(1000L, reconnectDelayMillis(-5, 1.0))
    }

    @Test
    fun testSettingsExtremeNotificationThresholdClamping() = runTest(dispatcher) {
        val repo = Tier5StubSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repo,
            tokenStorage = null,
            isBiometricAvailable = true,
            installedVersion = "v${BrandConfig.APP_VERSION}",
            ioDispatcher = dispatcher
        )
        runCurrent()

        // Negative boundary clamping
        viewModel.setCpuThreshold(-100)
        viewModel.setRamThreshold(-999)
        viewModel.setBatteryLowThreshold(-1)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.preferences.cpuThresholdPercent)
        assertEquals(0, viewModel.uiState.value.preferences.ramThresholdPercent)
        assertEquals(0, viewModel.uiState.value.preferences.batteryLowThresholdPercent)

        // Overflow boundary clamping
        viewModel.setCpuThreshold(101)
        viewModel.setRamThreshold(50000)
        viewModel.setBatteryLowThreshold(1000)
        runCurrent()
        assertEquals(100, viewModel.uiState.value.preferences.cpuThresholdPercent)
        assertEquals(100, viewModel.uiState.value.preferences.ramThresholdPercent)
        assertEquals(100, viewModel.uiState.value.preferences.batteryLowThresholdPercent)
    }

    @Test
    fun testTokenExpiration401StopsReconnectionAndDemandsPairing() = runTest(dispatcher) {
        val connection = Tier5FakeDashboardConnection().apply {
            ticketError = MonitorApiException(401, "Pairing token revoked")
            snapshotResult = Result.failure(MonitorApiException(401, "Pairing token revoked"))
        }
        val dashboard = DashboardViewModel(connection)
        dashboard.start()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()

        assertTrue(dashboard.uiState.value.requiresPairing)
        assertFalse(dashboard.uiState.value.isOnline)
        assertEquals("Pairing token revoked", dashboard.uiState.value.errorMessage)
        dashboard.stop()
    }

    @Test
    fun testTransientNetworkFailureRecoversTelemetryWithoutPairing() = runTest(dispatcher) {
        val connection = Tier5FakeDashboardConnection().apply {
            transientFailures = 1
        }
        val dashboard = DashboardViewModel(connection)
        dashboard.start()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        connection.events.emit(ServerEvent.TelemetryTick(sampleSnapshot))
        runCurrent()

        assertTrue(dashboard.uiState.value.isOnline)
        assertFalse(dashboard.uiState.value.requiresPairing)
        assertEquals(sampleSnapshot, dashboard.uiState.value.systemSnapshot)
        dashboard.stop()
    }

    @Test
    fun testEmptyDataSourcesNeverSurfaceMockOrDummyData() = runTest(dispatcher) {
        val connection = Tier5FakeDashboardConnection().apply {
            snapshotResult = Result.success(sampleSnapshot.copy(cooling = null, disks = emptyList()))
            projectsResult = Result.success(emptyList())
        }
        val dashboard = DashboardViewModel(connection)
        dashboard.loadDashboardData()
        runCurrent()

        val state = dashboard.uiState.value
        assertNotNull(state.systemSnapshot)
        assertNull(state.systemSnapshot?.cooling)
        assertTrue(state.systemSnapshot?.disks.orEmpty().isEmpty())
        assertTrue(state.projects.isEmpty())
        dashboard.stop()
    }

    private class Tier5FakeDashboardConnection : DashboardConnection {
        val events = MutableSharedFlow<ServerEvent>()
        var ticketRequests = 0
        var ticketError: Exception? = null
        var transientFailures = 0
        var snapshotResult: Result<SystemSnapshot> = Result.failure(IOException("Offline"))
        var projectsResult: Result<List<RepoSummary>> = Result.success(emptyList())

        override suspend fun acquireWsTicket(): Result<String> {
            ticketRequests++
            return ticketError?.let { Result.failure(it) } ?: Result.success("ticket-$ticketRequests")
        }

        override suspend fun getSystemSnapshot(): Result<SystemSnapshot> = snapshotResult
        override suspend fun getProjects(): Result<List<RepoSummary>> = projectsResult

        override fun streamServerEvents(ticket: String): Flow<ServerEvent> = flow {
            if (transientFailures-- > 0) throw IOException("Network timeout during stream")
            events.collect { emit(it) }
        }
    }

    private class Tier5StubSettingsRepository : SettingsRepository {
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

    private val sampleSnapshot = SystemSnapshot(
        schemaVersion = 3,
        observedAtUtcMs = 1700000000000L,
        hostname = "workstation-adversarial",
        agentVersion = "1.0.4",
        uptimeSeconds = 3600,
        battery = BatterySnapshot(hasBattery = false),
        cpu = CpuSnapshot(totalUsagePercent = 22.5f, frequencyMhz = 3600),
        ram = MemorySnapshot(totalBytes = 32000000000L, usedBytes = 16000000000L, availableBytes = 16000000000L, usagePercent = 50.0f)
    )
}
