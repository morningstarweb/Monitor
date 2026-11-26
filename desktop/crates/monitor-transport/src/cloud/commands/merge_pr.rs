async fn execute_merge_pr_command(state: &AppState, cmd: &PendingCommand) -> CommandResult {
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
            result: Some("Operation disabled by desktop security policy".to_string()),
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
            result: Some("Destructive delete/merge operations disabled by desktop security policy".to_string()),
        };
    }

    let repo_id_str = cmd
        .payload
        .get("repo_id")
        .and_then(serde_json::Value::as_str);
    let pr_number = cmd
        .payload
        .get("pr_number")
        .and_then(serde_json::Value::as_u64);
    let method = cmd
        .payload
        .get("method")
        .and_then(serde_json::Value::as_str)
        .map(ToString::to_string);

    let Some(repo_id_str) = repo_id_str else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Missing 'repo_id' in payload".to_string()),
        };
    };
    let Some(pr_number) = pr_number else {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Missing 'pr_number' in payload".to_string()),
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

    let lock = state
        .repo_coordinator
        .get_or_create_repo_lock(&repo_id, Revision(1))
        .await;
    let _guard = lock.lock().await;

    let root = std::path::PathBuf::from(repo.canonical_path);
    let res = tokio::task::spawn_blocking(move || {
        monitor_git::merge_pull_request(&root, pr_number, method.as_deref())
    })
    .await;

    match res {
        Ok(Ok(msg)) => {
            *state.project_cache.lock().await = None;
            CommandResult {
                id: cmd.id.clone(),
                status: "completed".to_string(),
                result: Some(msg),
            }
        }
        Ok(Err(e)) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(e.to_string()),
        },
        Err(join_err) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(format!("Task execution panicked: {join_err}")),
        },
    }
}
