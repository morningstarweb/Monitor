package app.monitor.core.network

import app.monitor.core.model.ServerEvent
import app.monitor.core.model.WsTicketResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerEventDecoderTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesRustTelemetryEnvelopeWithoutKotlinWrapper() {
        val event = json.decodeServerEvent("""{"event":"TelemetryTick","data":$snapshot}""")
        assertTrue(event is ServerEvent.TelemetryTick)
        assertEquals("workstation", (event as ServerEvent.TelemetryTick).snapshot.hostname)
        assertEquals(42L, event.snapshot.observedAtUtcMs)
        assertEquals(3, event.snapshot.schemaVersion)
    }

    @Test
    fun decodesProjectWorkspaceAndProgressEnvelopes() {
        val project = json.decodeServerEvent("""{"event":"ProjectUpdated","data":{
            "repo_id":"r1","display_name":"Monitor","path":"D:/Monitor",
            "current_branch":"main","is_clean":true,"modified_count":0,"staged_count":0,
            "untracked_count":0,"ahead_count":0,"behind_count":0,"last_activity_utc_ms":42,
            "watch_enabled":true,"trust_profile":"Restricted"}}""") as ServerEvent.ProjectUpdated
        assertEquals("r1", project.summary.repoId)
        val workspace = json.decodeServerEvent("""{"event":"WorkspaceUpdated","data":{
            "repo_id":"r1","snapshot":{"repo_id":"r1","revision":2,"current_branch":"main"}}}""")
            as ServerEvent.WorkspaceUpdated
        assertEquals(2L, workspace.snapshot.revision)
        val progress = json.decodeServerEvent("""{"event":"OperationProgress","data":{
            "operation_id":"o1","repo_id":"r1","stage":"done","message":"Complete"}}""")
            as ServerEvent.OperationProgress
        assertEquals("Complete", progress.message)
    }

    @Test
    fun decodesAlertsAndUnitResyncWithoutDroppingSocket() {
        val alert = json.decodeServerEvent("""{"event":"AlertTriggered","data":{
            "alert_id":"a1","rule_name":"Battery","message":"Low battery","severity":"warning"}}""")
            as ServerEvent.AlertTriggered
        assertEquals("Low battery", alert.message)
        assertEquals(ServerEvent.ResyncRequired, json.decodeServerEvent("""{"event":"ResyncRequired"}"""))
    }

    @Test
    fun ignoresFutureEventTypes() {
        assertNull(json.decodeServerEvent("""{"event":"FutureDesktopEvent","data":{"value":1}}"""))
    }

    @Test(expected = SerializationException::class)
    fun rejectsMalformedKnownEventsSoReconnectCanResync() {
        json.decodeServerEvent("""{"event":"TelemetryTick","data":{}}""")
    }

    @Test
    fun decodesRustTicketRelativeExpiry() {
        val ticket = json.decodeFromString<WsTicketResponse>("""{"ticket":"once-only","expires_in_seconds":30}""")
        assertEquals("once-only", ticket.ticket)
        assertEquals(30, ticket.expiresInSeconds)
    }

    private val snapshot = """{
        "schema_version":3,"observed_at_utc_ms":42,"hostname":"workstation",
        "agent_version":"0.1.0","uptime_seconds":10,
        "battery":{"has_battery":false,"percentage":null,"charging_state":"Unknown",
            "ac_status":"Connected","battery_saver_on":null,"power_draw_watts":null,
            "estimated_remaining_minutes":null,"health_percentage":null,"data_quality":"NoBattery"},
        "cpu":{"total_usage_percent":12.5,"frequency_mhz":3000,"per_core_usage_percent":[10,15],
            "history_60m_percent":[],"top_processes":[]},
        "ram":{"total_bytes":16000,"used_bytes":8000,"available_bytes":8000,"usage_percent":50,
            "swap_total_bytes":0,"swap_used_bytes":0,"history_60m_percent":[]}}
    """
}
