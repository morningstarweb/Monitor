//! Windows startup integration and the embedded tray glyph.
use anyhow::{Context, Result};
#[cfg(windows)]
use winreg::{
    RegKey,
    enums::{HKEY_CURRENT_USER, KEY_READ, KEY_WRITE},
};

pub const TRAY_INSTANCE_PORT: u16 = 9489;
#[cfg(windows)]
const RUN_KEY_PATH: &str = r"Software\Microsoft\Windows\CurrentVersion\Run";
#[cfg(windows)]
const APP_REG_NAME: &str = "Monitor";

#[cfg(windows)]
pub fn is_auto_start_enabled() -> bool {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let run_val = hkcu
        .open_subkey_with_flags(RUN_KEY_PATH, KEY_READ)
        .and_then(|key| key.get_value::<String, _>(APP_REG_NAME))
        .ok();

    if let Some(val) = run_val {
        let clean_val = val.trim_matches('"').to_lowercase();
        if let Ok(exe) = std::env::current_exe() {
            let exe_str = exe.to_string_lossy().to_lowercase();
            if clean_val == exe_str
                || (exe_str.contains("monitor.exe") && clean_val.contains("monitor.exe"))
            {
                if let Ok(approved_key) = hkcu.open_subkey_with_flags(
                    r"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run",
                    KEY_READ,
                ) && let Ok(raw) = approved_key.get_raw_value(APP_REG_NAME)
                    && !raw.bytes.is_empty()
                    && (raw.bytes[0] & 1) != 0
                {
                    return false;
                }
                return true;
            }
        }
    }
    false
}

#[cfg(not(windows))]
pub fn is_auto_start_enabled() -> bool {
    false
}

#[cfg(windows)]
pub fn set_auto_start(enabled: bool) -> Result<bool> {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    if enabled {
        let (key, _) = hkcu
            .create_subkey(RUN_KEY_PATH)
            .context("accessing Windows startup settings")?;
        let value = format!("\"{}\"", std::env::current_exe()?.display());
        key.set_value(APP_REG_NAME, &value)
            .context("saving Windows startup setting")?;

        let enabled_bytes: [u8; 12] = [2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        if let Ok((approved_key, _)) = hkcu.create_subkey(
            r"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run",
        ) {
            let _ = approved_key.set_raw_value(
                APP_REG_NAME,
                &winreg::RegValue {
                    vtype: winreg::enums::REG_BINARY,
                    bytes: enabled_bytes.to_vec(),
                },
            );
        }
        if let Ok((folder_key, _)) = hkcu.create_subkey(
            r"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\StartupFolder",
        ) {
            let _ = folder_key.set_raw_value(
                "Monitor.lnk",
                &winreg::RegValue {
                    vtype: winreg::enums::REG_BINARY,
                    bytes: enabled_bytes.to_vec(),
                },
            );
        }
    } else {
        match hkcu.open_subkey_with_flags(RUN_KEY_PATH, KEY_WRITE) {
            Ok(key) => match key.delete_value(APP_REG_NAME) {
                Ok(()) => {}
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                Err(error) => return Err(error.into()),
            },
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error.into()),
        }
        let disabled_bytes: [u8; 12] = [3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        if let Ok((approved_key, _)) = hkcu.create_subkey(
            r"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run",
        ) {
            let _ = approved_key.set_raw_value(
                APP_REG_NAME,
                &winreg::RegValue {
                    vtype: winreg::enums::REG_BINARY,
                    bytes: disabled_bytes.to_vec(),
                },
            );
        }
    }
    Ok(enabled)
}

#[cfg(not(windows))]
pub fn set_auto_start(_enabled: bool) -> Result<bool> {
    Ok(false)
}

/// Modern Windows 11 Fluent-inspired anti-aliased Monitor icon (32x32 RGBA).
/// Features the Tilted Perspective Monitor Bezel, Git Branch 3-Node Graph, and Emerald Online Indicator.
pub fn icon_pixels() -> Vec<u8> {
    let mut pixels = vec![0u8; 32 * 32 * 4];

    let mut put = |x: i32, y: i32, r: u8, g: u8, b: u8, a: u8| {
        if (0..32).contains(&x) && (0..32).contains(&y) {
            let idx = ((y as usize) * 32 + (x as usize)) * 4;
            let sa = a as f32 / 255.0;
            let da = pixels[idx + 3] as f32 / 255.0;
            let oa = sa + da * (1.0 - sa);
            if oa > 0.0 {
                pixels[idx] =
                    ((r as f32 * sa + pixels[idx] as f32 * da * (1.0 - sa)) / oa).round() as u8;
                pixels[idx + 1] =
                    ((g as f32 * sa + pixels[idx + 1] as f32 * da * (1.0 - sa)) / oa).round() as u8;
                pixels[idx + 2] =
                    ((b as f32 * sa + pixels[idx + 2] as f32 * da * (1.0 - sa)) / oa).round() as u8;
                pixels[idx + 3] = (oa * 255.0).round() as u8;
            }
        }
    };

    // 1. Tilted Monitor Bezel & Dark Obsidian Interior (Electric Indigo -> Cyan Gradient)
    for y in 3..23 {
        for x in 2..30 {
            let tilted_top = 6 - ((x - 2) * 3 / 28);
            if y >= tilted_top && y <= 21 {
                let is_border = y <= tilted_top + 1 || y >= 20 || x <= 3 || x >= 28;
                if is_border {
                    let t = (x - 2) as f32 / 28.0;
                    let r = (91.0 * (1.0 - t) + 0.0 * t) as u8;
                    let g = (77.0 * (1.0 - t) + 210.0 * t) as u8;
                    let b = 255u8;
                    put(x, y, r, g, b, 255);
                } else {
                    put(x, y, 15, 23, 42, 255);
                }
            }
        }
    }

    // 2. Git Branch 3-Node Graph inside Monitor Screen (Neon Emerald #10B981 & Cyan #00D2FF)
    for y in 9..18 {
        put(12, y, 16, 185, 129, 255);
        put(13, y, 16, 185, 129, 255);
    }
    for step in 0..6 {
        put(13 + step, 11 + step, 0, 210, 255, 255);
        put(13 + step, 12 + step, 0, 210, 255, 220);
    }
    // 3 Commit Nodes
    for &(cx, cy, r, g, b) in &[
        (12, 9, 16, 185, 129),
        (12, 17, 16, 185, 129),
        (19, 16, 0, 210, 255),
    ] {
        for dy in -1..=1 {
            for dx in -1..=1 {
                put(cx + dx, cy + dy, r, g, b, 255);
            }
        }
    }

    // 3. Stand Neck & Base
    for y in 22..26 {
        for x in 14..18 {
            put(x, y, 139, 92, 246, 255);
        }
    }
    for y in 26..28 {
        for x in 9..23 {
            put(x, y, 91, 77, 255, 255);
        }
    }

    // 4. Live Status Emerald Badge (Bottom-Right)
    for dy in 0..4 {
        for dx in 0..4 {
            put(26 + dx, 24 + dy, 16, 185, 129, 255);
        }
    }

    pixels
}
