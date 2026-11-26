use super::list::list_branches;
use super::validation::validate_branch_name;
use crate::executor::GitExecutor;
use crate::operations::redact_secrets;
use monitor_core::CoreError;
use std::path::Path;

/// Merges a specified branch into the currently checked-out branch.
pub async fn merge_branch(
    executor: &GitExecutor,
    repo_root: &Path,
    source_branch: &str,
) -> Result<String, CoreError> {
    validate_branch_name(source_branch)?;

    let mut cmd = executor.create_command(repo_root);
    cmd.args(["merge", "--no-edit", "--", source_branch]);

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git merge: {e}")))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);

    if !output.status.success() {
        let err = redact_secrets(&stderr);
        abort_failed_merge(executor, repo_root).await;

        if stdout.contains("CONFLICT")
            || stderr.contains("CONFLICT")
            || err.contains("Automatic merge failed")
        {
            return Err(CoreError::Conflict(format!("Conflict in {source_branch}")));
        }
        return Err(CoreError::Internal(format!(
            "Failed to merge {source_branch}"
        )));
    }

    let msg = if stdout.contains("Already up to date") {
        format!("Up to date with {source_branch}")
    } else {
        format!("Merged {source_branch}")
    };

    Ok(msg)
}

/// Merges all specified branches (or all unmerged local branches) into the current checked-out branch.
pub async fn merge_all_branches(
    executor: &GitExecutor,
    repo_root: &Path,
    source_branches: &[String],
) -> Result<String, CoreError> {
    let current_branch = read_current_branch(executor, repo_root).await?;
    let mut branches_to_process =
        collect_merge_targets(executor, repo_root, source_branches, &current_branch).await?;

    if branches_to_process.is_empty() {
        return Ok(format!(
            "All branches are already merged into '{current_branch}'"
        ));
    }

    // Sort ascending by unmerged commit count so ancestor branches are merged in lineage order
    branches_to_process.sort_by_key(|(_, count)| *count);

    let mut successfully_merged = Vec::new();
    let mut already_included = Vec::new();

    for (target_branch, _) in branches_to_process {
        if count_unmerged_commits(executor, repo_root, &target_branch).await == 0 {
            already_included.push(target_branch);
            continue;
        }

        let mut merge_cmd = executor.create_command(repo_root);
        merge_cmd.args(["merge", "--no-edit", "--", &target_branch]);
        let merge_out = executor.output(&mut merge_cmd).await.map_err(|e| {
            CoreError::Internal(format!(
                "Failed to execute git merge for '{target_branch}': {e}"
            ))
        })?;

        let stdout = String::from_utf8_lossy(&merge_out.stdout);
        let stderr = String::from_utf8_lossy(&merge_out.stderr);

        if !merge_out.status.success() {
            let err = redact_secrets(&stderr);
            abort_failed_merge(executor, repo_root).await;

            if stdout.contains("CONFLICT")
                || stderr.contains("CONFLICT")
                || err.contains("Automatic merge failed")
            {
                return Err(CoreError::Conflict(format!(
                    "Conflict in '{target_branch}'"
                )));
            }
            return Err(CoreError::Internal(format!(
                "Failed to merge '{target_branch}': {err}"
            )));
        }

        successfully_merged.push(target_branch);
    }

    let summary = if !successfully_merged.is_empty() {
        format!(
            "Successfully merged {} branch(es) into '{}': {}",
            successfully_merged.len(),
            current_branch,
            successfully_merged.join(", ")
        )
    } else if !already_included.is_empty() {
        format!(
            "Branches already merged into '{}': {}",
            current_branch,
            already_included.join(", ")
        )
    } else {
        format!("All branches are up to date with '{current_branch}'")
    };

    Ok(summary)
}

async fn abort_failed_merge(executor: &GitExecutor, repo_root: &Path) {
    // Crucial safety guarantee: Always abort a failed merge so the worktree
    // and index are restored cleanly and not left locked in MERGE_HEAD state.
    let mut abort_cmd = executor.create_command(repo_root);
    abort_cmd.args(["merge", "--abort"]);
    let _ = executor.output(&mut abort_cmd).await;
}

async fn read_current_branch(
    executor: &GitExecutor,
    repo_root: &Path,
) -> Result<String, CoreError> {
    let mut curr_cmd = executor.create_command(repo_root);
    curr_cmd.args(["rev-parse", "--abbrev-ref", "HEAD"]);
    let curr_out = executor
        .output(&mut curr_cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to determine current branch: {e}")))?;
    if !curr_out.status.success() {
        return Err(CoreError::Internal(
            "HEAD is not pointing to a valid branch".to_string(),
        ));
    }
    Ok(String::from_utf8_lossy(&curr_out.stdout).trim().to_string())
}

async fn count_unmerged_commits(
    executor: &GitExecutor,
    repo_root: &Path,
    branch_name: &str,
) -> usize {
    let mut cmd = executor.create_command(repo_root);
    cmd.args(["rev-list", "--count", &format!("HEAD..{branch_name}")]);
    if let Ok(out) = executor.output(&mut cmd).await
        && out.status.success()
    {
        return String::from_utf8_lossy(&out.stdout)
            .trim()
            .parse::<usize>()
            .unwrap_or(0);
    }
    0
}

async fn collect_merge_targets(
    executor: &GitExecutor,
    repo_root: &Path,
    source_branches: &[String],
    current_branch: &str,
) -> Result<Vec<(String, usize)>, CoreError> {
    let mut targets = Vec::new();
    if !source_branches.is_empty() {
        for b in source_branches {
            validate_branch_name(b)?;
            if b == current_branch {
                continue;
            }
            let count = count_unmerged_commits(executor, repo_root, b).await;
            if count > 0 {
                targets.push((b.clone(), count));
            }
        }
    } else {
        let all_branches = list_branches(executor, repo_root).await?;
        for b in all_branches {
            if !b.is_remote && !b.is_current && !b.is_merged && b.unmerged_commits > 0 {
                targets.push((b.name, b.unmerged_commits));
            }
        }
    }
    Ok(targets)
}
