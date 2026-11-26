#[derive(Debug, Deserialize)]
pub struct ListCheckpointsQuery {
    pub repo_path: String,
    pub limit: Option<u32>,
}

/// NEW-01 / AUD-04: centralized, fail-closed authorization for checkpoint
/// mutations. Must be called by every entry point (HTTP v1 and cloud/LAN v2)
/// before any repository mutation occurs. Viewer devices are always denied,
/// and a missing or disabled `allow_checkpoint_rollback` setting denies too.
fn authorize_checkpoint_mutation(
    state: &AppState,
    device: &AuthenticatedDevice,
    action: &str,
    target: &str,
) -> Result<(), StatusCode> {
    if device.role == DeviceRole::Viewer {
        let _ = state.storage.insert_audit_entry(
            device.device_id.as_str(),
            action,
            target,
            "denied_viewer_role",
        );
        return Err(StatusCode::FORBIDDEN);
    }

    let allowed = state
        .storage
        .get_setting("allow_checkpoint_rollback")
        .unwrap_or(None)
        .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !allowed {
        let _ = state.storage.insert_audit_entry(
            device.device_id.as_str(),
            action,
            target,
            "denied_permission",
        );
        return Err(StatusCode::FORBIDDEN);
    }

    Ok(())
}

fn canonical_or_lower(path: &Path) -> String {
    let resolved = std::fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf());
    resolved
        .to_string_lossy()
        .strip_prefix(r"\\?\")
        .unwrap_or(&resolved.to_string_lossy())
        .to_lowercase()
}

fn is_registered_repo(state: &AppState, path: &Path) -> bool {
    let Ok(repos) = state.storage.list_repositories() else {
        return false;
    };
    let target = canonical_or_lower(path);
    repos
        .iter()
        .any(|repo| canonical_or_lower(Path::new(&repo.canonical_path)) == target)
}

pub async fn list_checkpoints_handler(
    State(state): State<AppState>,
    Query(query): Query<ListCheckpointsQuery>,
) -> Result<Json<Vec<CheckpointSummary>>, StatusCode> {
    let limit = query.limit.unwrap_or(50).clamp(1, 200);
    let checkpoints = state
        .storage
        .list_checkpoints(&query.repo_path, limit)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let res = checkpoints
        .into_iter()
        .map(|c| CheckpointSummary {
            checkpoint_id: c.checkpoint_id,
            repo_path: c.repo_path,
            branch: c.branch,
            commit_sha: c.commit_sha,
            created_at_utc_ms: c.created_at_utc_ms,
            files_changed_count: c.files_changed_count,
            lines_added: c.lines_added,
            lines_deleted: c.lines_deleted,
            is_safety: c.is_safety,
            description: c.description,
        })
        .collect();

    Ok(Json(res))
}

pub async fn create_checkpoint_handler(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    Json(payload): Json<CreateCheckpointRequest>,
) -> Result<Json<CheckpointSummary>, StatusCode> {
    authorize_checkpoint_mutation(
        &state,
        &device,
        "create_checkpoint",
        &payload.repo_path,
    )?;

    let mut target_repo = payload.repo_path.trim().to_string();
    if target_repo.is_empty()
        && let Ok(repos) = state.storage.list_repositories()
        && let Some(first) = repos.first()
    {
        target_repo = first.canonical_path.clone();
    }

    let path = Path::new(&target_repo);
    if !path.join(".git").exists() {
        return Err(StatusCode::BAD_REQUEST);
    }
    if !is_registered_repo(&state, path) {
        let _ = state.storage.insert_audit_entry(
            device.device_id.as_str(),
            "create_checkpoint",
            &target_repo,
            "denied_unregistered_repo",
        );
        return Err(StatusCode::FORBIDDEN);
    }

    let git_exec = GitExecutor::new().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let desc: String = payload
        .description
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("Manual Checkpoint")
        .chars()
        .take(160)
        .collect();
    let chk = CheckpointEngine::create_checkpoint(&git_exec, path, &desc, false)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let stored = StoredCheckpoint {
        checkpoint_id: chk.checkpoint_id.clone(),
        repo_path: chk.repo_path.clone(),
        branch: chk.branch.clone(),
        commit_sha: chk.commit_sha.clone(),
        created_at_utc_ms: chk.created_at_utc_ms,
        files_changed_count: chk.files_changed_count,
        lines_added: chk.lines_added,
        lines_deleted: chk.lines_deleted,
        is_safety: chk.is_safety,
        description: chk.description.clone(),
    };

    state
        .storage
        .insert_checkpoint(&stored)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let _ = state.storage.insert_audit_entry(
        device.device_id.as_str(),
        "create_checkpoint",
        &chk.checkpoint_id,
        "success",
    );

    Ok(Json(CheckpointSummary {
        checkpoint_id: chk.checkpoint_id,
        repo_path: chk.repo_path,
        branch: chk.branch,
        commit_sha: chk.commit_sha,
        created_at_utc_ms: chk.created_at_utc_ms,
        files_changed_count: chk.files_changed_count,
        lines_added: chk.lines_added,
        lines_deleted: chk.lines_deleted,
        is_safety: chk.is_safety,
        description: chk.description,
    }))
}

pub async fn rollback_checkpoint_handler(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(checkpoint_id): AxumPath<String>,
) -> Result<Json<RollbackResponse>, StatusCode> {
    authorize_checkpoint_mutation(
        &state,
        &device,
        "rollback_checkpoint",
        &checkpoint_id,
    )?;

    let chk = state
        .storage
        .get_checkpoint(&checkpoint_id)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?
        .ok_or(StatusCode::NOT_FOUND)?;

    let path = Path::new(&chk.repo_path);
    if !path.join(".git").exists() {
        return Err(StatusCode::BAD_REQUEST);
    }
    if let Ok(repos) = state.storage.list_repositories()
        && !repos.is_empty()
        && !is_registered_repo(&state, path)
    {
        let _ = state.storage.insert_audit_entry(
            device.device_id.as_str(),
            "rollback_checkpoint",
            &chk.checkpoint_id,
            "denied_unregistered_repo",
        );
        return Err(StatusCode::FORBIDDEN);
    }
    let git_exec = GitExecutor::new().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let safety = CheckpointEngine::rollback_to_checkpoint(&git_exec, path, &chk.commit_sha)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let safety_stored = StoredCheckpoint {
        checkpoint_id: safety.checkpoint_id.clone(),
        repo_path: safety.repo_path.clone(),
        branch: safety.branch.clone(),
        commit_sha: safety.commit_sha.clone(),
        created_at_utc_ms: safety.created_at_utc_ms,
        files_changed_count: safety.files_changed_count,
        lines_added: safety.lines_added,
        lines_deleted: safety.lines_deleted,
        is_safety: safety.is_safety,
        description: safety.description.clone(),
    };
    let _ = state.storage.insert_checkpoint(&safety_stored);

    let _ = state.storage.insert_audit_entry(
        device.device_id.as_str(),
        "rollback_checkpoint",
        &chk.checkpoint_id,
        "success",
    );

    Ok(Json(RollbackResponse {
        success: true,
        checkpoint_id: chk.checkpoint_id,
        safety_checkpoint_id: safety.checkpoint_id,
        message: format!(
            "Successfully restored working tree to checkpoint from {}",
            chk.branch
        ),
    }))
}
