#[cfg(windows)]
use super::job_object::ACTIVE_TERMINAL_JOBS;
use super::registry::{ACTIVE_TERMINALS, abort_terminal_tasks, kill_process_tree, monitor_key};
use super::stream::read_bounded;
use super::{COMMAND_TIMEOUT_SECS, MAX_OUTPUT_BYTES};
use crate::state::AuthenticatedDevice;
use axum::{
    Extension, Json,
    extract::{Path as AxumPath, State},
    http::StatusCode,
};
use monitor_core::{
    DeviceRole, RepoId, ServerEvent, TerminalCommandResult, TerminalSettingPayload,
    TerminalStopRequest, TerminalStopResult,
};
use std::time::Instant;
use tokio::time::{Duration, timeout};

pub(crate) async fn stop_terminal_session_impl(
    state: &crate::state::AppState,
    repo_id_str: &str,
    session_id: &str,
) -> Result<TerminalStopResult, StatusCode> {
    let session_key = format!("{repo_id_str}:{session_id}");

    #[cfg(windows)]
    let job_opt = ACTIVE_TERMINAL_JOBS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .remove(&session_key);
    let pid_opt = ACTIVE_TERMINALS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .remove(&session_key);
    abort_terminal_tasks(&session_key);
    abort_terminal_tasks(&monitor_key(&session_key));
    #[cfg(windows)]
    if let Some(job) = job_opt {
        job.terminate_all();
    }
    let Some(pid) = pid_opt else {
        return Ok(TerminalStopResult {
            success: false,
            pid: None,
        });
    };

    kill_process_tree(pid).await;

    let repo_id = RepoId::new(repo_id_str);
    let _ = state.event_tx.send(ServerEvent::TerminalOutput {
        repo_id,
        session_id: session_id.to_string(),
        text: "^C\n[Process terminated by user]".to_string(),
    });

    let _ = state.storage.insert_audit_entry(
        "remote_terminal",
        "stop_command",
        &format!("{repo_id_str}:PID{pid}"),
        "stopped",
    );

    Ok(TerminalStopResult {
        success: true,
        pid: Some(pid),
    })
}

pub async fn stop_project_terminal_handler(
    State(state): State<crate::state::AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(repo_id_str): AxumPath<String>,
    Json(req): Json<TerminalStopRequest>,
) -> Result<Json<TerminalStopResult>, StatusCode> {
    if device.role == DeviceRole::Viewer {
        return Err(StatusCode::FORBIDDEN);
    }
    let session_id = req.session_id.unwrap_or_else(|| "1".to_string());
    Ok(Json(
        stop_terminal_session_impl(&state, &repo_id_str, &session_id).await?,
    ))
}

pub async fn get_terminal_setting_handler(
    State(state): State<crate::state::AppState>,
) -> Result<Json<TerminalSettingPayload>, StatusCode> {
    let enabled = state
        .storage
        .get_setting("allow_remote_terminal")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    Ok(Json(TerminalSettingPayload { enabled }))
}

pub async fn set_terminal_setting_handler(
    State(state): State<crate::state::AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    Json(req): Json<TerminalSettingPayload>,
) -> Result<Json<TerminalSettingPayload>, StatusCode> {
    if device.role != DeviceRole::Operator || device.device_id.as_str() != "local_desktop" {
        return Err(StatusCode::FORBIDDEN);
    }
    let val = if req.enabled { "1" } else { "0" };
    state
        .storage
        .set_setting("allow_remote_terminal", val)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(req))
}

pub async fn execute_terminal_in_dir(
    dir: &std::path::Path,
    command_str: &str,
    shell: Option<&str>,
) -> anyhow::Result<TerminalCommandResult> {
    let start = Instant::now();

    #[cfg(windows)]
    let mut cmd = if shell == Some("cmd") {
        let mut c = tokio::process::Command::new("cmd.exe");
        c.args(["/d", "/c", command_str]);
        c
    } else {
        let mut c = tokio::process::Command::new("powershell.exe");
        c.args([
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            command_str,
        ]);
        c
    };

    #[cfg(not(windows))]
    let mut cmd = {
        let mut c = tokio::process::Command::new(shell.unwrap_or("sh"));
        c.args(["-c", command_str]);
        c
    };

    cmd.current_dir(dir);
    cmd.stdout(std::process::Stdio::piped());
    cmd.stderr(std::process::Stdio::piped());

    let mut child = cmd.spawn()?;
    let mut stdout_pipe = child.stdout.take();
    let mut stderr_pipe = child.stderr.take();

    let read_stdout = async {
        if let Some(pipe) = stdout_pipe.as_mut() {
            let mut out = Vec::new();
            let _ = read_bounded(pipe, &mut out, MAX_OUTPUT_BYTES).await;
            out
        } else {
            Vec::new()
        }
    };
    let read_stderr = async {
        if let Some(pipe) = stderr_pipe.as_mut() {
            let mut err = Vec::new();
            let _ = read_bounded(pipe, &mut err, MAX_OUTPUT_BYTES).await;
            err
        } else {
            Vec::new()
        }
    };

    let (stdout_bytes, stderr_bytes, status) =
        match timeout(Duration::from_secs(COMMAND_TIMEOUT_SECS), async {
            let (out, err) = tokio::join!(read_stdout, read_stderr);
            let status = child.wait().await?;
            Ok::<_, std::io::Error>((out, err, status))
        })
        .await
        {
            Ok(Ok(result)) => result,
            Ok(Err(error)) => return Err(error.into()),
            Err(_) => {
                let _ = child.kill().await;
                anyhow::bail!("Command timed out after {COMMAND_TIMEOUT_SECS} seconds");
            }
        };

    let duration_ms = start.elapsed().as_millis() as u64;
    let exit_code = status.code().unwrap_or(-1);
    let mut stdout = String::from_utf8_lossy(&stdout_bytes).to_string();
    let mut stderr = String::from_utf8_lossy(&stderr_bytes).to_string();

    if stdout.len() > MAX_OUTPUT_BYTES {
        stdout.truncate(MAX_OUTPUT_BYTES);
        stdout.push_str("\n[Output truncated at 128KB]");
    }
    if stderr.len() > MAX_OUTPUT_BYTES {
        stderr.truncate(MAX_OUTPUT_BYTES);
        stderr.push_str("\n[Stderr truncated at 128KB]");
    }

    Ok(TerminalCommandResult {
        exit_code,
        stdout,
        stderr,
        duration_ms,
    })
}
