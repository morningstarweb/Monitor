use crate::engine::{ExistingInstall, InstallProgressEvent, detect_existing_install};
use std::path::PathBuf;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub enum InstallerStep {
    Welcome = 0,
    License = 1,
    Location = 2,
    Options = 3,
    Installing = 4,
    Finished = 5,
}

impl InstallerStep {
    pub fn index(self) -> usize {
        self as usize
    }

    pub fn total_steps() -> usize {
        6
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LegalDocument {
    Eula,
    Privacy,
}

#[derive(Clone, Debug)]
pub struct InstallerState {
    pub current_step: InstallerStep,
    pub highest_step: InstallerStep,
    pub active_legal_doc: LegalDocument,
    pub eula_agreed: bool,
    pub install_dir: PathBuf,
    pub path_error: Option<String>,
    pub path_warning: Option<String>,
    pub required_space_bytes: u64,
    pub available_space_bytes: Option<u64>,
    pub existing_install: Option<ExistingInstall>,
    pub create_desktop_shortcut: bool,
    pub start_with_windows: bool,
    pub clean_install: bool,
    pub launch_on_finish: bool,

    // Uninstaller State
    pub is_uninstall_mode: bool,
    pub uninstall_dir: PathBuf,
    pub uninstall_wipe_data: bool,
    pub uninstall_done: bool,
    pub is_uninstalling: bool,
    pub uninstall_error: Option<String>,

    // Installation Progress
    pub install_progress: f32,
    pub status_message: String,
    pub files_progress: Option<(u64, u64)>,
    pub task_host_done: bool,
    pub task_git_done: bool,
    pub task_shortcuts_done: bool,
    pub is_installing: bool,
    pub is_cancelling: bool,
    pub install_error: Option<String>,
    pub installed_is_upgrade: bool,
}

impl InstallerState {
    /// Initializes installer state while inspecting the host for an existing Monitor installation.
    pub fn detect_for_host(required_bytes: u64) -> Self {
        let existing = detect_existing_install();
        let mut state = Self {
            required_space_bytes: required_bytes,
            ..Default::default()
        };

        if let Some(ref found) = existing {
            state.install_dir = found.install_dir.clone();
            state.create_desktop_shortcut = found.create_desktop_shortcut;
            state.start_with_windows = found.start_with_windows;
            // Upgrading users already accepted terms on initial installation.
            state.eula_agreed = true;
            state.installed_is_upgrade = true;
        }
        state.refresh_location_checks();
        state.existing_install = existing;
        state.uninstall_dir = crate::engine::uninstall::resolve_uninstall_directory();
        state
    }

    pub fn is_upgrade_target(&self) -> bool {
        self.install_dir.join("Monitor.exe").is_file() || self.existing_install.is_some()
    }

    pub fn refresh_disk_space(&mut self) {
        self.available_space_bytes = crate::engine::available_disk_bytes(&self.install_dir);
    }

    pub fn refresh_location_checks(&mut self) {
        self.refresh_disk_space();
        let validation = crate::engine::validate_install_directory(&self.install_dir);
        self.path_error = validation.error;
        self.path_warning = validation.warning;
    }

    pub fn has_sufficient_disk_space(&self) -> bool {
        match self.available_space_bytes {
            Some(free) => free >= self.required_space_bytes.saturating_add(8 * 1024 * 1024),
            None => true,
        }
    }

    pub fn can_proceed_from_location(&self) -> bool {
        self.has_sufficient_disk_space() && self.path_error.is_none()
    }

    /// Applies progress updates monotonically so step switches never reset progress bars to 0%.
    pub fn apply_progress_event(&mut self, event: InstallProgressEvent) {
        match event {
            InstallProgressEvent::StepStarted { step_index } => {
                if step_index >= 1 {
                    self.task_host_done = true;
                }
                if step_index >= 2 {
                    self.task_host_done = true;
                    self.task_git_done = true;
                }
            }
            InstallProgressEvent::Progress {
                percent,
                message,
                files_done,
            } => {
                let clamped = percent.clamp(0.0, 100.0);
                self.install_progress = self.install_progress.max(clamped);
                if !message.is_empty() {
                    self.status_message = message;
                }
                if files_done.is_some() {
                    self.files_progress = files_done;
                }
                if self.install_progress >= 86.0 {
                    self.task_host_done = true;
                }
                if self.install_progress >= 90.0 {
                    self.task_git_done = true;
                }
            }
            InstallProgressEvent::Completed { is_upgrade, .. } => {
                self.install_progress = 100.0;
                self.status_message = if is_upgrade {
                    "Upgrade completed successfully!".to_string()
                } else {
                    "Installation completed successfully!".to_string()
                };
                self.task_host_done = true;
                self.task_git_done = true;
                self.task_shortcuts_done = true;
                self.is_installing = false;
                self.is_cancelling = false;
                self.install_error = None;
                self.installed_is_upgrade = is_upgrade;
                self.go_to_step(InstallerStep::Finished);
            }
            InstallProgressEvent::Failed { error } => {
                self.is_installing = false;
                self.is_cancelling = false;
                self.install_error = Some(error);
                self.status_message = "Installation failed.".to_string();
            }
            InstallProgressEvent::Cancelled => {
                self.is_installing = false;
                self.is_cancelling = false;
                self.install_error = None;
                self.install_progress = 0.0;
                self.files_progress = None;
                self.task_host_done = false;
                self.task_git_done = false;
                self.task_shortcuts_done = false;
                self.status_message = "Installation cancelled cleanly.".to_string();
                self.current_step = InstallerStep::Options;
            }
        }
    }

    pub fn prepare_for_install(&mut self) {
        self.install_progress = 0.0;
        self.files_progress = None;
        self.task_host_done = false;
        self.task_git_done = false;
        self.task_shortcuts_done = false;
        self.is_installing = true;
        self.is_cancelling = false;
        self.install_error = None;
        self.status_message = "Starting setup...".to_string();
        self.go_to_step(InstallerStep::Installing);
    }

    pub fn go_to_step(&mut self, step: InstallerStep) {
        self.current_step = step;
        if step > self.highest_step {
            self.highest_step = step;
        }
    }
}

impl Default for InstallerState {
    fn default() -> Self {
        let default_dir = default_install_directory();
        Self {
            current_step: InstallerStep::Welcome,
            highest_step: InstallerStep::Welcome,
            active_legal_doc: LegalDocument::Eula,
            eula_agreed: false,
            install_dir: default_dir.clone(),
            path_error: None,
            path_warning: None,
            required_space_bytes: 145 * 1024 * 1024,
            available_space_bytes: None,
            existing_install: None,
            create_desktop_shortcut: true,
            start_with_windows: true,
            clean_install: false,
            launch_on_finish: true,

            is_uninstall_mode: false,
            uninstall_dir: default_dir,
            uninstall_wipe_data: false,
            uninstall_done: false,
            is_uninstalling: false,
            uninstall_error: None,

            install_progress: 0.0,
            status_message: "Ready to install...".to_string(),
            files_progress: None,
            task_host_done: false,
            task_git_done: false,
            task_shortcuts_done: false,
            is_installing: false,
            is_cancelling: false,
            install_error: None,
            installed_is_upgrade: false,
        }
    }
}

pub fn default_install_directory() -> PathBuf {
    if let Ok(local_appdata) = std::env::var("LOCALAPPDATA") {
        PathBuf::from(local_appdata)
            .join("Programs")
            .join("Monitor")
    } else if let Ok(user_profile) = std::env::var("USERPROFILE") {
        PathBuf::from(user_profile)
            .join("AppData")
            .join("Local")
            .join("Programs")
            .join("Monitor")
    } else {
        PathBuf::from(r"C:\Program Files\Monitor")
    }
}
