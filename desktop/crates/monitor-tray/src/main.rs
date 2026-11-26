#![cfg_attr(windows, windows_subsystem = "windows")]
mod client;
mod flyout;

use anyhow::{Context, Result};
use client::{Command, Update};
use flyout::{FlyoutState, MonitorFlyout};
use gpui_kit::{
    AppContext, Bounds, QuitMode, WindowBackgroundAppearance, WindowBounds, WindowHandle,
    WindowKind, WindowOptions, point, px, size,
};
use monitor_agent::EmbeddedAgent;
use monitor_tray::{TRAY_INSTANCE_PORT, icon_pixels};
use std::{
    net::{Ipv4Addr, SocketAddrV4, TcpListener},
    sync::mpsc::Receiver,
    sync::{Arc, Mutex},
    time::Duration,
};
use tray_icon::{Icon, MouseButtonState, TrayIconBuilder, TrayIconEvent};

fn logging() -> Result<()> {
    let directory = monitor_agent::data_directory();
    std::fs::create_dir_all(&directory)?;
    let path = directory.join("monitor.log");
    if std::fs::metadata(&path).is_ok_and(|file| file.len() > 2_000_000) {
        let _ = std::fs::rename(&path, directory.join("monitor.previous.log"));
    }
    let file = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)?;
    tracing_subscriber::fmt()
        .with_env_filter("info")
        .with_ansi(false)
        .with_target(false)
        .with_writer(std::sync::Mutex::new(file))
        .try_init()
        .map_err(|error| anyhow::anyhow!(error.to_string()))?;
    Ok(())
}

