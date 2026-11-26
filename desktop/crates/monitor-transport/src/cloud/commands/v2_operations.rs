fn v2_json_result<T: Serialize, E: std::fmt::Debug>(
    result: Result<axum::Json<T>, E>,
) -> Result<serde_json::Value, String> {
    result
        .map(|axum::Json(value)| serde_json::to_value(value).unwrap_or(serde_json::Value::Null))
        .map_err(|error| format!("{error:?}"))
}

fn v2_value<T: Serialize>(value: T) -> Result<serde_json::Value, String> {
    serde_json::to_value(value).map_err(|error| error.to_string())
}

async fn execute_v2_command(state: &AppState, cmd: &PendingCommand) -> CommandResult {
    use axum::extract::{Path, Query, State};
    use monitor_core::{
        CreateCheckpointRequest, TerminalCommandRequest, TerminalStopRequest,
    };
    use serde_json::{Value, json};

    let payload = &cmd.payload;
    let repo_id = || payload.get("repo_id").and_then(Value::as_str).unwrap_or_default();
    let result: Result<Value, String> = match cmd.command_type.as_str() {
        "telemetry.poll" => v2_value(state.telemetry.sample_system_snapshot()),
        "projects.list" => crate::local_handlers::list_project_summaries(state)
            .await
            .map_err(|error| format!("{error:?}"))
            .and_then(v2_value),
        "permissions.get" => crate::local_handlers::get_permissions_handler(State(state.clone()))
            .await
            .map(|axum::Json(value)| value)
            .map_err(|error| format!("{error:?}")),
        "process.kill" => {
            let pending = PendingCommand {
                id: cmd.id.clone(),
                command_type: "kill_process".to_string(),
                payload: payload.clone(),
            };
            let result = execute_kill_process_command(state, &pending).await;
            if result.status == "completed" {
                Ok(json!(true))
            } else {
                Err(result.result.unwrap_or_else(|| "Process termination failed".to_string()))
            }
        }
        "git.workspace" => v2_json_result(
            crate::git_handlers::get_workspace(State(state.clone()), Path(repo_id().to_string()))
                .await,
        ),
        "git.diff" => v2_json_result(
            crate::git_handlers::get_file_diff(
                State(state.clone()),
                Path(repo_id().to_string()),
                Query(crate::git_handlers::DiffQueryParams {
                    file: payload.get("file").and_then(Value::as_str).unwrap_or_default().to_string(),
                    staged: payload.get("staged").and_then(Value::as_bool),
                    commit: payload.get("commit").and_then(Value::as_str).map(str::to_string),
                }),
            )
            .await,
        ),
        "git.commit_files" => v2_json_result(
            crate::git_handlers::get_commit_files(
                State(state.clone()),
                Path((
                    repo_id().to_string(),
                    payload.get("commit_oid").and_then(Value::as_str).unwrap_or_default().to_string(),
                )),
            )
            .await,
        ),
        "git.branches" => v2_json_result(
            crate::git_handlers::get_branches(State(state.clone()), Path(repo_id().to_string()))
                .await,
        ),
        "git.graph" => v2_json_result(
            crate::git_handlers::get_graph(
                State(state.clone()),
                Path(repo_id().to_string()),
                Query(crate::git_handlers::GraphQueryParams {
                    skip: payload.get("skip").and_then(Value::as_u64).map(|v| v as usize),
                    limit: payload.get("limit").and_then(Value::as_u64).map(|v| v as usize),
                    branch: payload.get("branch").and_then(Value::as_str).map(String::from),
                }),
            )
            .await,
        ),
        "git.pull_requests" => v2_json_result(
            crate::git_handlers::get_pull_requests(State(state.clone()), Path(repo_id().to_string()))
                .await,
        ),
        "git.identity.get" => v2_json_result(
            crate::git_handlers::get_git_identity(
                State(state.clone()),
                axum::Extension(crate::state::AuthenticatedDevice {
                    device_id: monitor_core::DeviceId::new("cloud_operator"),
                    role: monitor_core::DeviceRole::Operator,
                }),
            )
            .await,
        ),
        "git.identity.set" => v2_json_result(
            crate::git_handlers::set_git_identity(
                State(state.clone()),
                axum::Extension(crate::state::AuthenticatedDevice {
                    device_id: monitor_core::DeviceId::new("cloud_operator"),
                    role: monitor_core::DeviceRole::Operator,
                }),
                axum::Json(crate::git_handlers::SetGitIdentityRequest {
                    name: payload.get("name").and_then(Value::as_str).unwrap_or_default().to_string(),
                    email: payload.get("email").and_then(Value::as_str).unwrap_or_default().to_string(),
                }),
            )
            .await,
        ),
        "git.stage" | "git.unstage" | "git.discard_unstaged" | "git.commit" | "git.fetch"
        | "git.pull" | "git.push" | "git.create_branch" | "git.switch_branch"
        | "git.delete_branch" | "git.merge_branch" | "git.merge_all_branches" => {
            execute_git_operation_v2(state, cmd).await
        }
        "merge_pr" | "git.merge_pr" => execute_merge_pr_v2(state, cmd).await,
        "watchdog.alerts" => v2_json_result(
            crate::watchdog_handlers::list_alerts_handler(
                State(state.clone()),
                Query(crate::watchdog_handlers::ListAlertsQuery {
                    limit: payload.get("limit").and_then(Value::as_u64).map(|v| v as u32),
                    active_only: payload.get("active_only").and_then(Value::as_bool),
                }),
            )
            .await,
        ),
        "watchdog.ack" => {
            let alert_id = payload.get("alert_id").and_then(Value::as_str).unwrap_or_default();
            crate::watchdog_handlers::ack_alert_impl(state, alert_id)
                .await
                .map(|_| json!(true))
                .map_err(|status| format!("Alert acknowledgement failed: {status}"))
        }
        "watchdog.snooze" => {
            let alert_id = payload.get("alert_id").and_then(Value::as_str).unwrap_or_default();
            let duration_s = payload.get("duration_s").and_then(Value::as_u64).unwrap_or(3600) as u32;
            crate::watchdog_handlers::snooze_alert_impl(state, alert_id, duration_s)
                .await
                .map(|_| json!(true))
                .map_err(|status| format!("Alert snooze failed: {status}"))
        }
        "watchdog.sessions" => v2_json_result(
            crate::watchdog_handlers::list_agent_sessions_handler(State(state.clone())).await,
        ),
        "watchdog.kill" => {
            let delete_allowed = state
                .storage
                .get_setting("allow_delete_operations")
                .unwrap_or(None)
                .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
                .unwrap_or(false);
            if !delete_allowed {
                Err("Destructive delete/kill operations disabled by desktop security policy".to_string())
            } else {
                let session_id = payload.get("session_id").and_then(Value::as_str).unwrap_or_default();
                crate::watchdog_handlers::execute_surgical_kill_session(state, session_id)
                    // FEAT-05: absence of an explicit success flag is NOT success.
                    .map(|value| json!(value.get("success").and_then(Value::as_bool).unwrap_or(false)))
                    .map_err(|error| error.to_string())
            }
        }
        "watchdog.checkpoints" => v2_json_result(
            crate::watchdog_handlers::list_checkpoints_handler(
                State(state.clone()),
                Query(crate::watchdog_handlers::ListCheckpointsQuery {
                    repo_path: payload.get("repo_path").and_then(Value::as_str).unwrap_or_default().to_string(),
                    limit: payload.get("limit").and_then(Value::as_u64).map(|v| v as u32),
                }),
            )
            .await,
        ),
        "watchdog.checkpoint_create" => v2_json_result(
            crate::watchdog_handlers::create_checkpoint_handler(
                State(state.clone()),
                // The v2 tier gate in host_control.rs has already authorized the
                // peer as an operator; the handler still enforces the local
                // fail-closed checkpoint permission.
                axum::Extension(crate::state::AuthenticatedDevice {
                    device_id: monitor_core::DeviceId::new("cloud_operator"),
                    role: monitor_core::DeviceRole::Operator,
                }),
                axum::Json(CreateCheckpointRequest {
                    repo_path: payload.get("repo_path").and_then(Value::as_str).unwrap_or_default().to_string(),
                    description: payload.get("description").and_then(Value::as_str).map(str::to_string),
                }),
            )
            .await,
        ),
        "watchdog.checkpoint_rollback" => v2_json_result(
            crate::watchdog_handlers::rollback_checkpoint_handler(
                State(state.clone()),
                axum::Extension(crate::state::AuthenticatedDevice {
                    device_id: monitor_core::DeviceId::new("cloud_operator"),
                    role: monitor_core::DeviceRole::Operator,
                }),
                Path(payload.get("checkpoint_id").and_then(Value::as_str).unwrap_or_default().to_string()),
            )
            .await,
        ),
        "terminal.start" => v2_json_result(
            crate::terminal_handlers::run_project_terminal_handler(
                State(state.clone()),
                axum::Extension(crate::state::AuthenticatedDevice {
                    device_id: monitor_core::DeviceId::new("cloud_operator"),
                    role: monitor_core::DeviceRole::Operator,
                }),
                Path(repo_id().to_string()),
                axum::Json(TerminalCommandRequest {
                    command: payload.get("command").and_then(Value::as_str).unwrap_or_default().to_string(),
                    shell: payload.get("shell").and_then(Value::as_str).map(str::to_string),
                    session_id: payload.get("session_id").and_then(Value::as_str).map(str::to_string),
                }),
            )
            .await,
        ),
        "terminal.stop" => v2_json_result(
            crate::terminal_handlers::stop_project_terminal_handler(
                State(state.clone()),
                axum::Extension(crate::state::AuthenticatedDevice {
                    device_id: monitor_core::DeviceId::new("cloud_operator"),
                    role: monitor_core::DeviceRole::Operator,
                }),
                Path(repo_id().to_string()),
                axum::Json(TerminalStopRequest {
                    session_id: payload.get("session_id").and_then(Value::as_str).map(str::to_string),
                }),
            )
            .await,
        ),
        "terminal.setting.get" => v2_json_result(
            crate::terminal_handlers::get_terminal_setting_handler(State(state.clone())).await,
        ),
        "terminal.setting.set" => Err(
            "Remote modification of terminal execution policy is forbidden. Terminal execution can only be enabled locally on the desktop host.".to_string(),
        ),
        other => Err(format!("Unsupported v2 operation '{other}'")),
    };

    match result {
        Ok(value) => CommandResult {
            id: cmd.id.clone(),
            status: "completed".to_string(),
            result: Some(value.to_string()),
        },
        Err(error) => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(error),
        },
    }
}

