use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

/// System telemetry data quality level.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum SupportLevel {
    Full,
    PartiallySupported,
    NoBattery,
}

/// Battery charging state semantics.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum ChargingState {
    Charging,
    Discharging,
    Full,
    NotCharging,
    Unknown,
}

/// Physical AC wall adapter status.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum AcPowerStatus {
    Connected,
    Disconnected,
    Unknown,
}

/// Live battery snapshot.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct BatterySnapshot {
    pub has_battery: bool,
    pub percentage: Option<f32>,
    pub charging_state: ChargingState,
    pub ac_status: AcPowerStatus,
    pub battery_saver_on: Option<bool>,
    pub power_draw_watts: Option<f32>,
    pub estimated_remaining_minutes: Option<u32>,
    pub health_percentage: Option<f32>,
    pub data_quality: SupportLevel,
}

/// Process resource footprint.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct ProcessSnapshot {
    pub pid: u32,
    pub name: String,
    pub cpu_percent: f32,
    pub memory_bytes: u64,
    pub observed_at_utc_ms: i64,
}

/// CPU hardware telemetry.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct CpuSnapshot {
    pub total_usage_percent: f32,
    pub per_core_usage_percent: Vec<f32>,
    pub frequency_mhz: u32,
    pub history_60m_percent: Vec<f32>,
    pub top_processes: Vec<ProcessSnapshot>,
}

/// RAM and Swap memory telemetry.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct MemorySnapshot {
    pub total_bytes: u64,
    pub used_bytes: u64,
    pub available_bytes: u64,
    pub usage_percent: f32,
    pub swap_total_bytes: u64,
    pub swap_used_bytes: u64,
    pub history_60m_percent: Vec<f32>,
}

/// Hardware-agnostic cooling telemetry (fans, thermals).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct CoolingSnapshot {
    pub has_fan: bool,
    pub fan_rpm: u32,
    pub fan_speed_percent: f32,
    pub fan_profile: String,
    pub is_fanless: bool,
    #[serde(default)]
    pub cpu_fan_rpm: u32,
    #[serde(default)]
    pub gpu_fan_rpm: u32,
    #[serde(default)]
    pub is_dual_fan: bool,
}

/// Physical disk storage metrics.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct DiskSnapshot {
    pub name: String,
    pub mount_point: String,
    pub total_bytes: u64,
    pub available_bytes: u64,
}

/// Comprehensive hardware snapshot payload.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, JsonSchema)]
pub struct SystemSnapshot {
    pub schema_version: u32,
    pub observed_at_utc_ms: i64,
    pub hostname: String,
    pub agent_version: String,
    pub uptime_seconds: u64,
    pub battery: BatterySnapshot,
    pub cpu: CpuSnapshot,
    pub ram: MemorySnapshot,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cooling: Option<CoolingSnapshot>,
    #[serde(default)]
    pub disks: Vec<DiskSnapshot>,
}
