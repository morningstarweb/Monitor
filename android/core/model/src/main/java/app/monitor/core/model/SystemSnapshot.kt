package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChargingState {
    @SerialName("Charging") Charging,
    @SerialName("Discharging") Discharging,
    @SerialName("Full") Full,
    @SerialName("NotCharging") NotCharging,
    @SerialName("Unknown") Unknown
}

@Serializable
enum class AcPowerStatus {
    @SerialName("Connected") Connected,
    @SerialName("Disconnected") Disconnected,
    @SerialName("Unknown") Unknown
}

@Serializable
enum class SupportLevel {
    @SerialName("Full") Full,
    @SerialName("PartiallySupported") PartiallySupported,
    @SerialName("NoBattery") NoBattery,
    @SerialName("Estimated") Estimated,
    @SerialName("Unavailable") Unavailable
}

@Serializable
data class BatterySnapshot(
    @SerialName("has_battery") val hasBattery: Boolean,
    @SerialName("percentage") val percentage: Float? = null,
    @SerialName("charging_state") val chargingState: ChargingState = ChargingState.Unknown,
    @SerialName("ac_status") val acStatus: AcPowerStatus = AcPowerStatus.Unknown,
    @SerialName("battery_saver_on") val batterySaverOn: Boolean? = null,
    @SerialName("power_draw_watts") val powerDrawWatts: Float? = null,
    @SerialName("estimated_remaining_minutes") val estimatedRemainingMinutes: Long? = null,
    @SerialName("health_percentage") val healthPercentage: Float? = null,
    @SerialName("data_quality") val dataQuality: SupportLevel = SupportLevel.Full
)

@Serializable
data class ProcessSnapshot(
    @SerialName("pid") val pid: Long,
    @SerialName("name") val name: String,
    @SerialName("cpu_percent") val cpuPercent: Float,
    @SerialName("memory_bytes") val memoryBytes: Long
)

@Serializable
data class CpuSnapshot(
    @SerialName("total_usage_percent") val totalUsagePercent: Float,
    @SerialName("per_core_usage_percent") val perCoreUsagePercent: List<Float> = emptyList(),
    @SerialName("frequency_mhz") val frequencyMhz: Long,
    @SerialName("history_60m_percent") val history60mPercent: List<Float> = emptyList(),
    @SerialName("top_processes") val topProcesses: List<ProcessSnapshot> = emptyList()
)

@Serializable
data class MemorySnapshot(
    @SerialName("total_bytes") val totalBytes: Long,
    @SerialName("used_bytes") val usedBytes: Long,
    @SerialName("available_bytes") val availableBytes: Long,
    @SerialName("usage_percent") val usagePercent: Float,
    @SerialName("swap_total_bytes") val swapTotalBytes: Long = 0,
    @SerialName("swap_used_bytes") val swapUsedBytes: Long = 0,
    @SerialName("history_60m_percent") val history60mPercent: List<Float> = emptyList()
)

@Serializable
data class DiskSnapshot(
    @SerialName("name") val name: String,
    @SerialName("mount_point") val mountPoint: String,
    @SerialName("total_bytes") val totalBytes: Long,
    @SerialName("available_bytes") val availableBytes: Long
)

@Serializable
data class ProcessKillResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("pid") val pid: Long
)

@Serializable
data class SystemSnapshot(
    @SerialName("schema_version") val schemaVersion: Int = 3,
    @SerialName("observed_at_utc_ms") val observedAtUtcMs: Long,
    @SerialName("hostname") val hostname: String,
    @SerialName("agent_version") val agentVersion: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Long,
    @SerialName("battery") val battery: BatterySnapshot,
    @SerialName("cpu") val cpu: CpuSnapshot,
    @SerialName("ram") val ram: MemorySnapshot,
    @SerialName("cooling") val cooling: CoolingSnapshot? = null,
    @SerialName("disks") val disks: List<DiskSnapshot> = emptyList()
)

