#[derive(Debug, Deserialize)]
pub struct RegisterProjectRequest {
    pub path: String,
    pub display_name: Option<String>,
}

pub async fn register_project(
    State(state): State<AppState>,
    Json(request): Json<RegisterProjectRequest>,
) -> Result<Json<RepoSummary>, ApiFailure> {
    let mut selected = PathBuf::from(request.path.trim());
    if selected.is_file()
        && let Some(parent) = selected.parent() {
            selected = parent.to_path_buf();
        }
    if !selected.is_absolute() || !selected.is_dir() {
        return Err(failure(
            StatusCode::BAD_REQUEST,
            "Choose an existing project folder or file.",
        ));
    }
    let output = state
        .git_executor
        .create_command(&selected)
        .args(["rev-parse", "--show-toplevel"])
        .output()
        .await
        .map_err(|e| failure(StatusCode::BAD_REQUEST, e.to_string()))?;
    let stderr = String::from_utf8_lossy(&output.stderr);
    let stdout = String::from_utf8_lossy(&output.stdout);
    tracing::info!(
        "register_project rev-parse for {:?}: success={}, status={:?}, stdout={}, stderr={}",
        selected,
        output.status.success(),
        output.status,
        stdout.trim(),
        stderr.trim()
    );

    let path = if output.status.success() {
        PathBuf::from(stdout.trim())
    } else {
        let err_msg = if stderr.trim().is_empty() {
            "The selected folder is not a Git repository. Initialize Git first before adding it."
                .to_string()
        } else {
            format!("Not a Git repository: {}", stderr.trim())
        };
        return Err(failure(StatusCode::BAD_REQUEST, err_msg));
    };
    let canonical_path = if let Ok(canon) = path.canonicalize() {
        let s = canon.to_string_lossy();
        s.strip_prefix(r"\\?\").unwrap_or(&s).to_string()
    } else {
        path.to_string_lossy().to_string()
    };
    let repos = state
        .storage
        .list_repositories()
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    let existing = repos
        .into_iter()
        .find(|repo| repo.canonical_path.eq_ignore_ascii_case(&canonical_path));
    let repo_id = existing
        .as_ref()
        .map(|repo| repo.repo_id.clone())
        .unwrap_or_else(|| RepoId::new(format!("repo_{}", uuid::Uuid::new_v4().simple())));
    let display_name = request
        .display_name
        .filter(|name| !name.trim().is_empty())
        .unwrap_or_else(|| {
            std::path::Path::new(&canonical_path)
                .file_name()
                .map(|name| name.to_string_lossy().into_owned())
                .unwrap_or_else(|| canonical_path.clone())
        });
    state
        .storage
        .register_repo(
            &repo_id,
            &canonical_path,
            display_name.trim(),
            TrustProfile::Trusted,
        )
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    *state.project_cache.lock().await = None;
    let repo = state
        .storage
        .get_repository(&repo_id)
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or_else(|| failure(StatusCode::INTERNAL_SERVER_ERROR, "Project was not saved."))?;
    let (summary, snapshot_opt) = summarize_repository(&state, repo).await;
    if let Some(snapshot) = snapshot_opt {
        let mut ws_cache = state.workspace_cache.lock().await;
        ws_cache.insert(repo_id.clone(), (Instant::now(), snapshot.clone()));
        let _ = state.event_tx.send(ServerEvent::WorkspaceUpdated {
            repo_id: repo_id.clone(),
            snapshot,
        });
    }
    let _ = state
        .event_tx
        .send(ServerEvent::ProjectUpdated(summary.clone()));
    Ok(Json(summary))
}
