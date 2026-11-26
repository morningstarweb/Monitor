use crate::executor::GitExecutor;
use crate::operations::redact_secrets;
use monitor_core::{BranchInfo, CoreError};
use std::collections::HashSet;
use std::path::Path;

/// Lists all local and remote tracking branches with upstream status.
pub async fn list_branches(
    executor: &GitExecutor,
    repo_root: &Path,
) -> Result<Vec<BranchInfo>, CoreError> {
    let mut cmd = executor.create_command(repo_root);
    cmd.args([
        "branch",
        "-a",
        "--format=%(refname)|%(refname:short)|%(upstream:short)|%(HEAD)|%(upstream:track)",
    ]);

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to list branches: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git branch -a failed: {err}")));
    }

    let text = String::from_utf8_lossy(&output.stdout);
    let merged_set = fetch_merged_set(executor, repo_root).await;
    let mut branches = Vec::new();

    for line in text.lines() {
        if let Some(info) = parse_branch_line(executor, repo_root, line, &merged_set).await {
            branches.push(info);
        }
    }

    Ok(branches)
}

async fn fetch_merged_set(executor: &GitExecutor, repo_root: &Path) -> HashSet<String> {
    // Query branches that are already merged into HEAD
    let mut merged_cmd = executor.create_command(repo_root);
    merged_cmd.args(["branch", "-a", "--merged", "HEAD"]);
    match executor.output(&mut merged_cmd).await {
        Ok(out) if out.status.success() => String::from_utf8_lossy(&out.stdout)
            .lines()
            .map(|l| l.trim().trim_start_matches('*').trim().to_string())
            .filter(|s| !s.is_empty())
            .collect(),
        _ => HashSet::new(),
    }
}

async fn parse_branch_line(
    executor: &GitExecutor,
    repo_root: &Path,
    line: &str,
    merged_set: &HashSet<String>,
) -> Option<BranchInfo> {
    let trimmed = line.trim();
    if trimmed.is_empty() {
        return None;
    }

    let parts: Vec<&str> = trimmed.split('|').collect();
    if parts.len() < 5 {
        return None;
    }

    let full_ref = parts[0].trim();
    let short_name = parts[1].trim();

    // Skip symbolic HEAD pointers and bare origin remote pointer
    if full_ref.ends_with("/HEAD") || short_name == "origin" || short_name.ends_with("/HEAD") {
        return None;
    }

    let upstream = if parts[2].trim().is_empty() {
        None
    } else {
        Some(parts[2].trim().to_string())
    };

    let is_current = parts[3].trim() == "*";
    let (ahead_count, behind_count) = parse_track_counts(parts[4].trim());

    let is_remote = full_ref.starts_with("refs/remotes/")
        || short_name.starts_with("origin/")
        || short_name.starts_with("remotes/");

    let mut is_merged = is_current
        || merged_set.contains(short_name)
        || merged_set.contains(full_ref)
        || merged_set.iter().any(|m| m.ends_with(short_name));

    let (unmerged_commits, changed_files) = if !is_remote && !is_current && !is_merged {
        fetch_unmerged_details(executor, repo_root, short_name, &mut is_merged).await
    } else {
        (0, Vec::new())
    };

    Some(BranchInfo {
        name: short_name.to_string(),
        is_current,
        is_remote,
        upstream,
        ahead_count,
        behind_count,
        is_merged,
        unmerged_commits,
        changed_files,
    })
}

fn parse_track_counts(track: &str) -> (usize, usize) {
    let parse_after = |label: &str| -> usize {
        track
            .find(label)
            .map(|pos| {
                track[pos + label.len()..]
                    .chars()
                    .take_while(char::is_ascii_digit)
                    .collect::<String>()
                    .parse()
                    .unwrap_or(0)
            })
            .unwrap_or(0)
    };
    (parse_after("ahead "), parse_after("behind "))
}

async fn fetch_unmerged_details(
    executor: &GitExecutor,
    repo_root: &Path,
    short_name: &str,
    is_merged: &mut bool,
) -> (usize, Vec<String>) {
    let mut count_cmd = executor.create_command(repo_root);
    count_cmd.args(["rev-list", "--count", &format!("HEAD..{short_name}")]);
    let mut unmerged_commits = 0;
    if let Ok(count_out) = executor.output(&mut count_cmd).await
        && count_out.status.success()
    {
        let cnt_str = String::from_utf8_lossy(&count_out.stdout);
        unmerged_commits = cnt_str.trim().parse::<usize>().unwrap_or(0);
    }
    if unmerged_commits == 0 {
        *is_merged = true;
        return (0, Vec::new());
    }

    let mut diff_cmd = executor.create_command(repo_root);
    diff_cmd.args(["diff", "--name-only", &format!("HEAD...{short_name}")]);
    let changed_files = if let Ok(diff_out) = executor.output(&mut diff_cmd).await
        && diff_out.status.success()
    {
        String::from_utf8_lossy(&diff_out.stdout)
            .lines()
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .take(25)
            .map(str::to_string)
            .collect()
    } else {
        Vec::new()
    };
    (unmerged_commits, changed_files)
}
