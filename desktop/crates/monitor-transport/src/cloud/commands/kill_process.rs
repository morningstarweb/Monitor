async fn execute_kill_process_command(state: &AppState, cmd: &PendingCommand) -> CommandResult {
    let pid = cmd
        .payload
        .get("pid")
        .and_then(serde_json::Value::as_u64)
        .map(|p| p as u32);
    let Some(pid) = pid else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Missing 'pid' in payload".to_string()),
        };
    };

    if pid == 0 || pid == 4 || pid == std::process::id() {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Protected system process cannot be terminated".to_string()),
        };
    }

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
            result: Some("Operation disabled by desktop security policy".to_string()),
        };
    }

    match state.telemetry.kill_process(pid) {
        Ok(()) => CommandResult {
            id: cmd.id.clone(),
            status: "completed".to_string(),
            result: Some(format!("Process {pid} terminated")),
        },
        Err(e) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(e.to_string()),
        },
    }
}
