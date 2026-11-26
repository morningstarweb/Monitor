#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use monitor_core::{
    DeviceId, DeviceRole, TerminalCommandRequest, TerminalCommandResult, TerminalStopRequest,
};
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::{
    AppState, auth::TokenAuthenticator, build_router, terminal_handlers::execute_terminal_in_dir,
};
use tempfile::tempdir;
use tower::ServiceExt;

fn process_alive(pid: u32) -> bool {
    #[cfg(windows)]
    {
        let out = std::process::Command::new("tasklist")
            .args(["/FI", &format!("PID eq {pid}")])
            .output()
            .expect("tasklist");
        String::from_utf8_lossy(&out.stdout).contains(&pid.to_string())
    }
    #[cfg(not(windows))]
    {
        std::process::Command::new("kill")
            .args(["-0", &pid.to_string()])
            .status()
            .map(|s| s.success())
            .unwrap_or(false)
    }
}

fn json_request(uri: &str, token: &str, payload: serde_json::Value) -> Request<Body> {
    Request::builder()
        .method("POST")
        .uri(uri)
        .header("Authorization", format!("Bearer {token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(payload.to_string()))
        .unwrap()
}

#[tokio::test]
async fn test_terminal_executes_in_correct_directory() {
    let dir = tempdir().unwrap();
    let dir_path = dir.path();

    #[cfg(windows)]
    let cmd = "Write-Output (Get-Location).Path";
    #[cfg(not(windows))]
    let cmd = "pwd";

    let result = execute_terminal_in_dir(dir_path, cmd, None)
        .await
        .expect("Terminal execution failed");

    assert_eq!(result.exit_code, 0);
    let expected_canon = std::fs::canonicalize(dir_path).unwrap_or_else(|_| dir_path.to_path_buf());
    let actual_trimmed = result.stdout.trim();
    let actual_canon = std::fs::canonicalize(actual_trimmed)
        .unwrap_or_else(|_| std::path::PathBuf::from(actual_trimmed));
    let expected_lower = expected_canon.to_string_lossy().to_lowercase();
    let actual_lower = actual_canon.to_string_lossy().to_lowercase();
    assert!(
        actual_lower == expected_lower || actual_lower.contains(&expected_lower),
        "Expected output to match canonical dir {expected_lower}, got: {} (canonical: {actual_lower})",
        result.stdout
    );
}

#[tokio::test]
async fn test_terminal_captures_exit_code_and_stderr() {
    let dir = tempdir().unwrap();

    #[cfg(windows)]
    let cmd = "Write-Error 'TestError'; exit 42";
    #[cfg(not(windows))]
    let cmd = "echo 'TestError' >&2; exit 42";

    let result = execute_terminal_in_dir(dir.path(), cmd, None)
        .await
        .expect("Terminal execution failed");

    assert_eq!(result.exit_code, 42);
    assert!(
        result.stderr.contains("TestError"),
        "Expected stderr to contain 'TestError', got: {}",
        result.stderr
    );
}

// AUD-07 / NEW-03 / NEW-11: a stopped session must terminate its child, and
// the same session key must be safely reusable afterwards.
#[tokio::test]
async fn stopped_terminal_process_terminates_and_session_key_is_reusable() {
    let storage = Storage::in_memory().unwrap();
    storage.set_setting("allow_local_network", "1").unwrap();
    storage.set_setting("allow_remote_terminal", "1").unwrap();
    let state = AppState::new(
        storage,
        TelemetrySampler::new(),
        monitor_git::GitExecutor::new().unwrap(),
    );
    let repo_dir = tempdir().unwrap();
    state
        .storage
        .register_repo(
            &monitor_core::RepoId::new("lifecycle"),
            &repo_dir.path().to_string_lossy(),
            "lifecycle",
            monitor_core::TrustProfile::Trusted,
        )
        .unwrap();
    let token = "tok_terminal_lifecycle_authorization_1234";
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_terminal_lifecycle"),
            "Lifecycle Operator",
            &TokenAuthenticator::hash_token(token),
            DeviceRole::Operator,
        )
        .unwrap();
    let app = build_router(state.clone());

    #[cfg(windows)]
    let long_command = "Start-Sleep -Seconds 120";
    #[cfg(not(windows))]
    let long_command = "sleep 120";

    let req = json_request(
        "/api/v1/projects/lifecycle/terminal/run",
        token,
        serde_json::to_value(TerminalCommandRequest {
            command: long_command.to_string(),
            shell: Some("powershell".to_string()),
            session_id: Some("lifecycle-session".to_string()),
        })
        .unwrap(),
    );
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let bytes = res.into_body().collect().await.unwrap().to_bytes();
    let result: TerminalCommandResult = serde_json::from_slice(&bytes).unwrap();

    let pid_line = result
        .stdout
        .lines()
        .find(|line| line.contains("[Process active · PID"))
        .expect("long-running session must report an active PID");
    let pid: u32 = pid_line
        .split("PID ")
        .nth(1)
        .unwrap()
        .trim_end_matches(']')
        .trim()
        .parse()
        .unwrap();
    assert!(
        process_alive(pid),
        "child {pid} should be running before stop"
    );

    let stop = json_request(
        "/api/v1/projects/lifecycle/terminal/stop",
        token,
        serde_json::to_value(TerminalStopRequest {
            session_id: Some("lifecycle-session".to_string()),
        })
        .unwrap(),
    );
    let res = app.clone().oneshot(stop).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);

    // Wait briefly for the OS to reap the killed process.
    for _ in 0..20 {
        if !process_alive(pid) {
            break;
        }
        tokio::time::sleep(std::time::Duration::from_millis(100)).await;
    }
    assert!(
        !process_alive(pid),
        "child {pid} must not survive session stop"
    );

    // The same session key can be reused; a short command completes normally.
    #[cfg(windows)]
    let short_command = "Write-Output reused-ok";
    #[cfg(not(windows))]
    let short_command = "echo reused-ok";
    let req = json_request(
        "/api/v1/projects/lifecycle/terminal/run",
        token,
        serde_json::to_value(TerminalCommandRequest {
            command: short_command.to_string(),
            shell: None,
            session_id: Some("lifecycle-session".to_string()),
        })
        .unwrap(),
    );
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let bytes = res.into_body().collect().await.unwrap().to_bytes();
    let result: TerminalCommandResult = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(result.exit_code, 0);
    assert!(result.stdout.contains("reused-ok"));

    // AUD-012: unregistered repo_id MUST return 404 Not Found and never run in daemon CWD.
    let unknown_req = json_request(
        "/api/v1/projects/unregistered_repo_id/terminal/run",
        token,
        serde_json::to_value(TerminalCommandRequest {
            command: short_command.to_string(),
            shell: None,
            session_id: Some("unknown".to_string()),
        })
        .unwrap(),
    );
    let unknown_res = app.oneshot(unknown_req).await.unwrap();
    assert_eq!(unknown_res.status(), StatusCode::NOT_FOUND);
}
