async fn execute_kill_agent_session_command(
    state: &AppState,
    cmd: &PendingCommand,
) -> CommandResult {
    let session_id = cmd
        .payload
        .get("session_id")
        .and_then(serde_json::Value::as_str);
    let Some(session_id) = session_id else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Missing 'session_id' in payload".to_string()),
        };
    };

    let delete_allowed = state
        .storage
        .get_setting("allow_delete_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !delete_allowed {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Destructive delete/kill operations disabled by desktop security policy".to_string()),
        };
    }

    tracing::info!("Executing remote emergency stop on session '{session_id}'");
    match crate::watchdog_handlers::execute_surgical_kill_session(state, session_id) {
        Ok(val) => CommandResult {
            id: cmd.id.clone(),
            status: "completed".to_string(),
            result: Some(val.to_string()),
        },
        Err(e) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(e.to_string()),
        },
    }
}
