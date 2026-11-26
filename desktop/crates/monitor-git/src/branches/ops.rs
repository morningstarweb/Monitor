use super::validation::{sanitize_branch_name, validate_branch_name};
use crate::executor::GitExecutor;
use crate::operations::redact_secrets;
use monitor_core::CoreError;
use std::path::Path;

/// Creates a new branch from an optional start point.
/// Automatically sanitizes the branch name (e.g. converting spaces to hyphens) if needed.
pub async fn create_branch(
    executor: &GitExecutor,
    repo_root: &Path,
    branch_name: &str,
    start_point: Option<&str>,
) -> Result<(), CoreError> {
    let clean_name = sanitize_branch_name(branch_name);
    validate_branch_name(&clean_name)?;

    let mut cmd = executor.create_command(repo_root);
    cmd.args(["branch", "--", &clean_name]);
    if let Some(sp) = start_point {
        let clean_sp = sanitize_branch_name(sp);
        validate_branch_name(&clean_sp)?;
        cmd.arg(&clean_sp);
    }

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to create branch: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!(
            "git branch creation failed: {err}"
        )));
    }

    Ok(())
}

/// Switches to target branch with dirty-worktree conflict detection per PRD §5.3.4.
pub async fn switch_branch(
    executor: &GitExecutor,
    repo_root: &Path,
    branch_name: &str,
) -> Result<(), CoreError> {
    validate_branch_name(branch_name)?;

    let mut cmd = executor.create_command(repo_root);
    cmd.args(["switch", "--", branch_name]);

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to switch branch: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        if err.contains("overwritten by checkout") || err.contains("overwritten by switch") {
            return Err(CoreError::Conflict(format!(
                "DIRTY_WORKTREE_CONFLICT: {err}"
            )));
        }
        return Err(CoreError::Internal(format!("git switch failed: {err}")));
    }

    Ok(())
}

/// Deletes a local branch (safe -d or force -D per PRD §5.3.4).
/// Never allows deleting protected default branches ("main", "master").
pub async fn delete_branch(
    executor: &GitExecutor,
    repo_root: &Path,
    branch_name: &str,
    force: bool,
) -> Result<(), CoreError> {
    validate_branch_name(branch_name)?;

    let trimmed = branch_name.trim();
    if trimmed.eq_ignore_ascii_case("main")
        || trimmed.eq_ignore_ascii_case("master")
        || trimmed.eq_ignore_ascii_case("origin/main")
        || trimmed.eq_ignore_ascii_case("origin/master")
    {
        return Err(CoreError::InvalidArgument(format!(
            "Protected branch cannot be deleted: '{trimmed}' is the default safety branch"
        )));
    }

    let flag = if force { "-D" } else { "-d" };
    let mut cmd = executor.create_command(repo_root);
    cmd.args(["branch", flag, "--", branch_name]);

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to delete branch: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        if err.contains("is not fully merged") {
            return Err(CoreError::Conflict("NEEDS_FORCE_CONFIRMATION".to_string()));
        }
        return Err(CoreError::Internal(format!(
            "git branch deletion failed: {err}"
        )));
    }

    Ok(())
}
