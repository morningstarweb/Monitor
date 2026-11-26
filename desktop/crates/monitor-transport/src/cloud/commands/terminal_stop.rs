async fn execute_terminal_stop_command(state: &AppState, cmd: &PendingCommand) -> CommandResult {
    let terminal_allowed = state
        .storage
        .get_setting("allow_remote_terminal")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !terminal_allowed {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Remote terminal execution is disabled in host settings".to_string()),
        };
    }

    let repo_id_str = cmd
        .payload
        .get("repo_id")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");
    let session_id = cmd
        .payload
        .get("session_id")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("1");

    match crate::terminal_handlers::stop_terminal_session_impl(state, repo_id_str, session_id)
        .await
    {
        Ok(res) if res.success => CommandResult {
            id: cmd.id.clone(),
            status: "completed".to_string(),
            result: Some(format!(
                "Terminated terminal session {} (PID {:?})",
                session_id, res.pid
            )),
        },
        Ok(_) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(format!(
                "No active terminal process found for {repo_id_str}:{session_id}"
            )),
        },
        Err(status) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(format!("Failed stopping terminal session: {status}")),
        },
    }
}
