async fn summarize_repository(
    state: &AppState,
    repo: StoredRepository,
) -> (RepoSummary, Option<GitWorkspaceSnapshot>) {
    let root = PathBuf::from(&repo.canonical_path);
    let mut summary = RepoSummary {
        repo_id: repo.repo_id.clone(),
        display_name: repo.display_name,
        path: repo.canonical_path,
        current_branch: String::new(),
        is_clean: false,
        modified_count: 0,
        staged_count: 0,
        untracked_count: 0,
        ahead_count: 0,
        behind_count: 0,
        last_activity_utc_ms: repo.registered_at_utc_ms,
        watch_enabled: repo.watch_enabled,
        trust_profile: repo.trust_profile,
        status_error: None,
    };

    if !root.exists() {
        summary.status_error = Some("Project folder not found on disk".to_string());
        return (summary, None);
    }

    let lock = state
        .repo_coordinator
        .get_or_create_repo_lock(&repo.repo_id, Revision(1))
        .await;
    let guard = lock.lock().await;
    let git_status_fut =
        monitor_git::query_git_status(&state.git_executor, &root, &repo.repo_id, guard.revision);
    let result = tokio::time::timeout(Duration::from_secs(3), git_status_fut).await;
    drop(guard);

    let mut snapshot_opt = None;
    match result {
        Ok(Ok(snapshot)) => {
            summary.current_branch = snapshot.current_branch.clone();
            summary.is_clean = snapshot.files.is_empty();
            summary.staged_count = snapshot.files.iter().filter(|file| file.staged).count();
            summary.untracked_count = snapshot
                .files
                .iter()
                .filter(|file| file.category == FileChangeCategory::Untracked)
                .count();
            summary.modified_count = snapshot
                .files
                .iter()
                .filter(|file| !file.staged && file.category != FileChangeCategory::Untracked)
                .count();
            summary.ahead_count = snapshot.ahead_count;
            summary.behind_count = snapshot.behind_count;
            // Ultra-fast real change time from git index & HEAD logs (< 0.1ms)
            for path in [root.join(".git").join("index"), root.join(".git").join("logs").join("HEAD")] {
                if let Ok(modified) = std::fs::metadata(path).and_then(|m| m.modified())
                    && let Ok(elapsed) = modified.duration_since(std::time::UNIX_EPOCH)
                {
                    summary.last_activity_utc_ms =
                        summary.last_activity_utc_ms.max(elapsed.as_millis() as i64);
                }
            }
            snapshot_opt = Some(snapshot);
        }
        Ok(Err(error)) => summary.status_error = Some(error.to_string()),
        Err(_) => summary.status_error = Some("Git status check timed out".to_string()),
    }
    (summary, snapshot_opt)
}

pub async fn refresh_and_broadcast_repositories(
    state: &AppState,
) -> Result<Vec<RepoSummary>, ApiFailure> {
    let repos = state
        .storage
        .list_repositories()
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    let mut projects = Vec::with_capacity(repos.len());
    let mut snapshots = Vec::new();

    for repo in repos {
        let repo_id = repo.repo_id.clone();
        let (summary, snapshot_opt) = summarize_repository(state, repo).await;
        if let Some(snapshot) = snapshot_opt {
            snapshots.push((repo_id, snapshot));
        }
        projects.push(summary);
    }

    if !snapshots.is_empty() {
        let mut ws_cache = state.workspace_cache.lock().await;
        for (repo_id, snapshot) in snapshots {
            let changed = match ws_cache.get(&repo_id) {
                Some((_, old_snap)) => old_snap != &snapshot,
                None => true,
            };
            if changed {
                ws_cache.insert(repo_id.clone(), (Instant::now(), snapshot.clone()));
                let _ = state.event_tx.send(ServerEvent::WorkspaceUpdated {
                    repo_id,
                    snapshot,
                });
            }
        }
    }

    let mut p_cache = state.project_cache.lock().await;
    let old_projects = p_cache.as_ref().map(|(_, p)| p.clone()).unwrap_or_default();
    for project in &projects {
        if old_projects.iter().find(|old| old.repo_id == project.repo_id) != Some(project) {
            let _ = state.event_tx.send(ServerEvent::ProjectUpdated(project.clone()));
        }
    }
    if old_projects
        .iter()
        .any(|old| !projects.iter().any(|p| p.repo_id == old.repo_id))
    {
        let _ = state.event_tx.send(ServerEvent::ResyncRequired);
    }
    *p_cache = Some((Instant::now(), projects.clone()));

    Ok(projects)
}

pub async fn list_project_summaries(state: &AppState) -> Result<Vec<RepoSummary>, ApiFailure> {
    // Hot in-memory cache delivers results in < 0.1ms without disk latency.
    {
        let cache = state.project_cache.lock().await;
        if let Some((at, projects)) = &*cache
            && at.elapsed() < Duration::from_secs(5) {
                return Ok(projects.clone());
            }
    }
    refresh_and_broadcast_repositories(state).await
}
