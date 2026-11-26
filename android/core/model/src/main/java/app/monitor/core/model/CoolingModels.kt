package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Hardware-agnostic cooling telemetry model.
 * Supports Intel/AMD, ASUS, HP, Dell, Lenovo, and fanless devices.
 */
@Serializable
data class CoolingSnapshot(
    @SerialName("has_fan") val hasFan: Boolean = true,
    @SerialName("fan_rpm") val fanRpm: Int = 0,
    @SerialName("fan_speed_percent") val fanSpeedPercent: Float = 0f,
    @SerialName("fan_profile") val fanProfile: String = "Balanced",
    @SerialName("is_fanless") val isFanless: Boolean = false,
    @SerialName("cpu_fan_rpm") val cpuFanRpm: Int = 0,
    @SerialName("gpu_fan_rpm") val gpuFanRpm: Int = 0,
    @SerialName("is_dual_fan") val isDualFan: Boolean = false
)
