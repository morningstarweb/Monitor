package app.monitor.feature.dashboard

import app.monitor.core.model.BatterySnapshot
import app.monitor.core.model.CpuSnapshot
import app.monitor.core.model.MemorySnapshot
import app.monitor.core.model.RepoSummary
import app.monitor.core.model.ServerEvent
import app.monitor.core.model.SystemSnapshot
import app.monitor.core.network.DashboardConnection
import app.monitor.core.network.MonitorApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardConnectionTest {
    private val dispatcher = StandardTestDispatcher()
    private var model: DashboardViewModel? = null

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { model?.stop(); Dispatchers.resetMain() }

    @Test
    fun repeatedStartAndNetworkReconnectKeepExactlyOneCollector() = runTest(dispatcher) {
        val connection = FakeConnection()
        val dashboard = DashboardViewModel(connection).also { model = it }
        dashboard.start()
        dashboard.start()
        runCurrent()
        assertEquals(1, connection.events.subscriptionCount.value)
        dashboard.reconnect()
        runCurrent()
        assertEquals(1, connection.events.subscriptionCount.value)
        dashboard.stop()
        runCurrent()
        assertEquals(0, connection.events.subscriptionCount.value)
    }

    @Test
    fun telemetryMarksOnlineAndFailedHttpRefreshDoesNotClobberLiveStatus() = runTest(dispatcher) {
        val connection = FakeConnection()
        val dashboard = DashboardViewModel(connection).also { model = it }
        dashboard.start()
        runCurrent()
        connection.events.emit(ServerEvent.TelemetryTick(snapshot))
        runCurrent()
        assertTrue(dashboard.uiState.value.isOnline)
        dashboard.loadDashboardData()
        runCurrent()
        assertTrue(dashboard.uiState.value.isOnline)
        assertEquals(snapshot, dashboard.uiState.value.systemSnapshot)
        dashboard.stop()
    }

    @Test
    fun revokedTokenStopsRetryingAndRequestsPairing() = runTest(dispatcher) {
        val connection = FakeConnection().apply {
            ticketError = MonitorApiException(401, "Device revoked")
            snapshotResult = Result.success(snapshot)
        }
        val dashboard = DashboardViewModel(connection).also { model = it }
        dashboard.start()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, connection.ticketRequests)
        assertTrue(dashboard.uiState.value.requiresPairing)
        assertFalse(dashboard.uiState.value.isOnline)
        dashboard.stop()
    }

    @Test
    fun transientDisconnectRecoversWithoutPairingAgain() = runTest(dispatcher) {
        val connection = FakeConnection().apply { failures = 2 }
        val dashboard = DashboardViewModel(connection).also { model = it }
        dashboard.start()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(3, connection.ticketRequests)
        connection.events.emit(ServerEvent.TelemetryTick(snapshot))
        runCurrent()
        assertTrue(dashboard.uiState.value.isOnline)
        assertEquals(0, dashboard.uiState.value.reconnectAttempt)
        dashboard.stop()
    }

    @Test
    fun reconnectBackoffIsBoundedAndJittered() {
        assertEquals(500L, reconnectDelayMillis(0, 0.0))
        assertEquals(1_000L, reconnectDelayMillis(0, 1.0))
        assertEquals(7_500L, reconnectDelayMillis(99, 0.0))
        assertEquals(15_000L, reconnectDelayMillis(99, 1.0))
    }

    @Test
    fun onlineNetworkHandoverPreservesOnlineStateAndNotifiesTransport() = runTest(dispatcher) {
        val connection = FakeConnection().apply { snapshotResult = Result.success(snapshot) }
        val dashboard = DashboardViewModel(connection).also { model = it }
        dashboard.start()
        runCurrent()
        connection.events.emit(ServerEvent.TelemetryTick(snapshot))
        runCurrent()
        assertTrue(dashboard.uiState.value.isOnline)

        dashboard.reconnect()
        runCurrent()

        assertEquals(1, connection.networkNotifications)
        assertTrue(dashboard.uiState.value.isOnline)
        assertEquals(1, connection.events.subscriptionCount.value)
        assertEquals("Codex", app.monitor.feature.dashboard.components.formatModelTitle("Codex Agent"))
        assertEquals("Antigravity", app.monitor.feature.dashboard.components.formatModelTitle("antigravity"))
        dashboard.stop()
    }

    @Test
    fun snapshotRefreshRecoversOnlineStateAfterNetworkHandoverDisconnect() = runTest(dispatcher) {
        val connection = FakeConnection().apply {
            failures = 2
            snapshotResult = Result.failure(IOException("Wi-Fi changed"))
        }
        val dashboard = DashboardViewModel(connection).also { model = it }
        try {
            dashboard.start()
            runCurrent()
            assertFalse(dashboard.uiState.value.isOnline)

            connection.snapshotResult = Result.success(snapshot)
            dashboard.loadDashboardData()
            runCurrent()

            assertTrue(dashboard.uiState.value.isOnline)
            assertEquals(null, dashboard.uiState.value.errorMessage)
            assertEquals(snapshot, dashboard.uiState.value.systemSnapshot)
        } finally {
            dashboard.stop()
        }
    }

    private class FakeConnection : DashboardConnection {
        val events = MutableSharedFlow<ServerEvent>()
        var ticketRequests = 0
        var ticketError: Exception? = null
        var failures = 0
        var networkNotifications = 0
        var snapshotResult: Result<SystemSnapshot> = Result.failure(IOException("HTTP unavailable"))
        override fun notifyNetworkChanged() {
            networkNotifications++
        }
        override suspend fun acquireWsTicket(): Result<String> {
            ticketRequests++
            return ticketError?.let { Result.failure(it) } ?: Result.success("single-use-$ticketRequests")
        }
        override suspend fun getSystemSnapshot(): Result<SystemSnapshot> = snapshotResult
        override suspend fun getProjects(): Result<List<RepoSummary>> = Result.success(emptyList())
        override fun streamServerEvents(ticket: String): Flow<ServerEvent> = flow {
            if (failures-- > 0) throw IOException("Wi-Fi changed")
            events.collect { emit(it) }
        }
    }

    private val snapshot = SystemSnapshot(observedAtUtcMs = 42, hostname = "workstation",
        agentVersion = "0.1.0", uptimeSeconds = 10, battery = BatterySnapshot(hasBattery = false),
        cpu = CpuSnapshot(totalUsagePercent = 10f, frequencyMhz = 3000),
        ram = MemorySnapshot(totalBytes = 16000, usedBytes = 8000, availableBytes = 8000, usagePercent = 50f))
}
