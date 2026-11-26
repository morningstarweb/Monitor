pub mod actions;
pub mod device_card;
pub mod dwm;
pub mod footer;
pub mod header;
pub mod icons;
pub mod panels;
pub mod qr;
pub mod state;
pub mod theme;
pub mod tiles;

use crate::client::Command;
use gpui_kit::*;
use std::sync::mpsc::Sender;

use actions::render_actions;
use device_card::render_device_card;
use footer::render_footer;
use header::render_header;
use panels::*;
pub use state::FlyoutState;
use theme::FlyoutTheme;
use tiles::render_tiles;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum ActivePanel {
    #[default]
    Main,
    MobileDevices,
    Cloud,
    Projects,
    SystemMonitor,
    Permissions,
    Settings,
    Notifications,
}

pub struct MonitorFlyout {
    pub state: FlyoutState,
    pub command_sender: Option<Sender<Command>>,
    pub active_panel: ActivePanel,
    pub show_qr_code: bool,
    pub selected_otp_device_id: Option<String>,
    pub has_been_active: bool,
    pub _activation_sub: Subscription,
}

impl MonitorFlyout {
    pub fn new(
        window: &mut Window,
        cx: &mut Context<Self>,
        state: FlyoutState,
        command_sender: Option<Sender<Command>>,
    ) -> Self {
        dwm::remove_window_border(window);

        let _activation_sub = cx.observe_window_activation(window, |this, window, _| {
            if window.is_window_active() {
                dwm::remove_window_border(window);
                this.has_been_active = true;
            } else if this.has_been_active {
                window.remove_window();
            }
        });

        Self {
            state,
            command_sender,
            active_panel: ActivePanel::Main,
            show_qr_code: false,
            selected_otp_device_id: None,
            has_been_active: false,
            _activation_sub,
        }
    }

    pub fn update_state(&mut self, mut state: FlyoutState) {
        state.show_qr_code = self.show_qr_code;
        self.state = state;
    }
}

impl Render for MonitorFlyout {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let state = self.state.clone();
        let active = self.active_panel;
        let theme = FlyoutTheme::for_mode(state.is_dark_mode);

        // Classy layered architecture: Outer square glass frame with floating rounded card
        div().size_full().p(px(6.0)).child(
            div()
                .size_full()
                .flex()
                .flex_col()
                .gap(px(6.0))
                .p(px(10.0))
                .rounded(px(16.0))
                .bg(theme.glass_bg)
                .border_1()
                .border_color(theme.glass_border)
                .shadow_lg()
                .overflow_hidden()
                .child(match active {
                    ActivePanel::Main => div()
                        .flex()
                        .flex_col()
                        .gap(px(6.0))
                        .w_full()
                        .h_full()
                        .child(render_header(
                            &state,
                            cx.listener(|this, _, _, cx| {
                                this.active_panel = ActivePanel::Notifications;
                                cx.notify();
                            }),
                            cx.listener(|this, _, _, cx| {
                                this.active_panel = ActivePanel::Settings;
                                cx.notify();
                            }),
                        ))
                        .child(render_device_card(&state))
                        .child(render_tiles(
                            &state,
                            cx.listener(|this, _, _, cx| {
                                this.active_panel = ActivePanel::MobileDevices;
                                this.show_qr_code = false;
                                this.selected_otp_device_id = None;
                                cx.notify();
                            }),
                            cx.listener(|this, _, _, cx| {
                                this.active_panel = ActivePanel::Cloud;
                                cx.notify();
                            }),
                            cx.listener(|this, _, _, cx| {
                                this.active_panel = ActivePanel::Projects;
                                cx.notify();
                            }),
                            cx.listener(|this, _, _, cx| {
                                this.active_panel = ActivePanel::SystemMonitor;
                                cx.notify();
                            }),
                        ))
                        .child(render_actions(
                            &state,
                            cx.listener(|this, _, _, cx| {
                                this.active_panel = ActivePanel::Permissions;
                                cx.notify();
                            }),
                            cx.listener(|this, _, _, cx| {
                                let new_val = !this.state.autostart_enabled;
                                let _ = monitor_tray::set_auto_start(new_val);
                                this.state.autostart_enabled = new_val;
                                cx.notify();
                            }),
                            cx.listener(|_, _, _, _| {
                                let dir = monitor_agent::data_directory();
                                #[cfg(windows)]
                                let _ = std::process::Command::new("explorer").arg(dir).spawn();
                            }),
                            cx.listener(|this, _, _, cx| {
                                if let Some(ref tx) = this.command_sender {
                                    let _ = tx.send(Command::Refresh);
                                }
                                cx.notify();
                            }),
                        ))
                        .child(render_footer(state.is_dark_mode, cx))
                        .into_any_element(),
                    ActivePanel::MobileDevices => render_mobile_panel(
                        &state,
                        self.show_qr_code,
                        self.selected_otp_device_id.as_deref(),
                        &self.command_sender,
                        cx,
                        cx.listener(|this, _, _, cx| {
                            this.active_panel = ActivePanel::Main;
                            this.show_qr_code = false;
                            this.selected_otp_device_id = None;
                            cx.notify();
                        }),
                    )
                    .into_any_element(),
                    ActivePanel::Cloud => render_cloud_panel(
                        &state,
                        &self.command_sender,
                        cx,
                        cx.listener(|this, _, _, cx| {
                            this.active_panel = ActivePanel::Main;
                            cx.notify();
                        }),
                    )
                    .into_any_element(),
                    ActivePanel::Projects => render_projects_panel(
                        &state,
                        &self.command_sender,
                        cx,
                        cx.listener(|this, _, _, cx| {
                            this.active_panel = ActivePanel::Main;
                            cx.notify();
                        }),
                    )
                    .into_any_element(),
                    ActivePanel::SystemMonitor => render_sysmon_panel(
                        &state,
                        &self.command_sender,
                        cx,
                        cx.listener(|this, _, _, cx| {
                            this.active_panel = ActivePanel::Main;
                            cx.notify();
                        }),
                    )
                    .into_any_element(),
                    ActivePanel::Permissions => render_permissions_panel(
                        &state,
                        &self.command_sender,
                        cx,
                        cx.listener(|this, _, _, cx| {
                            this.active_panel = ActivePanel::Main;
                            cx.notify();
                        }),
                    )
                    .into_any_element(),
                    ActivePanel::Settings => render_settings_panel(
                        &state,
                        &self.command_sender,
                        cx,
                        cx.listener(|this, _, _, cx| {
                            this.active_panel = ActivePanel::Main;
                            cx.notify();
                        }),
                    )
                    .into_any_element(),
                    ActivePanel::Notifications => render_notifications_panel(
                        &state,
                        &self.command_sender,
                        cx,
                        cx.listener(|this, _, _, cx| {
                            this.active_panel = ActivePanel::Main;
                            cx.notify();
                        }),
                    )
                    .into_any_element(),
                }),
        )
    }
}
