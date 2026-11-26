use super::MonitorInstallerWindow;
use super::finish::render_finish_page;
use super::license::render_license_page;
use super::location::render_location_page;
use super::options::{self, render_options_page};
use super::progress::render_progress_page;
use super::sidebar::render_sidebar;
use super::stepper::render_stepper;
use super::titlebar::{minimize_window, render_titlebar};
use super::uninstaller::render_uninstaller_page;
use super::welcome::render_welcome_page;
use crate::state::{InstallerStep, LegalDocument};
use gpui_kit::{prelude::FluentBuilder, *};
use std::path::{Path, PathBuf};

impl Render for MonitorInstallerWindow {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let theme = self.theme;
        let step = self.state.current_step;
        let highest_step = self.state.highest_step;
        let is_uninstall = self.state.is_uninstall_mode;
        let is_upgrade = self.state.is_upgrade_target();
        let title = if is_uninstall {
            concat!("Monitor Uninstaller v", env!("CARGO_PKG_VERSION"))
        } else if is_upgrade {
            concat!("Monitor Setup v", env!("CARGO_PKG_VERSION"), " · Update")
        } else {
            concat!("Monitor Setup v", env!("CARGO_PKG_VERSION"))
        };
        let mode_label = if is_uninstall {
            "Uninstaller"
        } else if is_upgrade {
            "Upgrade Ready"
        } else {
            "Fresh Setup"
        };

        let uninstall_dir = self.state.uninstall_dir.clone();
        let wipe_data = self.state.uninstall_wipe_data;
        let uninstall_done = self.state.uninstall_done;
        let is_uninstalling = self.state.is_uninstalling;
        let uninstall_error = self.state.uninstall_error.clone();

        let eula_agreed = self.state.eula_agreed;
        let active_doc = self.state.active_legal_doc;
        let install_dir = self.state.install_dir.clone();
        let path_error = self.state.path_error.clone();
        let path_warning = self.state.path_warning.clone();
        let required_bytes = self.state.required_space_bytes;
        let available_bytes = self.state.available_space_bytes;
        let has_space = self.state.has_sufficient_disk_space();
        let existing_install = self.state.existing_install.clone();
        let desktop_shortcut = self.state.create_desktop_shortcut;
        let start_with_windows = self.state.start_with_windows;
        let clean_install = self.state.clean_install;
        let progress_pct = self.state.install_progress;
        let status_msg = self.state.status_message.clone();
        let files_progress = self.state.files_progress;
        let task_host = self.state.task_host_done;
        let task_git = self.state.task_git_done;
        let task_shortcuts = self.state.task_shortcuts_done;
        let is_installing = self.state.is_installing;
        let is_cancelling = self.state.is_cancelling;
        let install_error = self.state.install_error.clone();
        let launch_now = self.state.launch_on_finish;
        let finished_upgrade = self.state.installed_is_upgrade;
        let tx = self.command_sender.clone();

