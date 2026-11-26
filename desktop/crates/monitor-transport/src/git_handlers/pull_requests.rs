/// `GET /api/v1/projects/{repo_id}/pulls`
pub async fn get_pull_requests(
    State(state): State<AppState>,
    AxumPath(repo_id_str): AxumPath<String>,
) -> Result<Json<Vec<PullRequestSummary>>, (StatusCode, Json<ApiErrorResponse>)> {
    let (_, repo_path) = resolve_repo(&state, &repo_id_str)?;

    let path_clone = repo_path.clone();
    let prs = tokio::task::spawn_blocking(move || monitor_git::list_pull_requests(&path_clone))
        .await
        .map_err(|e| {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(ApiErrorResponse::new(
                    ErrorCode::InternalError,
                    format!("Task execution failed: {e}"),
                    true,
                )),
            )
        })?
        .map_err(|e| {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(ApiErrorResponse::new(
                    ErrorCode::InternalError,
                    format!("Failed to list pull requests: {e}"),
                    false,
                )),
            )
        })?;

    Ok(Json(prs))
}

/// `POST /api/v1/projects/{repo_id}/pulls/{pr_number}/merge`
pub async fn merge_pull_request_handler(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath((repo_id_str, pr_number)): AxumPath<(String, u64)>,
    payload: Option<Json<MergePrRequest>>,
) -> Result<Json<MergePrResponse>, (StatusCode, Json<ApiErrorResponse>)> {
    if device.role == DeviceRole::Viewer {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Insufficient permissions: Viewer role cannot merge pull requests",
                false,
            )),
        ));
    }

    if pr_number == 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                "PR number must be greater than 0",
                false,
            )),
        ));
    }

    let req_payload = payload.map(|p| p.0).unwrap_or_default();
    let method_str = match &req_payload.method {
        Some(m) => match m.to_ascii_lowercase().as_str() {
            "merge" | "squash" | "rebase" => m.to_ascii_lowercase(),
            other => {
                return Err((
                    StatusCode::BAD_REQUEST,
                    Json(ApiErrorResponse::new(
                        ErrorCode::InvalidArgument,
                        format!(
                            "Invalid merge method '{other}'. Allowed methods: 'merge', 'squash', 'rebase'"
                        ),
                        false,
                    )),
                ));
            }
        },
        None => "merge".to_string(),
    };

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
                "Git operations are blocked by desktop tray policy. Please enable Git Branch Access in the Windows system tray.",
                false,
            )),
        ));
    }

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
                "PR merge blocked: Destructive Git/Delete Operations are disabled by desktop tray policy.",
                false,
            )),
        ));
    }

    let (repo_id, repo_path) = resolve_repo(&state, &repo_id_str)?;

    let lock = state
        .repo_coordinator
        .get_or_create_repo_lock(&repo_id, Revision(1))
        .await;
    let mut guard = lock.lock().await;

    let path_clone = repo_path.clone();
    let method_opt = Some(method_str);
    let message = tokio::task::spawn_blocking(move || {
        monitor_git::merge_pull_request(&path_clone, pr_number, method_opt.as_deref())
    })
    .await
    .map_err(|e| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(ApiErrorResponse::new(
                ErrorCode::InternalError,
                format!("Task execution failed: {e}"),
                true,
            )),
        )
    })?
    .map_err(|e| {
        let (status, code) = match &e {
            monitor_core::CoreError::Conflict(_) => {
                (StatusCode::CONFLICT, ErrorCode::ConflictUnresolved)
            }
            monitor_core::CoreError::InvalidArgument(_) => {
                (StatusCode::BAD_REQUEST, ErrorCode::InvalidArgument)
            }
            _ => (StatusCode::INTERNAL_SERVER_ERROR, ErrorCode::InternalError),
        };
        (
            status,
            Json(ApiErrorResponse::new(code, e.to_string(), false)),
        )
    })?;

    guard.revision = guard.revision.next();
    let new_rev = guard.revision;
    drop(guard);

    *state.project_cache.lock().await = None;

    let executor = state.git_executor.clone();
    let event_tx = state.event_tx.clone();
    let r_id = repo_id.clone();
    let r_root = repo_path.clone();
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

    Ok(Json(MergePrResponse {
        success: true,
        message,
    }))
}
