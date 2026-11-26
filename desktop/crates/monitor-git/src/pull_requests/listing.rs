/// Lists all Pull Requests (Open, Merged, Closed) for the given repository,
/// with automatic parent repository discovery for GitHub forks.
pub fn list_pull_requests(repo_path: &Path) -> Result<Vec<PullRequestSummary>, CoreError> {
    let Some((owner, repo)) = parse_github_remote(repo_path) else {
        return Ok(Vec::new());
    };

    // 1. Try GitHub CLI (gh) first with --state all
    let fetch_gh_prs = |target_repo: Option<&str>| -> Option<Vec<PullRequestSummary>> {
        let mut cmd = gh_command(repo_path);
        cmd.args([
            "pr",
            "list",
            "--state",
            "all",
            "--limit",
            "50",
            "--json",
            "number,title,author,headRefName,baseRefName,updatedAt,url,state,isDraft",
        ]);
        if let Some(target) = target_repo {
            cmd.args(["-R", target]);
        }
        let output = run_bounded_output(cmd, std::time::Duration::from_secs(15)).ok()?;
        if !output.status.success() {
            return None;
        }
        let items: Vec<GhPrItem> = serde_json::from_slice(&output.stdout).ok()?;
        let prs = items
            .into_iter()
            .map(|item| PullRequestSummary {
                number: item.number,
                title: item.title,
                author: item
                    .author
                    .map(|a| a.login)
                    .unwrap_or_else(|| "unknown".to_string()),
                head_branch: item.head_ref_name,
                base_branch: item.base_ref_name,
                updated_at: item.updated_at,
                url: item.url,
                state: item.state,
                is_draft: item.is_draft,
            })
            .collect();
        Some(prs)
    };

    // First attempt: current repository
    if let Some(prs) = fetch_gh_prs(None)
        && !prs.is_empty()
    {
        return Ok(prs);
    }

    // Check if repository is a fork, and query upstream parent repo if so
    let gh_view = {
        let mut cmd = gh_command(repo_path);
        cmd.args(["repo", "view", "--json", "isFork,parent"]);
        run_bounded_output(cmd, std::time::Duration::from_secs(10))
    };
    if let Ok(output) = gh_view
        && output.status.success()
        && let Ok(meta) = serde_json::from_slice::<GhRepoMeta>(&output.stdout)
        && meta.is_fork
        && let Some(parent) = meta.parent
        && let Some(p_owner) = parent.owner
    {
        let parent_target = format!("{}/{}", p_owner.login, parent.name);
        if let Some(prs) = fetch_gh_prs(Some(&parent_target))
            && !prs.is_empty()
        {
            return Ok(prs);
        }
    }

    // 2. Fallback to GitHub public API
    let client = reqwest::blocking::Client::builder()
        .user_agent("Monitor-App/1.0")
        .timeout(std::time::Duration::from_secs(5))
        .build();

    let fetch_api_prs = |target_owner: &str,
                         target_repo: &str|
     -> Option<Vec<PullRequestSummary>> {
        let api_url = format!(
            "https://api.github.com/repos/{target_owner}/{target_repo}/pulls?state=all&per_page=50"
        );
        let client_ref = client.as_ref().ok()?;
        let resp = client_ref.get(&api_url).send().ok()?;
        if !resp.status().is_success() {
            return None;
        }
        let items: Vec<ApiPrItem> = resp.json().ok()?;
        let prs = items
            .into_iter()
            .map(|item| PullRequestSummary {
                number: item.number,
                title: item.title,
                author: item
                    .user
                    .map(|u| u.login)
                    .unwrap_or_else(|| "unknown".to_string()),
                head_branch: item.head.map(|h| h.branch_ref).unwrap_or_default(),
                base_branch: item.base.map(|b| b.branch_ref).unwrap_or_default(),
                updated_at: item.updated_at,
                url: item.html_url,
                state: item.state,
                is_draft: item.draft,
            })
            .collect();
        Some(prs)
    };

    if let Some(prs) = fetch_api_prs(&owner, &repo)
        && !prs.is_empty()
    {
        return Ok(prs);
    }

    Ok(Vec::new())
}
