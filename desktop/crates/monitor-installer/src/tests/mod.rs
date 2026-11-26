mod archive_tests;

use crate::cli::InstallerCliArgs;
use crate::engine::{InstallProgressEvent, validate_install_directory};
use crate::state::{InstallerState, InstallerStep, default_install_directory};
use crate::win32_fallback::can_start_fallback_action;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

pub(super) fn unique_temp_dir(prefix: &str) -> PathBuf {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    std::env::temp_dir().join(format!("{prefix}_{}_{}", std::process::id(), nanos))
}

#[test]
fn default_installer_state_is_consistent() {
    let state = InstallerState::default();
    assert_eq!(state.current_step, InstallerStep::Welcome);
    assert!(!state.eula_agreed);
    assert!(!state.is_uninstall_mode);
    assert!(!state.clean_install);
    assert_eq!(InstallerStep::total_steps(), 6);
    let dir = default_install_directory();
    assert!(dir.ends_with("Monitor"));
}

#[test]
fn cli_parses_silent_upgrade_and_uninstall_flags() {
    let install_cli = InstallerCliArgs::parse(
        [
            "Monitor_Setup.exe",
            "--silent",
            "--dir=D:\\Apps\\Monitor",
            "--no-desktop",
            "--clean",
            "--launch",
        ],
        Some("Monitor_Setup"),
    );
    assert!(!install_cli.is_uninstall);
    assert!(install_cli.is_silent);
    assert_eq!(
        install_cli.target_dir,
        Some(PathBuf::from(r"D:\Apps\Monitor"))
    );
    assert!(install_cli.no_desktop_shortcut);
    assert!(!install_cli.no_startup);
    assert!(install_cli.clean_install);
    assert_eq!(install_cli.launch_after, Some(true));

    let uninst_cli =
        InstallerCliArgs::parse(["uninstall.exe", "/S", "--wipe-data"], Some("uninstall"));
    assert!(uninst_cli.is_uninstall);
    assert!(uninst_cli.is_silent);
    assert!(uninst_cli.wipe_data);
}

#[test]
fn progress_state_never_regresses_on_step_boundaries() {
    let mut state = InstallerState::default();
    state.prepare_for_install();
    assert_eq!(state.current_step, InstallerStep::Installing);

    state.apply_progress_event(InstallProgressEvent::Progress {
        percent: 42.5,
        message: "Extracting Monitor.exe".to_string(),
        files_done: Some((10, 100)),
    });
    assert!((state.install_progress - 42.5).abs() < 0.01);

    state.apply_progress_event(InstallProgressEvent::StepStarted { step_index: 1 });
    assert!(state.task_host_done);
    assert!((state.install_progress - 42.5).abs() < 0.01);

    state.apply_progress_event(InstallProgressEvent::Progress {
        percent: 12.0,
        message: String::new(),
        files_done: None,
    });
    assert!((state.install_progress - 42.5).abs() < 0.01);
    assert_eq!(state.status_message, "Extracting Monitor.exe");

    state.apply_progress_event(InstallProgressEvent::Completed {
        estimated_size_kb: 50_000,
        is_upgrade: true,
    });
    assert_eq!(state.current_step, InstallerStep::Finished);
    assert!((state.install_progress - 100.0).abs() < 0.01);
    assert!(state.task_host_done && state.task_git_done && state.task_shortcuts_done);
    assert!(state.installed_is_upgrade);
}

#[test]
fn path_validation_blocks_drive_roots_system_dirs_dos_names_and_unwritable_targets() {
    assert!(
        validate_install_directory(PathBuf::from("").as_path())
            .error
            .is_some()
    );
    assert!(
        validate_install_directory(PathBuf::from("relative/Monitor").as_path())
            .error
            .is_some()
    );
    assert!(
        validate_install_directory(PathBuf::from(r"C:\").as_path())
            .error
            .is_some()
    );
    assert!(
        validate_install_directory(PathBuf::from(r"C:\Windows\Monitor").as_path())
            .error
            .is_some()
    );
    assert!(
        validate_install_directory(PathBuf::from(r"C:\Users").as_path())
            .error
            .is_some()
    );
    assert!(
        validate_install_directory(PathBuf::from(r"D:\Apps\CON\Monitor").as_path())
            .error
            .is_some()
    );
    assert!(
        validate_install_directory(PathBuf::from(r"D:\Apps\Bad<Name>\Monitor").as_path())
            .error
            .is_some()
    );

    let long_path = format!(r"C:\{}\Monitor", "a".repeat(175));
    assert!(
        validate_install_directory(PathBuf::from(long_path).as_path())
            .error
            .is_some()
    );

    let sandbox = unique_temp_dir("monitor_val_ok");
    let writable = sandbox.join("Monitor_Édition_Test");
    assert!(std::fs::create_dir_all(&sandbox).is_ok());
    let ok_res = validate_install_directory(&writable);
    assert!(
        ok_res.error.is_none(),
        "unexpected error: {:?}",
        ok_res.error
    );

    let occupied = sandbox.join("occupied");
    assert!(std::fs::create_dir_all(&occupied).is_ok());
    assert!(std::fs::write(occupied.join("unrelated.txt"), b"user-data").is_ok());
    let warn_res = validate_install_directory(&occupied);
    assert!(warn_res.error.is_none());
    assert!(warn_res.warning.is_some());

    let _ = std::fs::remove_dir_all(&sandbox);
}

#[test]
fn cli_and_fallback_readiness_work_as_expected() {
    let cli = InstallerCliArgs::parse(
        ["Monitor_Setup.exe", "--fallback-ui", "--help"],
        Some("Monitor_Setup"),
    );
    assert!(cli.fallback_ui);
    assert!(cli.show_help);

    let mut state = InstallerState::default();
    assert!(!can_start_fallback_action(&state));
    state.eula_agreed = true;
    assert!(can_start_fallback_action(&state));
    state.path_error = Some("Blocked".to_string());
    assert!(!can_start_fallback_action(&state));
}
