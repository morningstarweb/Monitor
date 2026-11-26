async fn execute_terminal_command(state: &AppState, cmd: &PendingCommand) -> CommandResult {
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
        .and_then(serde_json::Value::as_str);
    let command_str = cmd
        .payload
        .get("command")
        .and_then(serde_json::Value::as_str);
    let shell_str = cmd.payload.get("shell").and_then(serde_json::Value::as_str);

    let Some(repo_id_str) = repo_id_str else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Missing 'repo_id' in payload".to_string()),
        };
    };
    let Some(command_str) = command_str else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Missing 'command' in payload".to_string()),
        };
    };

    let repo_id = RepoId::new(repo_id_str);
    let repo = match state.storage.get_repository(&repo_id) {
        Ok(Some(r)) => r,
        Ok(None) => {
            return CommandResult {
                id: cmd.id.clone(),
                status: "failed".to_string(),
                result: Some(format!("Repository '{repo_id_str}' not found")),
            };
        }
        Err(e) => {
            return CommandResult {
                id: cmd.id.clone(),
                status: "failed".to_string(),
                result: Some(format!("Database error: {e}")),
            };
        }
    };

    let path = std::path::PathBuf::from(&repo.canonical_path);
    match crate::terminal_handlers::execute_terminal_in_dir(&path, command_str, shell_str).await {
        Ok(res) => CommandResult {
            id: cmd.id.clone(),
            status: if res.exit_code == 0 {
                "completed".to_string()
            } else {
                "failed".to_string()
            },
            result: serde_json::to_string(&res).ok(),
        },
        Err(e) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(e.to_string()),
        },
    }
}
