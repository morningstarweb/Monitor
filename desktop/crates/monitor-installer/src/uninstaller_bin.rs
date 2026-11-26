#![cfg_attr(windows, windows_subsystem = "windows")]

use anyhow::Result;
use gpui_kit::{
    AppContext, Bounds, QuitMode, WindowBackgroundAppearance, WindowBounds, WindowKind,
    WindowOptions,
    component::{Theme, ThemeMode},
    px, size,
};
use monitor_installer::cli::InstallerCliArgs;
use monitor_installer::engine::InstallProgressEvent;
use monitor_installer::engine::uninstall::{
    execute_clean_wipe, execute_uninstallation_at, resolve_uninstall_directory,
};
use monitor_installer::state::InstallerState;
use monitor_installer::ui::{InstallerCommand, MonitorInstallerWindow};
use monitor_installer::win32_fallback::{
    FallbackRunRequest, run_fallback_gui, show_cli_help, show_fatal_error_notice,
};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{Receiver, channel};
use std::sync::{Arc, Mutex};
use std::time::Duration;

/// Slim Windows uninstaller entry point without the embedded installer payload.
fn main() -> Result<()> {
    let cli = InstallerCliArgs::from_env();
    if cli.show_help {
        show_cli_help(true);
        return Ok(());
    }

    let install_dir = cli.target_dir.unwrap_or_else(resolve_uninstall_directory);

    if cli.is_silent {
        if let Err(err) = execute_uninstallation_at(&install_dir) {
            show_fatal_error_notice("Monitor Uninstaller Error", &format!("{err:#}"));
            std::process::exit(1);
        }
        if cli.wipe_data {
            execute_clean_wipe();
        }
        return Ok(());
    }

    let initial_state = InstallerState {
        is_uninstall_mode: true,
        uninstall_dir: install_dir,
        uninstall_wipe_data: cli.wipe_data,
        ..Default::default()
    };

    if cli.fallback_ui {
        if let Err(err) = run_fallback_gui(FallbackRunRequest {
            initial_state,
            install_runner: None,
            triggered_by_gpu_failure: false,
        }) {
            show_fatal_error_notice("Monitor Uninstaller Error", &format!("{err:#}"));
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
        let Ok(handle) = cx.open_window(window_options, |window, cx| {
            cx.new(|cx| {
                let view = MonitorInstallerWindow::new(initial_state, Some(cmd_tx_window), cx);
                window.focus(view.focus_handle(), cx);
                view
            })
        }) else {
            gpui_window_failed_clone.store(true, Ordering::SeqCst);
            cx.quit();
            return;
        };

        let active_handle = Arc::new(Mutex::new(Some(handle)));
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
                    if let InstallerCommand::StartUninstall {
                        install_dir,
                        wipe_data,
                    } = cmd
                    {
                        let tx_pipe = prog_tx_worker.clone();
                        let _ = std::thread::Builder::new()
                            .name("monitor-uninstall-worker".to_string())
                            .spawn(move || match execute_uninstallation_at(&install_dir) {
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
                                    let _ = tx_pipe.send(InstallProgressEvent::Failed {
                                        error: format!("{e:#}"),
                                    });
                                }
                            });
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
            install_runner: None,
            triggered_by_gpu_failure: true,
        })
    {
        show_fatal_error_notice(
            "Monitor Uninstaller Fatal Error",
            &format!("Unable to open uninstaller window: {err:#}"),
        );
        std::process::exit(1);
    }

    Ok(())
}
