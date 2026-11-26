#![cfg_attr(windows, windows_subsystem = "windows")]
mod install;

use anyhow::Result;
use gpui_kit::{
    AppContext, Bounds, QuitMode, WindowBackgroundAppearance, WindowBounds, WindowKind,
    WindowOptions,
    component::{Theme, ThemeMode},
    px, size,
};
use install::{payload_required_bytes, run_install_pipeline};
use monitor_installer::cli::InstallerCliArgs;
use monitor_installer::engine::uninstall::{execute_clean_wipe, execute_uninstallation_at};
use monitor_installer::engine::{InstallOptions, InstallProgressEvent, detect_existing_install};
use monitor_installer::state::{InstallerState, default_install_directory};
use monitor_installer::ui::{InstallerCommand, MonitorInstallerWindow};
use monitor_installer::win32_fallback::{
    FallbackRunRequest, run_fallback_gui, show_cli_help, show_fatal_error_notice,
};
use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{Receiver, Sender, channel};
use std::sync::{Arc, Mutex};
use std::time::Duration;

fn append_installer_log(line: &str) {
    let Some(local_appdata) = std::env::var_os("LOCALAPPDATA") else {
        return;
    };
    let log_dir = PathBuf::from(local_appdata).join("Monitor");
    let _ = std::fs::create_dir_all(&log_dir);
    let log_path = log_dir.join("installer.log");
    if let Ok(mut file) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(log_path)
    {
        let ts = chrono::Local::now().format("%Y-%m-%d %H:%M:%S");
        let _ = writeln!(file, "[{ts}] {line}");
    }
}

fn run_silent_install(cli: &InstallerCliArgs) -> Result<()> {
    let existing = detect_existing_install();
    let target_dir = cli.target_dir.clone().unwrap_or_else(|| {
        existing
            .as_ref()
            .map(|e| e.install_dir.clone())
            .unwrap_or_else(default_install_directory)
    });
    let create_desktop = if cli.no_desktop_shortcut {
        false
    } else {
        existing
            .as_ref()
            .map(|e| e.create_desktop_shortcut)
            .unwrap_or(true)
    };
    let start_with_windows = if cli.no_startup {
        false
    } else {
        existing
            .as_ref()
            .map(|e| e.start_with_windows)
            .unwrap_or(true)
    };

    let options = InstallOptions {
        target_dir: target_dir.clone(),
        create_desktop_shortcut: create_desktop,
        start_with_windows,
        clean_install: cli.clean_install,
    };

    append_installer_log(&format!(
        "Starting silent setup v{} into {}",
        env!("CARGO_PKG_VERSION"),
        target_dir.display()
    ));

    let cancel_flag = AtomicBool::new(false);
    run_install_pipeline(options, &cancel_flag, |ev| {
        if let InstallProgressEvent::Completed { is_upgrade, .. } = ev {
            append_installer_log(if is_upgrade {
                "Silent upgrade completed successfully."
            } else {
                "Silent install completed successfully."
            });
        }
    })?;

    if cli.launch_after == Some(true) {
        let exe = target_dir.join("Monitor.exe");
        if exe.is_file() {
            let _ = std::process::Command::new(&exe)
                .current_dir(&target_dir)
                .spawn();
        }
    }

    Ok(())
}

fn run_silent_uninstall(cli: &InstallerCliArgs) -> Result<()> {
    let install_dir = cli
        .target_dir
        .clone()
        .unwrap_or_else(monitor_installer::engine::uninstall::resolve_uninstall_directory);
    append_installer_log(&format!(
        "Starting silent uninstall from {}",
        install_dir.display()
    ));
    execute_uninstallation_at(&install_dir)?;
    if cli.wipe_data {
        execute_clean_wipe();
    }
    append_installer_log("Silent uninstall completed.");
    Ok(())
}

