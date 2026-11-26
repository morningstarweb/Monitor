use anyhow::{Context, Result, bail};
use monitor_installer::archive::{extract_payload_bytes, inspect_payload_summary};
use monitor_installer::engine::registry::{
    register_uninstaller, remove_startup_registry, save_installer_preferences, set_startup_registry,
};
use monitor_installer::engine::shortcut::{
    create_shortcut, desktop_shortcut_path, remove_shortcut, start_menu_shortcut_path,
};
use monitor_installer::engine::{
    InstallOptions, InstallProgressEvent, available_disk_bytes, terminate_running_monitor,
    validate_install_directory, wipe_user_data_if_requested,
};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};

static EMBEDDED_PAYLOAD: &[u8] = include_bytes!("../payload.zip");

/// Returns the estimated uncompressed bytes required by the embedded installer payload.
pub fn payload_required_bytes() -> u64 {
    inspect_payload_summary(EMBEDDED_PAYLOAD)
        .map(|s| s.total_uncompressed_bytes)
        .unwrap_or(145 * 1024 * 1024)
}

pub fn run_install_pipeline<F>(
    options: InstallOptions,
    cancel_flag: &AtomicBool,
    mut on_progress: F,
) -> Result<()>
where
    F: FnMut(InstallProgressEvent),
{
    if cancel_flag.load(Ordering::Relaxed) {
        on_progress(InstallProgressEvent::Cancelled);
        return Ok(());
    }

    let target_dir = &options.target_dir;
    let validation = validate_install_directory(target_dir);
    if let Some(reason) = validation.error {
        bail!("{reason}");
    }
    let is_upgrade = target_dir.join("Monitor.exe").is_file();

    // Step 0: Teardown & Clean Wipe
    on_progress(InstallProgressEvent::Progress {
        percent: 4.0,
        message: if is_upgrade {
            "Stopping existing Monitor service for upgrade...".to_string()
        } else {
            "Preparing installation environment...".to_string()
        },
        files_done: None,
    });

    terminate_running_monitor(Some(target_dir));
    if cancel_flag.load(Ordering::Relaxed) {
        on_progress(InstallProgressEvent::Cancelled);
        return Ok(());
    }

    wipe_user_data_if_requested(options.clean_install);

    std::fs::create_dir_all(target_dir)
        .with_context(|| format!("creating installation directory {}", target_dir.display()))?;

    // Check available disk space before extraction if the target path already has enough info.
    let required = payload_required_bytes();
    if let Some(free_bytes) = available_disk_bytes(target_dir)
        && required > 0
        && free_bytes < required.saturating_add(8 * 1024 * 1024)
    {
        bail!(
            "Insufficient disk space at {} ({:.0} MB free, {:.0} MB required).",
            target_dir.display(),
            free_bytes as f64 / (1024.0 * 1024.0),
            required as f64 / (1024.0 * 1024.0)
        );
    }

    // Step 1: Extract Solid Zstd/ZIP Payload
    on_progress(InstallProgressEvent::StepStarted { step_index: 0 });
    let mut git_stage_started = false;

    let report = match extract_payload_bytes(EMBEDDED_PAYLOAD, target_dir, cancel_flag, |prog| {
        if !git_stage_started && prog.current_file.starts_with("tools/") {
            git_stage_started = true;
            on_progress(InstallProgressEvent::StepStarted { step_index: 1 });
        }
        let byte_ratio = if prog.total_bytes > 0 {
            (prog.bytes_written as f32 / prog.total_bytes as f32).clamp(0.0, 1.0)
        } else if prog.total_files > 0 {
            (prog.file_index as f32 / prog.total_files as f32).clamp(0.0, 1.0)
        } else {
            1.0
        };
        let pct = 10.0 + byte_ratio * 76.0;
        let short_name = prog
            .current_file
            .rsplit('/')
            .next()
            .unwrap_or(&prog.current_file);
        on_progress(InstallProgressEvent::Progress {
            percent: pct,
            message: format!("Extracting {short_name}"),
            files_done: Some((prog.file_index, prog.total_files)),
        });
    }) {
        Ok(report) => report,
        Err(err) if cancel_flag.load(Ordering::Relaxed) => {
            tracing::info!("Installation cancelled and rolled back cleanly: {err}");
            on_progress(InstallProgressEvent::Cancelled);
            return Ok(());
        }
        Err(err) => return Err(err),
    };

    if cancel_flag.load(Ordering::Relaxed) {
        on_progress(InstallProgressEvent::Cancelled);
        return Ok(());
    }

    on_progress(InstallProgressEvent::StepStarted { step_index: 1 });
    on_progress(InstallProgressEvent::StepStarted { step_index: 2 });

    ensure_uninstaller_present(target_dir);

    on_progress(InstallProgressEvent::Progress {
        percent: 90.0,
        message: "Creating Windows Start Menu & Desktop shortcuts...".to_string(),
        files_done: None,
    });

    let target_exe = target_dir.join("Monitor.exe");
    let icon_path = target_dir.join("monitor.ico");

    if let Some(start_lnk) = start_menu_shortcut_path()
        && let Err(e) = create_shortcut(&start_lnk, &target_exe, target_dir, &icon_path)
    {
        tracing::warn!("Start menu shortcut failed: {e}");
    }

    if let Some(desk_lnk) = desktop_shortcut_path() {
        if options.create_desktop_shortcut {
            if let Err(e) = create_shortcut(&desk_lnk, &target_exe, target_dir, &icon_path) {
                tracing::warn!("Desktop shortcut failed: {e}");
            }
        } else {
            remove_shortcut(&desk_lnk);
        }
    }

    on_progress(InstallProgressEvent::Progress {
        percent: 95.0,
        message: "Configuring Windows startup & uninstall registry...".to_string(),
        files_done: None,
    });

    if options.start_with_windows {
        set_startup_registry(&target_exe)?;
    } else {
        remove_startup_registry()?;
    }

    let version = env!("CARGO_PKG_VERSION");
    register_uninstaller(target_dir, version, report.estimated_size_kb)?;
    save_installer_preferences(
        options.create_desktop_shortcut,
        options.start_with_windows,
        version,
    );

    on_progress(InstallProgressEvent::Completed {
        estimated_size_kb: report.estimated_size_kb,
        is_upgrade,
    });

    Ok(())
}

/// Ensures `uninstall.exe` exists in `target_dir` using the slim uninstaller when available.
fn ensure_uninstaller_present(target_dir: &Path) {
    let dest = target_dir.join("uninstall.exe");
    if dest.is_file() {
        return;
    }
    if let Ok(current_installer) = std::env::current_exe() {
        let slim_uninstaller = current_installer.with_file_name("monitor-uninstaller.exe");
        if slim_uninstaller.is_file() {
            let _ = std::fs::copy(slim_uninstaller, &dest);
            return;
        }
        tracing::warn!("monitor-uninstaller.exe missing; copying setup executable as fallback");
        let _ = std::fs::copy(current_installer, dest);
    }
}
