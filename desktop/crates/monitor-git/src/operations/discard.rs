/// Discards changes to a single file. Requires explicit confirmation token.
pub async fn discard_file(
    executor: &GitExecutor,
    repo_root: &Path,
    path: &str,
    confirmation_token: &str,
    expected_revision: Revision,
) -> Result<(), CoreError> {
    let expected_token = format!("discard:{}:{}", path, expected_revision.0);
    if confirmation_token != expected_token {
        return Err(CoreError::InvalidArgument(
            "Invalid confirmation token for discard operation".to_string(),
        ));
    }

    let clean_path = validate_repo_relative_path(path)?;
    let target_file = resolve_safe_target_path(repo_root, path)?;

    // Try restoring tracked file first
    let mut restore_cmd = executor.create_command(repo_root);
    restore_cmd.args(["restore", "--worktree", "--"]);
    restore_cmd.arg(clean_path.to_string_lossy().as_ref());

    let restore_out = executor.output(&mut restore_cmd).await;
    if let Ok(out) = restore_out
        && out.status.success()
    {
        return Ok(());
    }

    // If file is untracked, clean it via git clean -f -- <path>
    if target_file.exists() {
        let mut clean_cmd = executor.create_command(repo_root);
        clean_cmd.args(["clean", "-f", "--"]);
        clean_cmd.arg(clean_path.to_string_lossy().as_ref());
        let clean_out = executor
            .output(&mut clean_cmd)
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to clean untracked file: {e}")))?;

        if clean_out.status.success() {
            return Ok(());
        }

        return Err(CoreError::Internal(
            "Git could not discard the selected file".to_string(),
        ));
    }

    Ok(())
}