fn run_install_with_sender(
    options: InstallOptions,
    cancel_flag: &AtomicBool,
    tx: Sender<InstallProgressEvent>,
) -> Result<()> {
    run_install_pipeline(options, cancel_flag, move |event| {
        let _ = tx.send(event);
    })
}

fn main() -> Result<()> {
    let cli = InstallerCliArgs::from_env();

    if cli.show_help {
        show_cli_help(false);
        return Ok(());
    }

    if cli.is_silent {
        let res = if cli.is_uninstall {
            run_silent_uninstall(&cli)
        } else {
            run_silent_install(&cli)
        };
        if let Err(ref err) = res {
            append_installer_log(&format!("Silent setup error: {err:#}"));
            std::process::exit(1);
        }
        return Ok(());
    }

    let mut initial_state = InstallerState::detect_for_host(payload_required_bytes());
    if let Some(dir) = cli.target_dir {
        initial_state.install_dir = dir;
        initial_state.refresh_location_checks();
    }
    if cli.no_desktop_shortcut {
        initial_state.create_desktop_shortcut = false;
    }
    if cli.no_startup {
        initial_state.start_with_windows = false;
    }
    if cli.clean_install {
        initial_state.clean_install = true;
    }
    if let Some(launch) = cli.launch_after {
        initial_state.launch_on_finish = launch;
    }
    if cli.is_uninstall {
        initial_state.is_uninstall_mode = true;
    }

    if cli.fallback_ui {
        if let Err(err) = run_fallback_gui(FallbackRunRequest {
            initial_state,
            install_runner: Some(run_install_with_sender),
            triggered_by_gpu_failure: false,
        }) {
            let msg = format!("{err:#}");
            append_installer_log(&format!("Win32 fallback setup error: {msg}"));
            show_fatal_error_notice("Monitor Setup Error", &msg);
            std::process::exit(1);
        }
        return Ok(());
    }

    let app = gpui_kit::application().with_quit_mode(QuitMode::Explicit);
    let (cmd_tx, cmd_rx) = channel::<InstallerCommand>();
    let (prog_tx, prog_rx) = channel::<InstallProgressEvent>();

    let fallback_state = initial_state.clone();
    let shared_state = Arc::new(Mutex::new(initial_state));
    let gpui_window_failed = Arc::new(AtomicBool::new(false));
    let gpui_window_failed_clone = Arc::clone(&gpui_window_failed);

    app.run(move |cx| {
        gpui_kit::init(cx);
        Theme::change(ThemeMode::Dark, None, cx);

        let window_bounds =
            WindowBounds::Windowed(Bounds::centered(None, size(px(800.0), px(540.0)), cx));

        let window_options = WindowOptions {
            window_bounds: Some(window_bounds),
            titlebar: None,
            window_background: WindowBackgroundAppearance::Opaque,
            focus: true,
            show: true,
            kind: WindowKind::Normal,
            is_movable: true,
            is_resizable: false,
            is_minimizable: true,
            ..Default::default()
        };

        let initial_state = shared_state.lock().map(|s| s.clone()).unwrap_or_default();
        let cmd_tx_window = cmd_tx.clone();
        let Ok(installer_handle) = cx.open_window(window_options, |window, cx| {
            cx.new(|cx| {
                let view = MonitorInstallerWindow::new(initial_state, Some(cmd_tx_window), cx);
                window.focus(view.focus_handle(), cx);
                view
            })
        }) else {
            append_installer_log(
                "GPUI window creation failed (GPU/surface unavailable); launching Win32 GDI compatibility UI.",
            );
            gpui_window_failed_clone.store(true, Ordering::SeqCst);
            cx.quit();
            return;
        };

        let active_handle = Arc::new(Mutex::new(Some(installer_handle)));
        let active_handle_clone = Arc::clone(&active_handle);

        cx.spawn(async move |cx| {
            let cmd_receiver: Receiver<InstallerCommand> = cmd_rx;
            let prog_receiver: Receiver<InstallProgressEvent> = prog_rx;
            let prog_tx_worker = prog_tx.clone();

            loop {
                cx.background_executor()
                    .timer(Duration::from_millis(30))
                    .await;

                while let Ok(cmd) = cmd_receiver.try_recv() {
                    match cmd {
                        InstallerCommand::StartInstall {
                            options,
                            cancel_flag,
                        } => {
                            let tx_pipe = prog_tx_worker.clone();
                            let tx_err = tx_pipe.clone();
                            let _ = std::thread::Builder::new()
                                .name("monitor-install-worker".to_string())
                                .spawn(move || {
                                    let res =
                                        run_install_pipeline(options, &cancel_flag, move |event| {
                                            let _ = tx_pipe.send(event);
                                        });
                                    if let Err(e) = res {
                                        append_installer_log(&format!("Install error: {e:#}"));
                                        let _ = tx_err.send(InstallProgressEvent::Failed {
                                            error: format!("{e:#}"),
                                        });
                                    }
                                });
                        }
                        InstallerCommand::StartUninstall {
                            install_dir,
                            wipe_data,
                        } => {
                            let tx_pipe = prog_tx_worker.clone();
                            let _ = std::thread::Builder::new()
                                .name("monitor-uninstall-worker".to_string())
                                .spawn(move || {
                                    let res = execute_uninstallation_at(&install_dir);
                                    match res {
                                        Ok(()) => {
                                            if wipe_data {
                                                execute_clean_wipe();
                                            }
                                            let _ = tx_pipe.send(InstallProgressEvent::Completed {
                                                estimated_size_kb: 0,
                                                is_upgrade: false,
                                            });
                                        }
                                        Err(e) => {
                                            append_installer_log(&format!(
                                                "Uninstall error: {e:#}"
                                            ));
                                            let _ = tx_pipe.send(InstallProgressEvent::Failed {
                                                error: format!("{e:#}"),
                                            });
                                        }
                                    }
                                });
                        }
                    }
                }

                let mut events = Vec::new();
                while let Ok(event) = prog_receiver.try_recv() {
                    events.push(event);
                }

                if !events.is_empty()
                    && let Ok(guard) = active_handle_clone.lock()
                    && let Some(ref handle) = *guard
                {
                    cx.update(|cx| {
                        let _ = handle.update(cx, |view, _, cx| {
                            for event in events {
                                if view.state.is_uninstall_mode {
                                    match event {
                                        InstallProgressEvent::Completed { .. } => {
                                            view.state.uninstall_done = true;
                                            view.state.is_uninstalling = false;
                                            view.state.uninstall_error = None;
                                        }
                                        InstallProgressEvent::Failed { error } => {
                                            view.state.is_uninstalling = false;
                                            view.state.uninstall_error = Some(error);
                                        }
                                        _ => {}
                                    }
                                } else {
                                    if matches!(
                                        event,
                                        InstallProgressEvent::Completed { .. }
                                            | InstallProgressEvent::Failed { .. }
                                            | InstallProgressEvent::Cancelled
                                    ) {
                                        view.active_cancel_flag = None;
                                    }
                                    view.state.apply_progress_event(event);
                                }
                            }
                            cx.notify();
                        });
                    });
                }
            }
        })
        .detach();
    });

    if gpui_window_failed.load(Ordering::SeqCst)
        && let Err(err) = run_fallback_gui(FallbackRunRequest {
            initial_state: fallback_state,
            install_runner: Some(run_install_with_sender),
            triggered_by_gpu_failure: true,
        })
    {
        let msg = format!(
            "Monitor Setup could not initialize either its hardware UI or Win32 compatibility window.\n\nDetails: {err:#}"
        );
        append_installer_log(&msg);
        show_fatal_error_notice("Monitor Setup Fatal Error", &msg);
        std::process::exit(1);
    }

    Ok(())
}
