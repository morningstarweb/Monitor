use super::catalog::classify_process_profile;
use super::matching::{find_repo_path, matches_profile};
use super::*;

#[test]
fn test_profile_matcher() {
    let matchers = vec!["claude".to_string(), "aider".to_string()];
    let cmd = vec![
        std::ffi::OsString::from("npx"),
        std::ffi::OsString::from("claude"),
        std::ffi::OsString::from("--help"),
    ];
    assert!(matches_profile("node.exe", &cmd, &matchers));
    assert!(matches_profile("claude.exe", &[], &matchers));
    assert!(!matches_profile("notepad.exe", &[], &matchers));
}

#[test]
fn test_universal_catalog_coverage() {
    assert!(
        UNIVERSAL_AGENT_CATALOG
            .iter()
            .any(|c| c.name == "Antigravity")
    );
    assert!(UNIVERSAL_AGENT_CATALOG.iter().any(|c| c.name == "Gemini"));
    assert!(UNIVERSAL_AGENT_CATALOG.iter().any(|c| c.name == "Codex"));
    assert!(UNIVERSAL_AGENT_CATALOG.iter().any(|c| c.name == "Copilot"));
    assert!(UNIVERSAL_AGENT_CATALOG.iter().any(|c| c.name == "Cursor"));
    assert!(UNIVERSAL_AGENT_CATALOG.iter().any(|c| c.name == "Windsurf"));
    assert!(
        !UNIVERSAL_AGENT_CATALOG
            .iter()
            .any(|c| c.name == "ChatGPT / Copilot")
    );
}

#[test]
fn test_multi_agent_detection_preserves_concurrent_models_in_same_repo() {
    let repo = Some("D:\\Monitor".to_string());
    let candidates = vec![
        (101, "Antigravity".to_string(), repo.clone(), 1_000),
        (102, "Codex".to_string(), repo.clone(), 2_000),
        (103, "Claude".to_string(), repo.clone(), 3_000),
        (104, "Codex".to_string(), repo.clone(), 4_000),
    ];
    let deduped = deduplicate_candidates(candidates);
    assert_eq!(deduped.len(), 3);
    let names: Vec<_> = deduped.iter().map(|c| c.1.as_str()).collect();
    assert_eq!(names, vec!["Antigravity", "Codex", "Claude"]);
    assert_eq!(deduped[1].0, 102);
}

#[test]
fn test_codex_chatgpt_exe_and_antigravity_classification() {
    let profiles = vec![StoredAgentProfile {
        name: "gemini".to_string(),
        matchers: vec!["gemini".to_string(), "antigravity".to_string()],
        stall_threshold_s: 600,
        restart_command: None,
        keep_running: false,
        max_retries: 0,
    }];
    let codex_exe = Path::new(r"C:\Program Files\WindowsApps\OpenAI.Codex_1.0\app\ChatGPT.exe");
    assert_eq!(
        classify_process_profile("ChatGPT.exe", Some(codex_exe), &[], &profiles).as_deref(),
        Some("Codex")
    );
    let ls_cmd = vec![
        std::ffi::OsString::from("language_server_windows_x64.exe"),
        std::ffi::OsString::from("--override_ide_name"),
        std::ffi::OsString::from("antigravity"),
    ];
    assert_eq!(
        classify_process_profile("language_server_windows_x64.exe", None, &ls_cmd, &profiles)
            .as_deref(),
        Some("Antigravity")
    );
    let pty_exe = Path::new(r"C:\Users\Developer\.cache\opencode\bin\opencode-pty\opencode-pty.exe");
    let pty_cmd = vec![
        std::ffi::OsString::from(pty_exe),
        std::ffi::OsString::from("daemon"),
    ];
    assert_eq!(
        classify_process_profile("opencode-pty.exe", Some(pty_exe), &pty_cmd, &profiles),
        None
    );
    assert!(find_repo_path(None, &[], &["D:\\Monitor".to_string()]).is_none());
    let ws_cmd = vec![
        std::ffi::OsString::from("language_server_windows_x64.exe"),
        std::ffi::OsString::from("--workspace_id"),
        std::ffi::OsString::from("file_d_Monitor"),
    ];
    assert_eq!(
        find_repo_path(None, &ws_cmd, &["\\\\?\\D:\\Monitor".to_string()]).as_deref(),
        Some("D:\\Monitor")
    );
}
