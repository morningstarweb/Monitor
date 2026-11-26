/// `POST /api/v1/projects/{repo_id}/operations`
pub async fn execute_operation(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(repo_id_str): AxumPath<String>,
    Json(payload): Json<GitOperationRequest>,
) -> Result<Json<GitOperationResult>, (StatusCode, Json<ApiErrorResponse>)> {
    if device.role == DeviceRole::Viewer {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Insufficient permissions: Viewer role cannot execute mutating operations",
                false,
            )),
        ));
    }

    // Check if Git operations are enabled from desktop tray
    let git_allowed = state
        .storage
        .get_setting("allow_git_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);

    if !git_allowed {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Git branch & commit operations are blocked by desktop tray policy. Please enable Git Branch Access in the Windows system tray.",
                false,
            )),
        ));
    }

    // Check if delete operations are allowed for destructive commands
    let is_delete_operation = matches!(
        payload.operation,
        monitor_core::GitOperation::DeleteBranch { .. }
            | monitor_core::GitOperation::DiscardFile { .. }
    );

    if is_delete_operation {
        let delete_allowed = state
            .storage
            .get_setting("allow_delete_operations")
            .unwrap_or(None)
            .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
            .unwrap_or(false);

        if !delete_allowed {
            return Err((
                StatusCode::FORBIDDEN,
                Json(ApiErrorResponse::new(
                    ErrorCode::Unauthorized,
                    "Branch & file deletion is blocked by desktop tray policy. Please enable Delete Access in the Windows system tray.",
                    false,
                )),
            ));
        }
    }

    let (repo_id, repo_path) = resolve_repo(&state, &repo_id_str)?;

    let result = state
        .repo_coordinator
        .execute_operation(&repo_id, &repo_path, payload)
        .await
        .map_err(|e| {
            let (code, status) = match &e {
                monitor_core::CoreError::Conflict(msg)
                    if msg.contains("DIRTY_WORKTREE_CONFLICT") =>
                {
                    (ErrorCode::DirtyWorktreeConflict, StatusCode::CONFLICT)
                }
                monitor_core::CoreError::Conflict(msg) if msg.contains("REPO_LOCKED") => {
                    (ErrorCode::RepoLockedByExternalProcess, StatusCode::LOCKED)
                }
                monitor_core::CoreError::Conflict(msg)
                    if msg.contains("NEEDS_FORCE_CONFIRMATION") =>
                {
                    (ErrorCode::NeedsForceConfirmation, StatusCode::CONFLICT)
                }
                monitor_core::CoreError::InvalidArgument(_) => {
                    (ErrorCode::InvalidArgument, StatusCode::BAD_REQUEST)
                }
                monitor_core::CoreError::Conflict(_) => {
                    (ErrorCode::ConflictUnresolved, StatusCode::CONFLICT)
                }
                _ => (ErrorCode::InternalError, StatusCode::INTERNAL_SERVER_ERROR),
            };
            (
                status,
                Json(ApiErrorResponse::new(code, e.to_string(), false)),
            )
        })?;

    *state.project_cache.lock().await = None;

    if result.status == monitor_core::OperationStatus::Succeeded {
        let executor = state.git_executor.clone();
        let event_tx = state.event_tx.clone();
        let r_id = repo_id.clone();
        let r_root = repo_path.clone();
        let new_rev = result.new_repo_revision;
        tokio::spawn(async move {
            if let Ok(snapshot) =
                monitor_git::query_git_status(&executor, &r_root, &r_id, new_rev).await
            {
                let _ = event_tx.send(ServerEvent::WorkspaceUpdated {
                    repo_id: r_id,
                    snapshot,
                });
            }
        });
    }

    Ok(Json(result))
}