fn main() -> Result<()> {
    let _ = logging();
    let _instance_lock = match TcpListener::bind(SocketAddrV4::new(
        Ipv4Addr::LOCALHOST,
        TRAY_INSTANCE_PORT,
    )) {
        Ok(lock) => lock,
        Err(e) => {
            tracing::warn!(
                "Another Monitor tray instance is already running or port {TRAY_INSTANCE_PORT} is busy: {e}"
            );
            return Ok(());
        }
    };

    // Enable Windows startup ONLY once on initial installed run; never override user opt-outs (`AUD-024`).
    let autostart_marker = monitor_agent::data_directory().join("autostart.initialized");
    if !autostart_marker.exists()
        && let Ok(exe) = std::env::current_exe()
    {
        let exe_str = exe.to_string_lossy().to_lowercase();
        if exe_str.contains(r"\programs\monitor\") {
            if !monitor_tray::is_auto_start_enabled() {
                let _ = monitor_tray::set_auto_start(true);
            }
            let _ = std::fs::write(&autostart_marker, b"1");
        }
    }

    let agent = EmbeddedAgent::start().context("starting background service")?;
    let port = monitor_agent::configured_port().unwrap_or(9480);
    let (commands, updates) = client::start(port).context("starting local client worker")?;
    let _ = commands.send(Command::Refresh);

    let icon = Icon::from_rgba(icon_pixels(), 32, 32).context("creating tray icon")?;
    let _tray = TrayIconBuilder::new()
        .with_guid(0x82df6cf3_7b40_495e_a2cb_a74f84918acf)
        .with_tooltip("Monitor · 100% Native GPUI Glass")
        .with_icon(icon)
        .build()
        .context("building tray icon")?;

    let state = Arc::new(Mutex::new(FlyoutState {
        hostname: sysinfo::System::host_name().unwrap_or_else(|| "DESKTOP-PC".to_string()),
        os_name: sysinfo::System::long_os_version().unwrap_or_else(|| "Windows 11".to_string()),
        cpu_percent: 0.0,
        status_text: "Initializing...".to_string(),
        mobile_devices_count: 0,
        is_cloud_synced: false,
        cloud_email: None,
        projects_count: 0,
        autostart_enabled: monitor_tray::is_auto_start_enabled(),
        pairing_code: None,
        ..Default::default()
    }));

    let app = gpui_kit::application().with_quit_mode(QuitMode::Explicit);
    let commands_clone = commands.clone();
    let state_clone = Arc::clone(&state);

    app.run(move |cx| {
        gpui_kit::init(cx);

        let active_window: Arc<Mutex<Option<WindowHandle<MonitorFlyout>>>> =
            Arc::new(Mutex::new(None));
        let active_window_task = Arc::clone(&active_window);
        let cmd_tx = commands_clone;
        let app_state = state_clone;

        cx.spawn(async move |cx| {
            let update_receiver: Receiver<Update> = updates;
            let mut slow_poll_ticks: u32 = 0;
            loop {
                cx.background_executor()
                    .timer(Duration::from_millis(50))
                    .await;

                // 1. Process Status Updates from Background Worker
                let mut status_changed = false;
                while let Ok(update) = update_receiver.try_recv() {
                    if let Ok(ref status) = update.status
                        && let Ok(mut current) = app_state.lock()
                    {
                        status_changed = true;
                        current.cpu_percent = status.snapshot.cpu.total_usage_percent;
                        current.mobile_devices_count = status.devices.len();
                        current.projects_count = status.projects.len();
                        current.is_cloud_synced = status.cloud_account.is_some();
                        current.cloud_email =
                            status.cloud_account.as_ref().map(|c| c.email.clone());
                        current.status_text = if status.connected_clients > 0 {
                            format!("Online ({} connected)", status.connected_clients)
                        } else {
                            "Online (Ready to Pair)".to_string()
                        };
                        current.pairing_code = status.pairing_code.as_ref().map(|p| p.code.clone());
                        current.autostart_enabled = monitor_tray::is_auto_start_enabled();
                        current.devices = status.devices.clone();
                        current.projects = status.projects.clone();
                        current.addresses = status.addresses.clone();
                        current.git_access_allowed = status.git_access_allowed;
                        current.delete_access_allowed = status.delete_access_allowed;
                        current.cloud_access_allowed = status.cloud_access_allowed;
                        current.local_access_allowed = status.local_access_allowed;
                        current.terminal_access_allowed = status.terminal_access_allowed;
                        current.checkpoint_access_allowed = status.checkpoint_access_allowed;
                        current.connected_clients = status.connected_clients;
                        current.alerts = status.alerts.clone();
                        current.active_agent_sessions = status.active_agent_sessions.clone();
                        current.disks = status.snapshot.disks.clone();
                        current.top_processes = status.snapshot.cpu.top_processes.clone();
                        current.battery = Some(status.snapshot.battery.clone());
                        current.memory_used_gb =
                            status.snapshot.ram.used_bytes as f32 / (1024.0 * 1024.0 * 1024.0);
                        current.memory_total_gb =
                            status.snapshot.ram.total_bytes as f32 / (1024.0 * 1024.0 * 1024.0);
                        current.uptime_seconds = status.snapshot.uptime_seconds;
                        current.listen_port = status.listen_port;
                        current.host_cloud_device_id = status.host_cloud_device_id.clone();
                    }
                }

                // 2. Reactive check for Theme (Light/Dark) & Desktop Wallpaper (throttled to 1s intervals)
                let mut should_notify = status_changed;
                slow_poll_ticks = slow_poll_ticks.wrapping_add(1);

                if let Ok(mut current) = app_state.lock() {
                    if slow_poll_ticks >= 20 {
                        slow_poll_ticks = 0;
                        let fresh_dark = flyout::state::is_system_dark_mode();
                        let fresh_wall = flyout::state::get_windows_wallpaper_path();

                        if current.is_dark_mode != fresh_dark {
                            current.is_dark_mode = fresh_dark;
                            should_notify = true;
                        }
                        if current.wallpaper_path != fresh_wall {
                            current.wallpaper_path = fresh_wall;
                            should_notify = true;
                        }
                    }

                    if should_notify {
                        let fresh_state = current.clone();
                        if let Ok(mut guard) = active_window_task.lock()
                            && let Some(ref handle) = *guard
                        {
                            let mut closed = false;
                            cx.update(|cx| {
                                if handle
                                    .update(cx, |view, _, cx| {
                                        view.update_state(fresh_state);
                                        cx.notify();
                                    })
                                    .is_err()
                                {
                                    closed = true;
                                }
                            });
                            if closed {
                                *guard = None;
                            }
                        }
                    }
                }

                // 3. Process Tray Click Events
                while let Ok(event) = TrayIconEvent::receiver().try_recv() {
                    if let TrayIconEvent::Click {
                        button_state: MouseButtonState::Up,
                        rect,
                        ..
                    } = event
                    {
                        let Ok(mut win_guard) = active_window_task.lock() else {
                            continue;
                        };

                        let is_open = if let Some(ref handle) = *win_guard {
                            cx.update(|cx| handle.update(cx, |_, _, _| ()).is_ok())
                        } else {
                            false
                        };

                        if is_open {
                            if let Some(existing_window) = win_guard.take() {
                                cx.update(|cx| {
                                    let _ = existing_window.update(cx, |_, window, _| {
                                        window.remove_window();
                                    });
                                });
                            }
                        } else {
                            *win_guard = None;

                            // Calculate usable work area (excluding taskbar) and real Win32 per-monitor DPI scale factor
                            let scale_factor = flyout::state::query_dpi_scale_for_point(
                                rect.position.x as i32,
                                rect.position.y as i32,
                            );
                            let (work_origin_x, work_origin_y, work_w, work_h) = cx.update(|cx| {
                                if let Some(display) = cx.primary_display() {
                                    let vb = display.visible_bounds();
                                    (
                                        (vb.origin.x / px(1.0)) as f64,
                                        (vb.origin.y / px(1.0)) as f64,
                                        (vb.size.width / px(1.0)) as f64,
                                        (vb.size.height / px(1.0)) as f64,
                                    )
                                } else {
                                    (0.0, 0.0, 1920.0, 1030.0)
                                }
                            });

                            let win_w = 332.0;
                            let win_h = 400.0;
                            let work_bottom = work_origin_y + work_h;
                            let work_right = work_origin_x + work_w;

                            // Place bottom of flyout 10px above the taskbar
                            let origin_y = (work_bottom - win_h - 10.0).max(work_origin_y + 10.0);

                            // Center flyout horizontally above tray icon
                            let tray_center_x =
                                (rect.position.x + (rect.size.width as f64 / 2.0)) / scale_factor;
                            let origin_x = (tray_center_x - (win_w / 2.0))
                                .clamp(work_origin_x + 10.0, work_right - win_w - 10.0);

                            let mut current_state =
                                app_state.lock().map(|s| s.clone()).unwrap_or_default();
                            current_state.is_dark_mode = flyout::state::is_system_dark_mode();
                            current_state.wallpaper_path =
                                flyout::state::get_windows_wallpaper_path();
                            let cmd_tx_inner = cmd_tx.clone();

                            let bounds = Bounds {
                                origin: point(px(origin_x as f32), px(origin_y as f32)),
                                size: size(px(win_w as f32), px(win_h as f32)),
                            };

                            let options = WindowOptions {
                                window_bounds: Some(WindowBounds::Windowed(bounds)),
                                titlebar: None,
                                kind: WindowKind::PopUp,
                                window_background: WindowBackgroundAppearance::Transparent,
                                is_movable: false,
                                is_resizable: false,
                                focus: true,
                                show: true,
                                ..Default::default()
                            };

                            let opened_window = cx.update(|cx| {
                                cx.open_window(options, |window, cx| {
                                    cx.new(|cx| {
                                        MonitorFlyout::new(
                                            window,
                                            cx,
                                            current_state,
                                            Some(cmd_tx_inner),
                                        )
                                    })
                                })
                            });

                            if let Ok(wh) = opened_window {
                                *win_guard = Some(wh);
                            }
                        }
                    }
                }
            }
        })
        .detach();
    });

    agent.stop();
    Ok(())
}
