#![allow(unsafe_code)]
// SAFETY: This module encapsulates the single native Win32 FFI call `GetSystemPowerStatus`.
// Invariant: The pointer passed to `GetSystemPowerStatus` points to a zeroed stack-allocated
// `SYSTEM_POWER_STATUS` struct. Windows fills this struct synchronously; no references escape.

use monitor_core::{AcPowerStatus, BatterySnapshot, ChargingState, SupportLevel};
use windows_sys::Win32::System::Power::{GetSystemPowerStatus, SYSTEM_POWER_STATUS};

pub fn read_power_status() -> BatterySnapshot {
    let mut status: SYSTEM_POWER_STATUS = unsafe { std::mem::zeroed() };

    // SAFETY: `&mut status` is a valid, aligned mutable reference to stack memory.
    let success = unsafe { GetSystemPowerStatus(&mut status) };

    if success == 0 {
        return BatterySnapshot {
            has_battery: false,
            percentage: None,
            charging_state: ChargingState::Unknown,
            ac_status: AcPowerStatus::Unknown,
            battery_saver_on: None,
            power_draw_watts: None,
            estimated_remaining_minutes: None,
            health_percentage: None,
            data_quality: SupportLevel::PartiallySupported,
        };
    }

    // BatteryFlag: 128 means No system battery
    let has_battery = status.BatteryFlag != 128 && status.BatteryFlag != 255;

    let percentage = if has_battery && status.BatteryLifePercent <= 100 {
        Some(status.BatteryLifePercent as f32)
    } else {
        None
    };

    let ac_status = match status.ACLineStatus {
        0 => AcPowerStatus::Disconnected,
        1 => AcPowerStatus::Connected,
        _ => AcPowerStatus::Unknown,
    };

    let charging_state = if !has_battery {
        ChargingState::Unknown
    } else if status.BatteryFlag & 8 != 0 {
        ChargingState::Charging
    } else if status.BatteryLifePercent == 100 && ac_status == AcPowerStatus::Connected {
        ChargingState::Full
    } else if ac_status == AcPowerStatus::Connected {
        ChargingState::NotCharging
    } else if has_battery {
        ChargingState::Discharging
    } else {
        ChargingState::Unknown
    };

    // SystemStatusFlag == 1 means Battery Saver active
    let battery_saver_on = Some(status.SystemStatusFlag == 1);

    let estimated_remaining_minutes =
        if has_battery && status.BatteryLifeTime != u32::MAX && status.BatteryLifeTime > 0 {
            Some(status.BatteryLifeTime / 60)
        } else {
            None
        };

    let data_quality = if has_battery {
        SupportLevel::PartiallySupported
    } else {
        SupportLevel::NoBattery
    };

    BatterySnapshot {
        has_battery,
        percentage,
        charging_state,
        ac_status,
        battery_saver_on,
        power_draw_watts: None,
        estimated_remaining_minutes,
        health_percentage: None,
        data_quality,
    }
}