async fn execute_git_operation_v2(
    state: &AppState,
    cmd: &PendingCommand,
) -> Result<serde_json::Value, String> {
    let allowed = state
        .storage
        .get_setting("allow_git_operations")
        .unwrap_or(None)
        .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !allowed {
        return Err("Operation disabled by desktop security policy".to_string());
    }

    let repo_id_str = cmd
        .payload
        .get("repo_id")
        .and_then(serde_json::Value::as_str)
        .ok_or_else(|| "Missing 'repo_id' in payload".to_string())?;
    let request_value = cmd
        .payload
        .get("operation")
        .cloned()
        .ok_or_else(|| "Missing GitOperationRequest payload".to_string())?;
    let request: monitor_core::GitOperationRequest = serde_json::from_value(request_value)
        .map_err(|error| format!("Invalid GitOperationRequest: {error}"))?;
    let repo_id = monitor_core::RepoId::new(repo_id_str);
    let repo = state
        .storage
        .get_repository(&repo_id)
        .map_err(|error| error.to_string())?
        .ok_or_else(|| format!("Repository '{repo_id_str}' not found"))?;
    let result = state
        .repo_coordinator
        .execute_operation(&repo_id, std::path::Path::new(&repo.canonical_path), request)
        .await
        .map_err(|error| error.to_string())?;
    *state.project_cache.lock().await = None;
    serde_json::to_value(result).map_err(|error| error.to_string())
}

