/// Creates a commit with the given message summary and optional body.
pub async fn commit(
    executor: &GitExecutor,
    repo_root: &Path,
    message_summary: &str,
    message_body: Option<&str>,
    stage_all_tracked: bool,
) -> Result<String, CoreError> {
    if message_summary.trim().is_empty() {
        return Err(CoreError::InvalidArgument(
            "Commit summary cannot be empty".to_string(),
        ));
    }

    if stage_all_tracked {
        let mut add_cmd = executor.create_command(repo_root);
        add_cmd.args(["add", "-A"]);
        let add_out = executor
            .output(&mut add_cmd)
            .await
            .map_err(|e| CoreError::Internal(format!("git add -A failed: {e}")))?;
        if !add_out.status.success() {
            let err = redact_secrets(&String::from_utf8_lossy(&add_out.stderr));
            return Err(CoreError::Internal(format!("git add -A failed: {err}")));
        }
    }

    let full_message = match message_body {
        Some(body) if !body.trim().is_empty() => {
            format!("{}\n\n{}", message_summary.trim(), body.trim())
        }
        _ => message_summary.trim().to_string(),
    };

    let mut commit_cmd = executor.create_command(repo_root);
    commit_cmd.args(["commit", "-F", "-"]);
    commit_cmd.stdin(Stdio::piped());

    // Auto-heal missing Git author identity so commits from mobile never fail with "Author identity unknown"
    let author_name_res = executor
        .create_command(repo_root)
        .args(["config", "user.name"])
        .output()
        .await;
    let author_name = match author_name_res {
        Ok(out) if out.status.success() && !out.stdout.is_empty() => {
            String::from_utf8_lossy(&out.stdout).trim().to_string()
        }
        _ => String::new(),
    };

    let author_email_res = executor
        .create_command(repo_root)
        .args(["config", "user.email"])
        .output()
        .await;
    let author_email = match author_email_res {
        Ok(out) if out.status.success() && !out.stdout.is_empty() => {
            String::from_utf8_lossy(&out.stdout).trim().to_string()
        }
        _ => String::new(),
    };

    if author_name.is_empty() || author_email.is_empty() {
        return Err(CoreError::InvalidArgument(
            "Git author identity not set. Please set your Name and Email in Settings.".to_string(),
        ));
    }

    let mut child = commit_cmd
        .spawn()
        .map_err(|e| CoreError::Internal(format!("Failed to spawn git commit: {e}")))?;

    if let Some(mut stdin) = child.stdin.take() {
        stdin
            .write_all(full_message.as_bytes())
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to write commit message: {e}")))?;
    }

    let output = tokio::time::timeout(
        std::time::Duration::from_secs(120),
        child.wait_with_output(),
    )
    .await
    .map_err(|_| {
        CoreError::Internal(
            "Git commit timed out; review repository state before retrying".to_string(),
        )
    })?
    .map_err(|e| CoreError::Internal(format!("git commit failed: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git commit failed: {err}")));
    }

    // Get the new HEAD commit SHA
    let mut rev_cmd = executor.create_command(repo_root);
    rev_cmd.args(["rev-parse", "HEAD"]);
    let rev_out = executor
        .output(&mut rev_cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("git rev-parse HEAD failed: {e}")))?;

    if !rev_out.status.success() {
        return Err(CoreError::Internal(
            "Failed to resolve new commit OID".to_string(),
        ));
    }

    Ok(String::from_utf8_lossy(&rev_out.stdout).trim().to_string())
}

/// Commits changes and immediately pushes to remote. Partial success model per PRD §5.3.3.
pub async fn commit_and_push(
    executor: &GitExecutor,
    repo_root: &Path,
    message_summary: &str,
    message_body: Option<&str>,
    stage_all_tracked: bool,
) -> Result<(String, bool, Option<String>), CoreError> {
    let commit_oid = commit(
        executor,
        repo_root,
        message_summary,
        message_body,
        stage_all_tracked,
    )
    .await?;

    let mut push_cmd = executor.create_command(repo_root);
    push_cmd.arg("push");

    let push_out = executor.output(&mut push_cmd).await;
    match push_out {
        Ok(out) if out.status.success() => Ok((commit_oid, true, None)),
        Ok(out) => {
            let err = redact_secrets(&String::from_utf8_lossy(&out.stderr));
            Ok((commit_oid, false, Some(err)))
        }
        Err(e) => Ok((commit_oid, false, Some(e.to_string()))),
    }
}
