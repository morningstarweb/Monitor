#[cfg(windows)]
use super::job_object::{ACTIVE_TERMINAL_JOBS, ChildJob};
use super::registry::{
    ACTIVE_TERMINALS, TERMINAL_LIFECYCLE, abort_terminal_tasks, kill_process_tree, monitor_key,
    register_terminal_task, remove_owned_pid, unregister_terminal_tasks,
};
use super::stream::{append_bounded, reserve_output_bytes, stream_bounded_lines};
use super::{
    INITIAL_LOG_WAIT_SECS, MAX_OUTPUT_BYTES, MAX_TERMINAL_SESSIONS, OUTPUT_CHANNEL_CAPACITY,
};
use crate::state::AuthenticatedDevice;
use axum::{
    Extension, Json,
    extract::{Path as AxumPath, State},
    http::StatusCode,
};
use monitor_core::{
    DeviceRole, RepoId, ServerEvent, TerminalCommandRequest, TerminalCommandResult,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::time::Instant;
use tokio::time::Duration;

fn build_project_command(
    path: &std::path::Path,
    command_str: &str,
    shell: Option<&str>,
) -> tokio::process::Command {
    #[cfg(windows)]
    let mut cmd = match shell {
        Some("cmd") => {
            let mut c = tokio::process::Command::new("cmd.exe");
            c.args(["/d", "/c", command_str]);
            c
        }
        Some("pwsh") => {
            let mut c = tokio::process::Command::new("pwsh.exe");
            c.args([
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                command_str,
            ]);
            c
        }
        Some("powershell") => {
            let mut c = tokio::process::Command::new("powershell.exe");
            c.args([
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                command_str,
            ]);
            c
        }
        _ => {
            let trimmed = command_str.trim();
            let is_common_cmd = trimmed.starts_with("dir")
                || trimmed.starts_with("cls")
                || trimmed.starts_with("echo")
                || trimmed.starts_with("cd")
                || trimmed.starts_with("mkdir")
                || trimmed.starts_with("rmdir")
                || trimmed.starts_with("del")
                || trimmed.starts_with("copy")
                || trimmed.starts_with("move")
                || trimmed.starts_with("type");
            if is_common_cmd {
                let mut c = tokio::process::Command::new("cmd.exe");
                c.args(["/d", "/c", command_str]);
                c
            } else {
                let mut c = tokio::process::Command::new("powershell.exe");
                c.args([
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command_str,
                ]);
                c
            }
        }
    };

    #[cfg(not(windows))]
    let mut cmd = {
        let mut c = tokio::process::Command::new(shell.unwrap_or("sh"));
        c.args(["-c", command_str]);
        c
    };

    cmd.current_dir(path);
    cmd.stdout(std::process::Stdio::piped());
    cmd.stderr(std::process::Stdio::piped());
    cmd.kill_on_drop(true);
    cmd
}

pub async fn run_project_terminal_handler(
    State(state): State<crate::state::AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(repo_id_str): AxumPath<String>,
    Json(req): Json<TerminalCommandRequest>,
) -> Result<Json<TerminalCommandResult>, StatusCode> {
    if device.role == DeviceRole::Viewer {
        return Err(StatusCode::FORBIDDEN);
    }

    let terminal_allowed = state
        .storage
        .get_setting("allow_remote_terminal")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !terminal_allowed {
        return Err(StatusCode::FORBIDDEN);
    }

    let repo_id = RepoId::new(&repo_id_str);
    let repo_opt = state.storage.get_repository(&repo_id).unwrap_or(None);
    let path = if let Some(r) = repo_opt {
        std::path::PathBuf::from(&r.canonical_path)
    } else {
        let repos = state.storage.list_repositories().unwrap_or_default();
        if let Some(matched) = repos
            .iter()
            .find(|r| r.display_name.eq_ignore_ascii_case(&repo_id_str))
        {
            std::path::PathBuf::from(&matched.canonical_path)
        } else {
            return Err(StatusCode::NOT_FOUND);
        }
    };
    if !path.exists() || !path.is_dir() {
        return Err(StatusCode::BAD_REQUEST);
    }

    let command_str = req.command.trim();
    if command_str.is_empty() {
        return Ok(Json(TerminalCommandResult {
            exit_code: 0,
            stdout: String::new(),
            stderr: String::new(),
            duration_ms: 0,
        }));
    }

    let session_id = req.session_id.unwrap_or_else(|| "1".to_string());
    let session_key = format!("{}:{}", repo_id_str, session_id);

    let lifecycle_guard = TERMINAL_LIFECYCLE.lock().await;
    let previous_pid = {
        let mut sessions = ACTIVE_TERMINALS
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        if !sessions.contains_key(&session_key) && sessions.len() >= MAX_TERMINAL_SESSIONS {
            return Err(StatusCode::TOO_MANY_REQUESTS);
        }
        sessions.remove(&session_key)
    };
    if let Some(old_pid) = previous_pid {
        abort_terminal_tasks(&session_key);
        abort_terminal_tasks(&monitor_key(&session_key));
        #[cfg(windows)]
        if let Some(old_job) = ACTIVE_TERMINAL_JOBS
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .remove(&session_key)
        {
            old_job.terminate_all();
        }
        kill_process_tree(old_pid).await;
    }

    let start = Instant::now();
    let mut cmd = build_project_command(&path, command_str, req.shell.as_deref());
    let mut child = cmd.spawn().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    let pid = child.id().unwrap_or(0);
    if pid > 0 {
        #[cfg(windows)]
        if let Some(job) = ChildJob::attach_pid(pid) {
            ACTIVE_TERMINAL_JOBS
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .insert(session_key.clone(), Arc::new(job));
        }
        ACTIVE_TERMINALS
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .insert(session_key.clone(), pid);
    }
    drop(lifecycle_guard);

    let stdout_pipe = child.stdout.take();
    let stderr_pipe = child.stderr.take();
    let event_tx = state.event_tx.clone();
    let (stdout_tx, mut rx_out) = tokio::sync::mpsc::channel::<String>(OUTPUT_CHANNEL_CAPACITY);
    let (stderr_tx, mut rx_err) = tokio::sync::mpsc::channel::<String>(OUTPUT_CHANNEL_CAPACITY);
    let output_used = Arc::new(AtomicUsize::new(0));
    let output_overflow = Arc::new(AtomicBool::new(false));
    let mut reader_handles: Vec<tokio::task::JoinHandle<()>> = Vec::new();

    if let Some(stdout_pipe) = stdout_pipe {
        let tx = stdout_tx.clone();
        let budget = Arc::clone(&output_used);
        let overflow = Arc::clone(&output_overflow);
        let evt = event_tx.clone();
        let repo = repo_id.clone();
        let sid = session_id.clone();
        let handle = tokio::spawn(async move {
            stream_bounded_lines(stdout_pipe, &overflow, |line| {
                if !reserve_output_bytes(line.len() + 1, &budget, &overflow) {
                    return;
                }
                if tx.try_send(line.clone()).is_err() {
                    overflow.store(true, Ordering::SeqCst);
                }
                let _ = evt.send(ServerEvent::TerminalOutput {
                    repo_id: repo.clone(),
                    session_id: sid.clone(),
                    text: line,
                });
            })
            .await;
        });
        register_terminal_task(&session_key, handle.abort_handle());
        reader_handles.push(handle);
    }

    if let Some(stderr_pipe) = stderr_pipe {
        let tx = stderr_tx.clone();
        let budget = Arc::clone(&output_used);
        let overflow = Arc::clone(&output_overflow);
        let evt = event_tx.clone();
        let repo = repo_id.clone();
        let sid = session_id.clone();
        let handle = tokio::spawn(async move {
            stream_bounded_lines(stderr_pipe, &overflow, |line| {
                if !reserve_output_bytes(line.len() + 1, &budget, &overflow) {
                    return;
                }
                let text = format!("[stderr] {line}");
                if tx.try_send(text.clone()).is_err() {
                    overflow.store(true, Ordering::SeqCst);
                }
                let _ = evt.send(ServerEvent::TerminalOutput {
                    repo_id: repo.clone(),
                    session_id: sid.clone(),
                    text,
                });
            })
            .await;
        });
        register_terminal_task(&session_key, handle.abort_handle());
        reader_handles.push(handle);
    }

    drop(stdout_tx);
    drop(stderr_tx);

    let mut accumulated_stdout = String::new();
    let mut accumulated_stderr = String::new();
    let mut out_budget = MAX_OUTPUT_BYTES;
    let mut err_budget = MAX_OUTPUT_BYTES;
    let initial_wait = Duration::from_secs(INITIAL_LOG_WAIT_SECS);
    let wait_start = Instant::now();
    let mut exit_status: Option<std::process::ExitStatus> = None;

    while wait_start.elapsed() < initial_wait {
        tokio::select! {
            status_res = child.wait() => {
                match status_res {
                    Ok(st) => { exit_status = Some(st); break; }
                    Err(_) => break,
                }
            }
            _ = tokio::time::sleep(Duration::from_millis(50)) => {
                while let Ok(line) = rx_out.try_recv() {
                    append_bounded(&mut accumulated_stdout, &line, &mut out_budget);
                }
                while let Ok(line) = rx_err.try_recv() {
                    append_bounded(&mut accumulated_stderr, &line, &mut err_budget);
                }
            }
        }
    }

    while let Ok(line) = rx_out.try_recv() {
        append_bounded(&mut accumulated_stdout, &line, &mut out_budget);
    }
    while let Ok(line) = rx_err.try_recv() {
        append_bounded(&mut accumulated_stderr, &line, &mut err_budget);
    }

    if exit_status.is_some() {
        let _ = tokio::time::timeout(Duration::from_millis(200), async {
            for handle in &mut reader_handles {
                let _ = handle.await;
            }
        })
        .await;
        for handle in reader_handles {
            handle.abort();
        }
    } else {
        drop(reader_handles);
    }

    let is_still_running = exit_status.is_none();
    let duration_ms = start.elapsed().as_millis() as u64;
    let exit_code = match exit_status {
        Some(st) => {
            ACTIVE_TERMINALS
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .remove(&session_key);
            st.code().unwrap_or(-1)
        }
        None => 0,
    };

    let mut stdout_str = accumulated_stdout;
    if output_overflow.load(Ordering::SeqCst) {
        stdout_str.push_str("\n[Output truncated at 128KB]");
    }

    if is_still_running {
        if !stdout_str.is_empty() {
            stdout_str.push('\n');
        }
        stdout_str.push_str(&format!("[Process active · PID {pid}]"));

        let session_key_bg = session_key.clone();
        let monitor_registry_key = monitor_key(&session_key);
        let monitor_owned_key = monitor_registry_key.clone();
        let event_tx_bg = event_tx.clone();
        let repo_id_bg = repo_id.clone();
        let session_id_bg = session_id.clone();
        let monitor = tokio::spawn(async move {
            let res = child.wait().await;
            #[cfg(windows)]
            ACTIVE_TERMINAL_JOBS
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .remove(&session_key_bg);
            remove_owned_pid(&session_key_bg, pid);
            abort_terminal_tasks(&session_key_bg);
            unregister_terminal_tasks(&monitor_owned_key);
            let code = res.map(|s| s.code().unwrap_or(0)).unwrap_or(-1);
            let _ = event_tx_bg.send(ServerEvent::TerminalOutput {
                repo_id: repo_id_bg,
                session_id: session_id_bg,
                text: format!("[Process exited with code {code}]"),
            });
        });
        register_terminal_task(&monitor_registry_key, monitor.abort_handle());
    } else {
        #[cfg(windows)]
        ACTIVE_TERMINAL_JOBS
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .remove(&session_key);
        remove_owned_pid(&session_key, pid);
        abort_terminal_tasks(&session_key);
    }

    let _ = state.storage.insert_audit_entry(
        "remote_terminal",
        "execute_command",
        &format!("{}:{}", repo_id_str, command_str),
        if exit_code == 0 { "success" } else { "failed" },
    );

    Ok(Json(TerminalCommandResult {
        exit_code,
        stdout: stdout_str,
        stderr: accumulated_stderr,
        duration_ms,
    }))
}
