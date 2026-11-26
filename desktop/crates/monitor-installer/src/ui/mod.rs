pub mod finish;
pub mod license;
pub mod location;
pub mod options;
pub mod progress;
pub mod render;
pub mod sidebar;
pub mod stepper;
pub mod titlebar;
pub mod uninstaller;
pub mod welcome;

use crate::engine::{InstallOptions, resolve_windows_system_exe};
use crate::state::{InstallerState, InstallerStep};
use crate::theme::InstallerTheme;
use gpui_kit::*;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::Sender;

#[derive(Clone, Debug)]
pub enum InstallerCommand {
    StartInstall {
        options: InstallOptions,
        cancel_flag: Arc<AtomicBool>,
    },
    StartUninstall {
        install_dir: PathBuf,
        wipe_data: bool,
    },
}

#[derive(Debug)]
pub struct MonitorInstallerWindow {
    pub state: InstallerState,
    pub theme: InstallerTheme,
    pub command_sender: Option<Sender<InstallerCommand>>,
    pub active_cancel_flag: Option<Arc<AtomicBool>>,
    pub focus_handle: FocusHandle,
}

impl MonitorInstallerWindow {
    pub fn new(
        state: InstallerState,
        command_sender: Option<Sender<InstallerCommand>>,
        cx: &mut Context<Self>,
    ) -> Self {
        Self {
            state,
            theme: InstallerTheme::default(),
            command_sender,
            active_cancel_flag: None,
            focus_handle: cx.focus_handle().tab_stop(true),
        }
    }

    pub fn focus_handle(&self) -> &FocusHandle {
        &self.focus_handle
    }

    pub fn trigger_install(&mut self, cx: &mut Context<Self>) {
        let cancel_flag = Arc::new(AtomicBool::new(false));
        self.active_cancel_flag = Some(Arc::clone(&cancel_flag));
        self.state.prepare_for_install();
        cx.notify();

        if let Some(ref tx_cmd) = self.command_sender {
            let opts = InstallOptions {
                target_dir: self.state.install_dir.clone(),
                create_desktop_shortcut: self.state.create_desktop_shortcut,
                start_with_windows: self.state.start_with_windows,
                clean_install: self.state.clean_install,
            };
            let _ = tx_cmd.send(InstallerCommand::StartInstall {
                options: opts,
                cancel_flag,
            });
        }
    }

    pub fn request_cancel_install(&mut self, cx: &mut Context<Self>) {
        if let Some(ref flag) = self.active_cancel_flag {
            flag.store(true, Ordering::Relaxed);
            self.state.is_cancelling = true;
            self.state.status_message = "Cancelling & rolling back partial changes...".to_string();
            cx.notify();
        } else {
            cx.quit();
        }
    }

    pub(super) fn normalize_picked_directory(picked: PathBuf) -> PathBuf {
        if picked.join("Monitor.exe").is_file()
            || picked
                .file_name()
                .is_some_and(|n| n.to_string_lossy().eq_ignore_ascii_case("Monitor"))
        {
            return picked;
        }
        let has_other_entries = std::fs::read_dir(&picked)
            .ok()
            .and_then(|mut it| it.next())
            .is_some();
        if has_other_entries {
            picked.join("Monitor")
        } else {
            picked
        }
    }

    pub(super) fn launch_installed_app_and_quit(&self, cx: &mut Context<Self>) {
        if self.state.launch_on_finish {
            let target_exe = self.state.install_dir.join("Monitor.exe");
            if target_exe.is_file() {
                let _ = std::process::Command::new(&target_exe)
                    .current_dir(&self.state.install_dir)
                    .spawn();
            }
        }
        cx.quit();
    }

    pub(super) fn open_install_folder(dir: &Path) {
        #[cfg(windows)]
        {
            let explorer = resolve_windows_system_exe("explorer.exe");
            let _ = std::process::Command::new(explorer).arg(dir).spawn();
        }
        #[cfg(not(windows))]
        {
            let _ = dir;
        }
    }

    pub(super) fn handle_key_down(
        &mut self,
        event: &KeyDownEvent,
        _window: &mut Window,
        cx: &mut Context<Self>,
    ) {
        let key = event.keystroke.key.as_str();
        if key.eq_ignore_ascii_case("escape") {
            if self.state.is_uninstall_mode {
                if !self.state.is_uninstalling {
                    cx.quit();
                }
            } else if self.state.is_installing {
                self.request_cancel_install(cx);
            } else {
                cx.quit();
            }
            return;
        }

        if key.eq_ignore_ascii_case("enter") && !self.state.is_uninstall_mode {
            match self.state.current_step {
                InstallerStep::Welcome => {
                    self.state.go_to_step(InstallerStep::License);
                    cx.notify();
                }
                InstallerStep::License if self.state.eula_agreed => {
                    self.state.refresh_location_checks();
                    self.state.go_to_step(InstallerStep::Location);
                    cx.notify();
                }
                InstallerStep::Location if self.state.can_proceed_from_location() => {
                    self.state.go_to_step(InstallerStep::Options);
                    cx.notify();
                }
                InstallerStep::Options => {
                    self.trigger_install(cx);
                }
                InstallerStep::Finished => {
                    self.launch_installed_app_and_quit(cx);
                }
                _ => {}
            }
        }
    }
}
