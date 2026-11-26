use super::matching::matches_profile_with_exe;
use monitor_storage::StoredAgentProfile;
use std::path::Path;

#[derive(Debug, Clone)]
pub struct UniversalCatalogEntry {
    pub name: &'static str,
    pub matchers: &'static [&'static str],
}

pub const UNIVERSAL_AGENT_CATALOG: &[UniversalCatalogEntry] = &[
    UniversalCatalogEntry {
        name: "Antigravity",
        matchers: &["antigravity", "antigravity ide", "agy"],
    },
    UniversalCatalogEntry {
        name: "Codex",
        matchers: &["codex", "codex-cli", "openai.codex"],
    },
    UniversalCatalogEntry {
        name: "Copilot",
        matchers: &[
            "copilot",
            "github copilot",
            "github-copilot",
            "copilot-agent",
        ],
    },
    UniversalCatalogEntry {
        name: "ChatGPT",
        matchers: &["chatgpt", "openai"],
    },
    UniversalCatalogEntry {
        name: "Claude",
        matchers: &["claude", "claude-code", "claude-cli", "anthropic"],
    },
    UniversalCatalogEntry {
        name: "Gemini",
        matchers: &[
            "language_server",
            "language_server.exe",
            "language_server_windows_x64",
            "gemini",
            "gemini-cli",
            "gemini-agent",
            "google-gemini",
        ],
    },
    UniversalCatalogEntry {
        name: "Cursor",
        matchers: &["cursor", "cursor ide", "cursor.exe"],
    },
    UniversalCatalogEntry {
        name: "Windsurf",
        matchers: &["windsurf", "codeium", "windsurf.exe"],
    },
    UniversalCatalogEntry {
        name: "Aider",
        matchers: &["aider", "aider.chat", "aider-chat"],
    },
    UniversalCatalogEntry {
        name: "Cline / Roo",
        matchers: &["cline", "roo-cline", "roo-code", "roo-code-cli"],
    },
    UniversalCatalogEntry {
        name: "Continue",
        matchers: &["continue", "continue.dev"],
    },
    UniversalCatalogEntry {
        name: "Goose",
        matchers: &["goose", "goose-ai", "goose.exe"],
    },
    UniversalCatalogEntry {
        name: "OpenCode",
        matchers: &["opencode", "open-code", "devin", "opendevin", "mentat"],
    },
    UniversalCatalogEntry {
        name: "IDE Agent",
        matchers: &[
            "code", "code.exe", "idea", "idea64", "pycharm", "webstorm", "studio64", "zed", "fleet",
        ],
    },
];

const SEEDED_DEFAULT_PROFILES: &[&str] = &["claude", "codex", "gemini", "aider", "opencode"];

pub(super) fn classify_process_profile(
    name: &str,
    exe: Option<&Path>,
    cmd: &[std::ffi::OsString],
    profiles: &[StoredAgentProfile],
) -> Option<String> {
    // 1. User-created custom profiles always take priority over built-in rules.
    for profile in profiles {
        if !SEEDED_DEFAULT_PROFILES.contains(&profile.name.as_str())
            && matches_profile_with_exe(name, exe, cmd, &profile.matchers)
        {
            return Some(profile.name.clone());
        }
    }

    // 2. Built-in specific model/IDE catalog (Antigravity before generic Gemini, Codex before ChatGPT).
    for entry in UNIVERSAL_AGENT_CATALOG {
        let matchers_vec: Vec<String> = entry.matchers.iter().map(ToString::to_string).collect();
        if matches_profile_with_exe(name, exe, cmd, &matchers_vec) {
            return Some(entry.name.to_string());
        }
    }

    // 3. Fallback to seeded default DB profiles (only for customized matchers).
    for profile in profiles {
        if matches_profile_with_exe(name, exe, cmd, &profile.matchers) {
            return Some(profile.name.clone());
        }
    }

    None
}

pub fn is_gui_ide(name: &str) -> bool {
    is_gui_ide_proc(name, None)
}

pub(super) fn is_gui_ide_proc(name: &str, exe: Option<&Path>) -> bool {
    let lower = name.to_lowercase();
    if lower == "chatgpt.exe"
        && exe
            .map(|p| p.to_string_lossy().to_lowercase().contains("openai.codex"))
            .unwrap_or(false)
    {
        return true;
    }
    lower == "antigravity.exe"
        || lower == "antigravity ide.exe"
        || lower == "cursor.exe"
        || lower == "code.exe"
        || lower == "windsurf.exe"
        || lower == "idea.exe"
        || lower == "idea64.exe"
        || lower == "pycharm.exe"
        || lower == "pycharm64.exe"
        || lower == "webstorm.exe"
        || lower == "studio64.exe"
        || lower == "zed.exe"
        || lower == "fleet.exe"
}

pub fn is_agent_engine(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.contains("language_server")
        || lower == "agy.exe"
        || lower == "claude.exe"
        || lower == "aider.exe"
        || lower == "goose.exe"
        || lower == "opencode.exe"
        || lower == "codex.exe"
}

pub(super) fn is_ignored_process(name: &str, cmd_parts: &[std::ffi::OsString]) -> bool {
    let lower = name.to_lowercase();
    if lower.contains("monitor-agent")
        || lower.contains("monitor-tray")
        || lower.contains("monitor.exe")
        || lower.contains("crashpad")
        || lower.contains("conhost")
        || lower == "update.exe"
        || lower == "squirrel.exe"
    {
        return true;
    }

    let lower_cmd = cmd_parts
        .iter()
        .map(|s| s.to_string_lossy().to_lowercase())
        .collect::<Vec<_>>()
        .join(" ");

    lower_cmd.contains("--type=gpu-process")
        || lower_cmd.contains("--type=renderer")
        || lower_cmd.contains("--type=utility")
        || lower_cmd.contains("--type=zygote")
        || lower_cmd.contains("--type=broker")
        || lower_cmd.contains("--type=crashpad-handler")
        || lower_cmd.contains("--type=audio.mojom")
        || lower_cmd.contains("--type=video_capture.mojom")
        || lower_cmd.contains("--type=network.mojom")
        || lower_cmd.contains("--service-worker")
}
