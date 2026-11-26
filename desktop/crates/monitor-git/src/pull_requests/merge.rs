/// Merges an open Pull Request on GitHub and synchronizes the local repository.
pub fn merge_pull_request(
    repo_path: &Path,
    pr_number: u64,
    method: Option<&str>,
) -> Result<String, CoreError> {
    if pr_number == 0 {
        return Err(CoreError::InvalidArgument(
            "PR number must be greater than 0".to_string(),
        ));
    }

    let pr_str = pr_number.to_string();
    let normalized_method = method.map(str::to_ascii_lowercase);
    let method_str = normalized_method.as_deref().unwrap_or("merge");
    if !matches!(method_str, "merge" | "squash" | "rebase") {
        return Err(CoreError::InvalidArgument(format!(
            "Invalid merge method '{method_str}'. Allowed methods: 'merge', 'squash', 'rebase'"
        )));
    }

    let method_flag = match method_str {
        "squash" => "--squash",
        "rebase" => "--rebase",
        _ => "--merge",
    };

    // 1. Try GitHub CLI (gh pr merge)
    let gh_merge = {
        let mut cmd = gh_command(repo_path);
        cmd.args(["pr", "merge", &pr_str, method_flag]);
        run_bounded_output(cmd, std::time::Duration::from_secs(30))
    };

    if let Ok(output) = gh_merge {
        if output.status.success() {
            // Also pull latest commits to keep local working tree synchronized
            let pull_out = {
                let mut cmd = git_command(repo_path);
                cmd.args(["pull"]);
                run_bounded_output(cmd, std::time::Duration::from_secs(30))
            };
            if let Ok(ref res) = pull_out
                && !res.status.success()
            {
                let err = String::from_utf8_lossy(&res.stderr);
                tracing::warn!("git pull after gh pr merge failed ({err}), aborting merge");
                let mut abort_cmd = git_command(repo_path);
                abort_cmd.args(["merge", "--abort"]);
                let _ = run_bounded_output(abort_cmd, std::time::Duration::from_secs(10));
            }

            return Ok(format!("Successfully merged PR #{pr_number} via GitHub"));
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            if !stderr.is_empty() {
                tracing::warn!("gh pr merge stderr: {stderr}");
            }
        }
    }

    // 2. Fallback: native Git fetch and merge using temporary remote ref
    let fetch_ref = format!("+pull/{pr_number}/head:refs/remotes/origin/pr/{pr_number}");
    let fetch_out = {
        let mut cmd = git_command(repo_path);
        cmd.args(["fetch", "origin", &fetch_ref]);
        run_bounded_output(cmd, std::time::Duration::from_secs(30))
    }
    .map_err(|e| CoreError::Internal(format!("Git fetch failed: {e}")))?;

    if !fetch_out.status.success() {
        let err = String::from_utf8_lossy(&fetch_out.stderr);
        return Err(CoreError::Internal(format!(
            "Failed fetching PR #{pr_number}: {err}"
        )));
    }

    let pr_branch = format!("origin/pr/{pr_number}");
    let merge_timeout = std::time::Duration::from_secs(30);
    let run_git = |args: &[&str]| {
        let mut cmd = git_command(repo_path);
        cmd.args(args);
        run_bounded_output(cmd, merge_timeout)
    };
    let merge_out = match method_str {
        "squash" => {
            let out = run_git(&["merge", "--squash", &pr_branch]);
            match out {
                Ok(ref res) if res.status.success() => {
                    let msg = format!("Merge pull request #{pr_number} (squash)");
                    let commit_out = run_git(&["commit", "-m", &msg]);
                    match commit_out {
                        Ok(commit_res) if commit_res.status.success() => Ok(commit_res),
                        Ok(commit_res) => {
                            abort_merge_operation(repo_path, "squash");
                            let ref_name = format!("refs/remotes/origin/pr/{pr_number}");
                            let _ = run_git(&["update-ref", "-d", &ref_name]);
                            let stderr = String::from_utf8_lossy(&commit_res.stderr);
                            return Err(CoreError::Internal(format!(
                                "Failed committing squashed PR #{pr_number}: {stderr}"
                            )));
                        }
                        Err(e) => {
                            abort_merge_operation(repo_path, "squash");
                            let ref_name = format!("refs/remotes/origin/pr/{pr_number}");
                            let _ = run_git(&["update-ref", "-d", &ref_name]);
                            return Err(CoreError::Internal(format!(
                                "Failed to execute commit for squashed PR #{pr_number}: {e}"
                            )));
                        }
                    }
                }
                other => other,
            }
        }
        "rebase" => run_git(&["rebase", &pr_branch]),
        _ => run_git(&["merge", &pr_branch]),
    }
    .map_err(|e| CoreError::Internal(format!("Git merge failed: {e}")))?;

    // Clean up temporary remote ref
    let ref_name = format!("refs/remotes/origin/pr/{pr_number}");
    let _ = run_git(&["update-ref", "-d", &ref_name]);
    let branch_name = format!("pr-{pr_number}");
    let _ = run_git(&["branch", "-D", &branch_name]);

    if !merge_out.status.success() {
        let stderr = String::from_utf8_lossy(&merge_out.stderr);
        let stdout = String::from_utf8_lossy(&merge_out.stdout);
        let combined = format!("{stderr} {stdout}");

        // Abort merge using strategy-aware cleanup to restore clean repository state
        abort_merge_operation(repo_path, method_str);

        if combined.to_ascii_lowercase().contains("conflict") {
            return Err(CoreError::Conflict(format!("Conflict in PR #{pr_number}")));
        }
        return Err(CoreError::Internal(format!(
            "Failed to merge PR #{pr_number}"
        )));
    }

    // Push merged branch back to origin
    let push_out = run_git(&["push"]);
    if let Ok(ref res) = push_out
        && !res.status.success()
    {
        let stderr = String::from_utf8_lossy(&res.stderr);
        tracing::warn!("Failed pushing merged PR to origin: {stderr}");
    }

    Ok(format!("PR #{pr_number} merged"))
}
