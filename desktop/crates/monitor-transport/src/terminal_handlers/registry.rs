use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};
use tokio::task::AbortHandle;

/// Serializes session creation/teardown so two concurrent requests with the
/// same session key cannot both take ownership of a process slot (NEW-11).
pub(super) static TERMINAL_LIFECYCLE: LazyLock<tokio::sync::Mutex<()>> =
    LazyLock::new(|| tokio::sync::Mutex::new(()));

/// Active child processes indexed by "repo_id:session_id" -> PID
pub(super) static ACTIVE_TERMINALS: LazyLock<Mutex<HashMap<String, u32>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

/// Owned abort handles for reader and completion-monitor tasks (AUD-07).
static TERMINAL_TASKS: LazyLock<Mutex<HashMap<String, Vec<AbortHandle>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

pub(super) fn register_terminal_task(session_key: &str, handle: AbortHandle) {
    TERMINAL_TASKS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .entry(session_key.to_string())
        .or_default()
        .push(handle);
}

pub(super) fn abort_terminal_tasks(session_key: &str) {
    if let Some(handles) = TERMINAL_TASKS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .remove(session_key)
    {
        for handle in handles {
            handle.abort();
        }
    }
}

/// Registry key for the completion-monitor task of a session (AUD-07).
pub(super) fn monitor_key(session_key: &str) -> String {
    format!("{session_key}#monitor")
}

/// Remove handles without aborting them.
pub(super) fn unregister_terminal_tasks(session_key: &str) {
    TERMINAL_TASKS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .remove(session_key);
}

/// Remove a PID from the registry only if it still belongs to this session owner (NEW-11).
pub(super) fn remove_owned_pid(session_key: &str, pid: u32) {
    let mut sessions = ACTIVE_TERMINALS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if sessions
        .get(session_key)
        .is_some_and(|&current| current == pid)
    {
        sessions.remove(session_key);
    }
}

/// Best-effort kill of a child process (and, on Windows, its descendants).
pub(super) async fn kill_process_tree(pid: u32) {
    #[cfg(windows)]
    let _ = tokio::process::Command::new("taskkill")
        .args(["/F", "/T", "/PID", &pid.to_string()])
        .output()
        .await;

    #[cfg(not(windows))]
    let _ = tokio::process::Command::new("kill")
        .args(["-9", &pid.to_string()])
        .output()
        .await;
}

/// Abort all terminal reader/monitor tasks. Called during agent shutdown.
pub fn abort_all_terminal_tasks() {
    let handles: Vec<AbortHandle> = TERMINAL_TASKS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .values()
        .flatten()
        .cloned()
        .collect();
    for handle in handles {
        handle.abort();
    }
}