        div()
            .id("installer-window-root")
            .track_focus(&self.focus_handle)
            .on_key_down(cx.listener(Self::handle_key_down))
            .flex()
            .flex_col()
            .w_full()
            .h_full()
            .bg(theme.window_bg)
            .border_1()
            .border_color(theme.card_border)
            .overflow_hidden()
            .child(render_titlebar(
                title,
                &theme,
                |_, w, _| minimize_window(w),
                cx.listener(|this, _, _, cx| {
                    if this.state.is_installing {
                        this.request_cancel_install(cx);
                    } else if !this.state.is_uninstalling {
                        cx.quit();
                    }
                }),
            ))
            .child(
                div()
                    .flex()
                    .flex_row()
                    .w_full()
                    .flex_1()
                    .min_h(px(0.0))
                    .overflow_hidden()
                    .child(render_sidebar(&theme, mode_label))
                    .child(
                        div()
                            .flex()
                            .flex_col()
                            .flex_1()
                            .min_w(px(0.0))
                            .h_full()
                            .px(px(26.0))
                            .py(px(16.0))
                            .bg(theme.content_bg)
                            .overflow_hidden()
                            .map(|el| {
                                if is_uninstall {
                                    let tx_uninst = tx.clone();
                                    let target_uninst = uninstall_dir.clone();
                                    el.child(render_uninstaller_page(
                                        &uninstall_dir,
                                        wipe_data,
                                        uninstall_done,
                                        is_uninstalling,
                                        uninstall_error.as_deref(),
                                        &theme,
                                        cx.listener(|this, _, _, cx| {
                                            if !this.state.is_uninstalling {
                                                cx.quit();
                                            }
                                        }),
                                        cx.listener(|this, _, _, cx| {
                                            this.state.uninstall_wipe_data =
                                                !this.state.uninstall_wipe_data;
                                            cx.notify();
                                        }),
                                        cx.listener(move |this, _, _, cx| {
                                            this.state.is_uninstalling = true;
                                            this.state.uninstall_error = None;
                                            cx.notify();
                                            if let Some(ref tx_cmd) = tx_uninst {
                                                let _ = tx_cmd.send(
                                                    super::InstallerCommand::StartUninstall {
                                                        install_dir: target_uninst.clone(),
                                                        wipe_data: this.state.uninstall_wipe_data,
                                                    },
                                                );
                                            }
                                        }),
                                    ))
                                } else {
                                    el.child(render_stepper(
                                        step,
                                        highest_step,
                                        is_installing || step == InstallerStep::Finished,
                                        &theme,
                                        cx.listener(|this, target_step: &InstallerStep, _, cx| {
                                            if *target_step == InstallerStep::Location {
                                                this.state.refresh_location_checks();
                                            }
                                            this.state.go_to_step(*target_step);
                                            cx.notify();
                                        }),
                                    ))
                                    .child(
                                        div()
                                            .flex()
                                            .flex_col()
                                            .flex_1()
                                            .min_h(px(0.0))
                                            .w_full()
                                            .mt(px(8.0))
                                            .overflow_hidden()
                                            .child(match step {
                                                InstallerStep::Welcome => render_welcome_page(
                                                    existing_install.as_ref(),
                                                    &theme,
                                                    cx.listener(|_, _, _, cx| cx.quit()),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.go_to_step(InstallerStep::License);
                                                        cx.notify();
                                                    }),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.trigger_install(cx);
                                                    }),
                                                )
                                                .into_any_element(),
                                                InstallerStep::License => render_license_page(
                                                    eula_agreed,
                                                    active_doc,
                                                    is_upgrade,
                                                    &theme,
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.go_to_step(InstallerStep::Welcome);
                                                        cx.notify();
                                                    }),
                                                    cx.listener(|_, _, _, cx| cx.quit()),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.refresh_location_checks();
                                                        this.state.go_to_step(InstallerStep::Location);
                                                        cx.notify();
                                                    }),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.eula_agreed =
                                                            !this.state.eula_agreed;
                                                        cx.notify();
                                                    }),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.active_legal_doc =
                                                            LegalDocument::Eula;
                                                        cx.notify();
                                                    }),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.active_legal_doc =
                                                            LegalDocument::Privacy;
                                                        cx.notify();
                                                    }),
                                                )
                                                .into_any_element(),
                                                InstallerStep::Location => render_location_page(
                                                    &install_dir,
                                                    required_bytes,
                                                    available_bytes,
                                                    install_dir.join("Monitor.exe").is_file(),
                                                    has_space,
                                                    path_error.as_deref(),
                                                    path_warning.as_deref(),
                                                    &theme,
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.go_to_step(InstallerStep::License);
                                                        cx.notify();
                                                    }),
                                                    cx.listener(|_, _, _, cx| cx.quit()),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.state.go_to_step(InstallerStep::Options);
                                                        cx.notify();
                                                    }),
                                                    cx.listener(|this, _, _, cx| {
                                                        let start_dir = if this.state.install_dir.exists() {
                                                            this.state.install_dir.clone()
                                                        } else {
                                                            this.state
                                                                .install_dir
                                                                .parent()
                                                                .map(Path::to_path_buf)
                                                                .unwrap_or_else(|| PathBuf::from("C:\\"))
                                                        };
                                                        if let Some(folder) = rfd::FileDialog::new()
                                                            .set_title("Choose installation folder for Monitor")
                                                            .set_directory(&start_dir)
                                                            .pick_folder()
                                                        {
                                                            let normalized =
                                                                Self::normalize_picked_directory(folder);
                                                            this.state.install_dir = normalized;
                                                            this.state.refresh_location_checks();
                                                            cx.notify();
                                                        }
                                                    }),
                                                )
                                                .into_any_element(),
                                                InstallerStep::Options => {
                                                    let flags = options::InstallOptionFlags {
                                                        desktop_shortcut,
                                                        start_with_windows,
                                                        clean_install,
                                                        is_upgrade: install_dir
                                                            .join("Monitor.exe")
                                                            .is_file(),
                                                    };
                                                    let callbacks = options::OptionCallbacks {
                                                        on_back: cx.listener(|this, _, _, cx| {
                                                            this.state.go_to_step(
                                                                InstallerStep::Location,
                                                            );
                                                            cx.notify();
                                                        }),
                                                        on_cancel: cx
                                                            .listener(|_, _, _, cx| cx.quit()),
                                                        on_install: cx.listener(
                                                            move |this, _, _, cx| {
                                                                this.trigger_install(cx);
                                                            },
                                                        ),
                                                        on_toggle_desktop: cx.listener(
                                                            |this, _, _, cx| {
                                                                this.state.create_desktop_shortcut =
                                                                    !this.state.create_desktop_shortcut;
                                                                cx.notify();
                                                            },
                                                        ),
                                                        on_toggle_startup: cx.listener(
                                                            |this, _, _, cx| {
                                                                this.state.start_with_windows =
                                                                    !this.state.start_with_windows;
                                                                cx.notify();
                                                            },
                                                        ),
                                                        on_toggle_clean: cx.listener(
                                                            |this, _, _, cx| {
                                                                this.state.clean_install =
                                                                    !this.state.clean_install;
                                                                cx.notify();
                                                            },
                                                        ),
                                                    };
                                                    render_options_page(
                                                        &install_dir,
                                                        flags,
                                                        &theme,
                                                        callbacks,
                                                    )
                                                    .into_any_element()
                                                }
                                                InstallerStep::Installing => render_progress_page(
                                                    progress_pct,
                                                    &status_msg,
                                                    files_progress,
                                                    task_host,
                                                    task_git,
                                                    task_shortcuts,
                                                    is_cancelling,
                                                    install_error.as_deref(),
                                                    &theme,
                                                    cx.listener(|this, _, _, cx| {
                                                        this.request_cancel_install(cx);
                                                    }),
                                                    cx.listener(|this, _, _, cx| {
                                                        this.trigger_install(cx);
                                                    }),
                                                    cx.listener(|_, _, _, cx| cx.quit()),
                                                )
                                                .into_any_element(),
                                                InstallerStep::Finished => {
                                                    let open_dir = install_dir.clone();
                                                    render_finish_page(
                                                        &install_dir,
                                                        launch_now,
                                                        finished_upgrade,
                                                        &theme,
                                                        cx.listener(move |this, _, _, cx| {
                                                            this.launch_installed_app_and_quit(cx);
                                                        }),
                                                        cx.listener(move |_, _, _, _| {
                                                            Self::open_install_folder(&open_dir);
                                                        }),
                                                        cx.listener(|this, _, _, cx| {
                                                            this.state.launch_on_finish =
                                                                !this.state.launch_on_finish;
                                                            cx.notify();
                                                        }),
                                                    )
                                                    .into_any_element()
                                                }
                                            }),
                                    )
                                }
                            }),
                    ),
            )
    }
}
