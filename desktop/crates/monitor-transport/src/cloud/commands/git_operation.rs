async fn execute_git_operation_command(state: &AppState, cmd: &PendingCommand) -> CommandResult {
    let git_allowed = state
        .storage
        .get_setting("allow_git_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);

    if !git_allowed {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Git operations disabled by desktop security policy".to_string()),
        };
    }

    let repo_id_str = cmd
        .payload
        .get("repo_id")
        .and_then(serde_json::Value::as_str);
    let Some(repo_id_str) = repo_id_str else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Missing 'repo_id' in payload".to_string()),
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

    let op_val = cmd
        .payload
        .get("operation")
        .cloned()
        .unwrap_or(serde_json::Value::Null);
    let Ok(op_req) = serde_json::from_value::<monitor_core::GitOperationRequest>(op_val) else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Invalid git operation request payload".to_string()),
        };
    };

    let root = std::path::PathBuf::from(repo.canonical_path);
    let res = state
        .repo_coordinator
        .execute_operation(&repo_id, &root, op_req)
        .await;

    match res {
        Ok(out) => {
            *state.project_cache.lock().await = None;
            CommandResult {
                id: cmd.id.clone(),
                status: "completed".to_string(),
                result: Some(out.user_message),
            }
        }
        Err(e) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(e.to_string()),
        },
    }
}
