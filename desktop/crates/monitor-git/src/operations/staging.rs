pub fn redact_secrets(input: &str) -> String {
    let mut result = input.to_string();
    // Strip all URL userinfo, including passwords without a recognizable token prefix.
    let schemes: Vec<usize> = result.match_indices("://").map(|(at, _)| at + 3).collect();
    for start in schemes.into_iter().rev() {
        let end = result[start..]
            .find(|c: char| c.is_whitespace() || c == '/' || c == '\'' || c == '"')
            .map_or(result.len(), |len| start + len);
        if let Some(at) = result[start..end].rfind('@') {
            result.replace_range(start..start + at, "[REDACTED_CREDENTIAL]");
        }
    }
    let patterns = ["ghp_", "github_pat_", "glpat-", "Bearer "];
    for pat in patterns {
        while let Some(start) = result.find(pat) {
            let token_start = start + pat.len();
            let end = result[token_start..]
                .find(|c: char| c.is_whitespace() || matches!(c, '@' | '\'' | '"' | '<' | '>'))
                .map_or(result.len(), |len| token_start + len);
            result.replace_range(start..end, "[REDACTED_CREDENTIAL]");
        }
    }
    result
}

/// Stages specific relative paths in the repository.
pub async fn stage_files(
    executor: &GitExecutor,
    repo_root: &Path,
    paths: &[String],
) -> Result<(), CoreError> {
    if paths.is_empty() {
        return Ok(());
    }

    let mut validated = Vec::with_capacity(paths.len());
    for p in paths {
        let clean = validate_repo_relative_path(p)?;
        resolve_safe_target_path(repo_root, p)?;
        validated.push(clean.to_string_lossy().to_string());
    }

    let mut cmd = executor.create_command(repo_root);
    cmd.arg("add").arg("--");
    for p in &validated {
        cmd.arg(p);
    }

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git add: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git add failed: {err}")));
    }

    Ok(())
}

/// Stages all changes (tracked and untracked).
pub async fn stage_all(executor: &GitExecutor, repo_root: &Path) -> Result<(), CoreError> {
    let mut cmd = executor.create_command(repo_root);
    cmd.args(["add", "-A"]);

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git add -A: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git add -A failed: {err}")));
    }

    Ok(())
}

/// Unstages specific relative paths.
pub async fn unstage_files(
    executor: &GitExecutor,
    repo_root: &Path,
    paths: &[String],
) -> Result<(), CoreError> {
    if paths.is_empty() {
        return Ok(());
    }

    let mut validated = Vec::with_capacity(paths.len());
    for p in paths {
        let clean = validate_repo_relative_path(p)?;
        resolve_safe_target_path(repo_root, p)?;
        validated.push(clean.to_string_lossy().to_string());
    }

    let mut cmd = executor.create_command(repo_root);
    cmd.args(["restore", "--staged", "--"]);
    for p in &validated {
        cmd.arg(p);
    }

    let output = executor.output(&mut cmd).await;
    match output {
        Ok(out) if out.status.success() => Ok(()),
        _ => {
            // Fallback for older git or initial commit
            let mut reset_cmd = executor.create_command(repo_root);
            reset_cmd.args(["reset", "HEAD", "--"]);
            for p in &validated {
                reset_cmd.arg(p);
            }
            let reset_out = executor
                .output(&mut reset_cmd)
                .await
                .map_err(|e| CoreError::Internal(format!("git reset fallback failed: {e}")))?;
            if !reset_out.status.success() {
                let err = redact_secrets(&String::from_utf8_lossy(&reset_out.stderr));
                return Err(CoreError::Internal(format!("git unstage failed: {err}")));
            }
            Ok(())
        }
    }
}

/// Unstages all currently staged changes.
pub async fn unstage_all(executor: &GitExecutor, repo_root: &Path) -> Result<(), CoreError> {
    let mut cmd = executor.create_command(repo_root);
    cmd.args(["restore", "--staged", "."]);

    let output = executor.output(&mut cmd).await;
    match output {
        Ok(out) if out.status.success() => Ok(()),
        _ => {
            let mut reset_cmd = executor.create_command(repo_root);
            reset_cmd.args(["reset", "HEAD"]);
            let reset_out = executor
                .output(&mut reset_cmd)
                .await
                .map_err(|e| CoreError::Internal(format!("git reset failed: {e}")))?;
            if !reset_out.status.success() {
                let err = redact_secrets(&String::from_utf8_lossy(&reset_out.stderr));
                return Err(CoreError::Internal(format!(
                    "git unstage all failed: {err}"
                )));
            }
            Ok(())
        }
    }
}
