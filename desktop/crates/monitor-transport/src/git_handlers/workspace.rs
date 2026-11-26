#[derive(Debug, Deserialize)]
pub struct DiffQueryParams {
    pub file: String,
    pub staged: Option<bool>,
    pub commit: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct GraphQueryParams {
    pub skip: Option<usize>,
    pub limit: Option<usize>,
    pub branch: Option<String>,
}

/// Helper to resolve repository path from storage or return 404.
fn resolve_repo(
    state: &AppState,
    repo_id_str: &str,
) -> Result<(RepoId, PathBuf), (StatusCode, Json<ApiErrorResponse>)> {
    let repo_id = RepoId::new(repo_id_str);
    let repo = state.storage.get_repository(&repo_id).map_err(|e| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(ApiErrorResponse::new(
                ErrorCode::InternalError,
                format!("Database error: {e}"),
                true,
            )),
        )
    })?;

    let Some(repo) = repo else {
        return Err((
            StatusCode::NOT_FOUND,
            Json(ApiErrorResponse::new(
                ErrorCode::RepoNotFound,
                format!("Repository '{repo_id_str}' not found"),
                false,
            )),
        ));
    };

    Ok((repo_id, PathBuf::from(repo.canonical_path)))
}

/// `GET /api/v1/projects/{repo_id}/workspace`
pub async fn get_workspace(
    State(state): State<AppState>,
    AxumPath(repo_id_str): AxumPath<String>,
) -> Result<Json<GitWorkspaceSnapshot>, (StatusCode, Json<ApiErrorResponse>)> {
    let (repo_id, repo_path) = resolve_repo(&state, &repo_id_str)?;

    // Fast-path: Check hot in-memory workspace cache (< 0.1ms RAM response)
    {
        let cache = state.workspace_cache.lock().await;
        if let Some((at, snap)) = cache.get(&repo_id)
            && at.elapsed() < std::time::Duration::from_secs(10) {
                return Ok(Json(snap.clone()));
            }
    }

    let lock = state
        .repo_coordinator
        .get_or_create_repo_lock(&repo_id, Revision(1))
        .await;
    let guard = lock.lock().await;
    let snapshot =
        monitor_git::query_git_status(&state.git_executor, &repo_path, &repo_id, guard.revision)
            .await
            .map_err(|e| {
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(ApiErrorResponse::new(
                        ErrorCode::InternalError,
                        format!("Failed to query workspace: {e}"),
                        true,
                    )),
                )
            })?;

    // Update warm workspace cache
    {
        let mut cache = state.workspace_cache.lock().await;
        cache.insert(repo_id.clone(), (std::time::Instant::now(), snapshot.clone()));
    }

    Ok(Json(snapshot))
}

/// `GET /api/v1/projects/{repo_id}/diff?file=...&staged=...`
pub async fn get_file_diff(
    State(state): State<AppState>,
    AxumPath(repo_id_str): AxumPath<String>,
    Query(query): Query<DiffQueryParams>,
) -> Result<Json<FileDiffResponse>, (StatusCode, Json<ApiErrorResponse>)> {
    let (_, repo_path) = resolve_repo(&state, &repo_id_str)?;

    let is_staged = query.staged.unwrap_or(false);
    let commit_ref = query.commit.as_deref().filter(|s| !s.trim().is_empty());
    let diff = monitor_git::query_file_diff(
        &state.git_executor,
        &repo_path,
        &query.file,
        is_staged,
        commit_ref,
    )
    .await
    .map_err(|e| match e {
        monitor_core::CoreError::InvalidArgument(msg) => (
            StatusCode::BAD_REQUEST,
            Json(ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                msg,
                false,
            )),
        ),
        _ => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(ApiErrorResponse::new(
                ErrorCode::InternalError,
                format!("Failed to query diff: {e}"),
                false,
            )),
        ),
    })?;

    Ok(Json(diff))
}

/// `GET /api/v1/projects/{repo_id}/branches`
pub async fn get_branches(
    State(state): State<AppState>,
    AxumPath(repo_id_str): AxumPath<String>,
) -> Result<Json<Vec<BranchInfo>>, (StatusCode, Json<ApiErrorResponse>)> {
    let (_, repo_path) = resolve_repo(&state, &repo_id_str)?;

    let branches = monitor_git::list_branches(&state.git_executor, &repo_path)
        .await
        .map_err(|e| {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(ApiErrorResponse::new(
                    ErrorCode::InternalError,
                    format!("Failed to list branches: {e}"),
                    true,
                )),
            )
        })?;

    Ok(Json(branches))
}

/// `GET /api/v1/projects/{repo_id}/graph?skip=...&limit=...`
pub async fn get_graph(
    State(state): State<AppState>,
    AxumPath(repo_id_str): AxumPath<String>,
    Query(query): Query<GraphQueryParams>,
) -> Result<Json<GitGraphPage>, (StatusCode, Json<ApiErrorResponse>)> {
    let (repo_id, repo_path) = resolve_repo(&state, &repo_id_str)?;

    let skip = query.skip.unwrap_or(0);
    let limit = query.limit.unwrap_or(150).clamp(1, 500);

    let graph_page = monitor_git::query_git_graph(
        &state.git_executor,
        &repo_path,
        &repo_id,
        skip,
        limit,
        query.branch.as_deref(),
    )
    .await
    .map_err(|e| {
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(ApiErrorResponse::new(
                        ErrorCode::InternalError,
                        format!("Failed to query git graph: {e}"),
                        false,
                    )),
                )
            })?;

    Ok(Json(graph_page))
}

/// `GET /api/v1/projects/{repo_id}/commits/{commit_oid}/files`
pub async fn get_commit_files(
    State(state): State<AppState>,
    AxumPath((repo_id_str, commit_oid)): AxumPath<(String, String)>,
) -> Result<Json<Vec<GitFileChange>>, (StatusCode, Json<ApiErrorResponse>)> {
    let (_repo_id, repo_path) = resolve_repo(&state, &repo_id_str)?;

    let files = monitor_git::query_commit_files(
        &state.git_executor,
        &repo_path,
        &commit_oid,
    )
    .await
    .map_err(|e| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(ApiErrorResponse::new(
                ErrorCode::InternalError,
                format!("Failed to query commit files: {e}"),
                false,
            )),
        )
    })?;

    Ok(Json(files))
}