async fn execute_merge_pr_v2(
    state: &AppState,
    cmd: &PendingCommand,
) -> Result<serde_json::Value, String> {
    let allowed = state
        .storage
        .get_setting("allow_git_operations")
        .unwrap_or(None)
        .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !allowed {
        return Err("Operation disabled by desktop security policy".to_string());
    }
    let repo_id_str = cmd
        .payload
        .get("repo_id")
        .and_then(serde_json::Value::as_str)
        .ok_or_else(|| "Missing 'repo_id' in payload".to_string())?;
    let pr_number = cmd
        .payload
        .get("pr_number")
        .and_then(serde_json::Value::as_u64)
        .ok_or_else(|| "Missing 'pr_number' in payload".to_string())?;
    let method = cmd
        .payload
        .get("method")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("merge")
        .to_ascii_lowercase();
    if !matches!(method.as_str(), "merge" | "squash" | "rebase") {
        return Err("Merge method must be merge, squash, or rebase".to_string());
    }
    let repo_id = monitor_core::RepoId::new(repo_id_str);
    let repo = state
        .storage
        .get_repository(&repo_id)
        .map_err(|error| error.to_string())?
        .ok_or_else(|| format!("Repository '{repo_id_str}' not found"))?;
    let root = std::path::PathBuf::from(repo.canonical_path);
    let message = tokio::task::spawn_blocking(move || {
        monitor_git::merge_pull_request(&root, pr_number, Some(&method))
    })
    .await
    .map_err(|error| error.to_string())?
    .map_err(|error| error.to_string())?;
    *state.project_cache.lock().await = None;
    serde_json::to_value(monitor_core::MergePrResponse {
        success: true,
        message,
    })
    .map_err(|error| error.to_string())
}
