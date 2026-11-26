use std::path::PathBuf;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct InstallerCliArgs {
    pub is_uninstall: bool,
    pub is_silent: bool,
    pub fallback_ui: bool,
    pub show_help: bool,
    pub target_dir: Option<PathBuf>,
    pub clean_install: bool,
    pub wipe_data: bool,
    pub no_desktop_shortcut: bool,
    pub no_startup: bool,
    pub launch_after: Option<bool>,
}

impl InstallerCliArgs {
    pub fn parse<I, S>(args: I, exe_stem: Option<&str>) -> Self
    where
        I: IntoIterator<Item = S>,
        S: AsRef<str>,
    {
        let mut parsed = Self {
            is_uninstall: exe_stem
                .map(|s| s.to_ascii_lowercase().contains("uninstall"))
                .unwrap_or(false),
            is_silent: false,
            fallback_ui: false,
            show_help: false,
            target_dir: None,
            clean_install: false,
            wipe_data: false,
            no_desktop_shortcut: false,
            no_startup: false,
            launch_after: None,
        };

        let mut iter = args.into_iter().skip(1);
        while let Some(arg_ref) = iter.next() {
            let arg = arg_ref.as_ref();
            let lower = arg.to_ascii_lowercase();
            if lower == "--help" || lower == "-h" || lower == "/?" || lower == "/help" {
                parsed.show_help = true;
            } else if lower == "--uninstall" || lower == "/uninstall" {
                parsed.is_uninstall = true;
            } else if lower == "--silent"
                || lower == "-s"
                || lower == "/s"
                || lower == "/verysilent"
            {
                parsed.is_silent = true;
            } else if lower == "--fallback-ui" || lower == "--gdi" || lower == "/fallback-ui" {
                parsed.fallback_ui = true;
            } else if lower == "--clean" || lower == "/clean" {
                parsed.clean_install = true;
            } else if lower == "--wipe-data" || lower == "/wipe-data" {
                parsed.wipe_data = true;
            } else if lower == "--no-desktop" {
                parsed.no_desktop_shortcut = true;
            } else if lower == "--no-startup" {
                parsed.no_startup = true;
            } else if lower == "--launch" {
                parsed.launch_after = Some(true);
            } else if lower == "--no-launch" {
                parsed.launch_after = Some(false);
            } else if let Some(val) = arg.strip_prefix("--dir=") {
                if !val.trim().is_empty() {
                    parsed.target_dir = Some(PathBuf::from(val.trim()));
                }
            } else if let Some(val) = arg.strip_prefix("/D=").or_else(|| arg.strip_prefix("/d=")) {
                if !val.trim().is_empty() {
                    parsed.target_dir = Some(PathBuf::from(val.trim()));
                }
            } else if lower == "--dir"
                && let Some(next_val) = iter.next()
            {
                let trimmed = next_val.as_ref().trim();
                if !trimmed.is_empty() {
                    parsed.target_dir = Some(PathBuf::from(trimmed));
                }
            }
        }

        parsed
    }

    pub fn from_env() -> Self {
        let exe_stem = std::env::current_exe()
            .ok()
            .and_then(|p| p.file_stem().map(|s| s.to_string_lossy().into_owned()));
        Self::parse(std::env::args(), exe_stem.as_deref())
    }
}
