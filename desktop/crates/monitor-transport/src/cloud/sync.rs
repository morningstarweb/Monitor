pub fn broker_base_url() -> String {
    std::env::var("MONITOR_BROKER_URL").unwrap_or_else(|_| BROKER_BASE_URL.to_string())
}

#[derive(Debug, Clone, Deserialize)]
pub struct PendingCommand {
    pub id: String,
    #[serde(rename = "type")]
    pub command_type: String,
    pub payload: serde_json::Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResult {
    pub id: String,
    pub status: String, // "completed" | "failed"
    #[serde(skip_serializing_if = "Option::is_none")]
    pub result: Option<String>,
}

#[derive(Debug, Clone)]
pub struct PrCacheEntry {
    pub fetched_at: std::time::Instant,
    pub pull_requests: Vec<PullRequestSummary>,
}

/// Collects workspace snapshots for all registered repositories.
pub async fn collect_workspaces(state: &AppState) -> HashMap<String, GitWorkspaceSnapshot> {
    let mut workspaces = HashMap::new();
    let Ok(repos) = state.storage.list_repositories() else {
        return workspaces;
    };

    for repo in repos {
        let root = std::path::PathBuf::from(&repo.canonical_path);
        let lock = state
            .repo_coordinator
            .get_or_create_repo_lock(&repo.repo_id, Revision(1))
            .await;
        let guard = lock.lock().await;
        if let Ok(snapshot) =
            monitor_git::query_git_status(&state.git_executor, &root, &repo.repo_id, guard.revision)
                .await
        {
            workspaces.insert(repo.repo_id.as_str().to_string(), snapshot);
        }
    }
    workspaces
}

/// Collects pull requests for registered repositories using a 30s TTL cache.
pub async fn collect_pull_requests(
    state: &AppState,
    pr_cache: &mut HashMap<RepoId, PrCacheEntry>,
) -> HashMap<String, Vec<PullRequestSummary>> {
    let mut pr_map = HashMap::new();
    let Ok(repos) = state.storage.list_repositories() else {
        return pr_map;
    };

    let now = std::time::Instant::now();
    for repo in repos {
        let repo_id = repo.repo_id.clone();
        if let Some(entry) = pr_cache.get(&repo_id)
            && entry.fetched_at.elapsed() < Duration::from_secs(30)
        {
            pr_map.insert(repo_id.as_str().to_string(), entry.pull_requests.clone());
            continue;
        }

        let root = std::path::PathBuf::from(repo.canonical_path);
        let prs = tokio::task::spawn_blocking(move || {
            monitor_git::list_pull_requests(&root).unwrap_or_default()
        })
        .await
        .unwrap_or_default();

        pr_cache.insert(
            repo_id.clone(),
            PrCacheEntry {
                fetched_at: now,
                pull_requests: prs.clone(),
            },
        );
        pr_map.insert(repo_id.as_str().to_string(), prs);
    }
    pr_map
}

