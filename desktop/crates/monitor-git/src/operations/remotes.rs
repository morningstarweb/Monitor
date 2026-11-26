/// Fetches from remotes.
pub async fn fetch(
    executor: &GitExecutor,
    repo_root: &Path,
    remote: Option<&str>,
) -> Result<String, CoreError> {
    validate_network_arguments(remote, None)?;
    let mut cmd = executor.create_command(repo_root);
    cmd.arg("fetch");
    if let Some(r) = remote {
        cmd.arg(r);
    } else {
        cmd.arg("--all");
    }

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("git fetch failed: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git fetch failed: {err}")));
    }

    Ok("Fetch complete".to_string())
}

/// Pulls changes from upstream.
pub async fn pull(
    executor: &GitExecutor,
    repo_root: &Path,
    remote: Option<&str>,
    branch: Option<&str>,
) -> Result<String, CoreError> {
    validate_network_arguments(remote, branch)?;
    let mut cmd = executor.create_command(repo_root);
    cmd.arg("pull");
    if let Some(r) = remote {
        cmd.arg(r);
    }
    if let Some(b) = branch {
        cmd.arg(b);
    }

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("git pull failed: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git pull failed: {err}")));
    }

    let msg = String::from_utf8_lossy(&output.stdout);
    if msg.to_ascii_lowercase().contains("already up to date") {
        Ok("Already up-to-date".to_string())
    } else {
        Ok("Pull complete".to_string())
    }
}

/// Pushes current branch to remote.
pub async fn push(
    executor: &GitExecutor,
    repo_root: &Path,
    remote: Option<&str>,
    branch: Option<&str>,
    set_upstream: bool,
) -> Result<String, CoreError> {
    validate_network_arguments(remote, branch)?;
    let mut cmd = executor.create_command(repo_root);
    cmd.arg("push");
    if set_upstream {
        cmd.arg("-u");
    }
    if let Some(r) = remote {
        cmd.arg(r);
    }
    if let Some(b) = branch {
        cmd.arg(b);
    }

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("git push failed: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git push failed: {err}")));
    }

    let combined = format!(
        "{} {}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    if combined
        .to_ascii_lowercase()
        .contains("everything up-to-date")
    {
        Ok("Everything up-to-date".to_string())
    } else {
        Ok("Push complete".to_string())
    }
}

fn validate_network_arguments(remote: Option<&str>, branch: Option<&str>) -> Result<(), CoreError> {
    if let Some(remote) = remote
        && (remote.is_empty()
            || remote.starts_with(['-', '.'])
            || !remote
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || matches!(c, '_' | '-' | '.' | '/')))
    {
        return Err(CoreError::InvalidArgument(
            "Select a configured remote name, without URLs or command options".to_string(),
        ));
    }
    if let Some(branch) = branch {
        validate_branch_name(branch)?;
        if remote.is_none() {
            return Err(CoreError::InvalidArgument(
                "Select a remote when specifying a branch".to_string(),
            ));
        }
    }
    Ok(())
}
