use super::parse::{MAX_DIFF_BYTES, MAX_DIFF_LINES, parse_unified_diff};
use crate::executor::GitExecutor;
use crate::path_guard::{resolve_safe_target_path, validate_repo_relative_path};
use monitor_core::{
    CoreError, DiffHunk, DiffLine, DiffLineType, FileChangeCategory, FileDiffResponse,
    GitFileChange,
};
use std::collections::HashMap;
use std::path::Path;
use tokio::io::AsyncReadExt;

/// Validates a commit OID or `HEAD` reference so arguments cannot inject Git CLI flags.
pub fn validate_commit_oid(raw: &str) -> Result<String, CoreError> {
    let trimmed = raw.trim();
    if trimmed == "HEAD" {
        return Ok("HEAD".to_string());
    }
    let valid_hex =
        (4..=64).contains(&trimmed.len()) && trimmed.bytes().all(|b| b.is_ascii_hexdigit());
    if !valid_hex {
        return Err(CoreError::InvalidArgument(
            "Invalid commit identifier: expected 4-64 hexadecimal characters or HEAD".to_string(),
        ));
    }
    Ok(trimmed.to_ascii_lowercase())
}

/// Queries diff for a specific file.
pub async fn query_file_diff(
    executor: &GitExecutor,
    repo_root: &Path,
    relative_path: &str,
    staged: bool,
    commit: Option<&str>,
) -> Result<FileDiffResponse, CoreError> {
    let clean_path = validate_repo_relative_path(relative_path)?;
    let disk_file = resolve_safe_target_path(repo_root, relative_path)?;
    let validated_commit = commit.map(validate_commit_oid).transpose()?;
    let mut cmd = executor.create_command(repo_root);

    if let Some(ref commit_oid) = validated_commit {
        cmd.args([
            "show",
            "--no-ext-diff",
            "--no-textconv",
            "--format=",
            commit_oid.as_str(),
            "--",
        ]);
    } else if staged {
        cmd.args(["diff", "--no-ext-diff", "--no-textconv", "--cached", "--"]);
    } else {
        cmd.args(["diff", "--no-ext-diff", "--no-textconv", "--"]);
    }
    cmd.arg(clean_path.to_string_lossy().as_ref());

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git diff: {e}")))?;

    if !output.status.success() {
        let err = crate::operations::redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git diff failed: {err}")));
    }

    if commit.is_some() || staged {
        return Ok(parse_unified_diff(relative_path, &output.stdout));
    }

    // If git diff returned empty stdout and not cached, check if file is untracked on disk
    if output.stdout.is_empty() && !staged {
        let mut tracked = executor.create_command(repo_root);
        tracked
            .args(["ls-files", "--error-unmatch", "--"])
            .arg(&clean_path);
        if executor
            .output(&mut tracked)
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to check tracked file: {e}")))?
            .status
            .success()
        {
            return Ok(parse_unified_diff(relative_path, &output.stdout));
        }
    }
    if output.stdout.is_empty()
        && !staged
        && disk_file.is_file()
        && let Ok(file) = tokio::fs::File::open(&disk_file).await
    {
        return read_untracked_file_diff(relative_path, file).await;
    }

    Ok(parse_unified_diff(relative_path, &output.stdout))
}

async fn read_untracked_file_diff(
    relative_path: &str,
    file: tokio::fs::File,
) -> Result<FileDiffResponse, CoreError> {
    let mut bytes = Vec::new();
    let size = file
        .metadata()
        .await
        .map_err(|e| CoreError::Internal(e.to_string()))?
        .len();
    file.take(MAX_DIFF_BYTES as u64 + 1)
        .read_to_end(&mut bytes)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to read selected file: {e}")))?;
    let check_len = bytes.len().min(8192);
    if bytes[..check_len].contains(&0) {
        return Ok(FileDiffResponse {
            path: relative_path.to_string(),
            is_binary: true,
            is_truncated: false,
            old_size_bytes: None,
            new_size_bytes: Some(size),
            insertions: 0,
            deletions: 0,
            hunks: Vec::new(),
        });
    }

    let content_str = String::from_utf8_lossy(&bytes);
    let lines: Vec<&str> = content_str.lines().collect();
    let is_truncated = lines.len() > MAX_DIFF_LINES || bytes.len() > MAX_DIFF_BYTES;
    let take_lines = lines.len().min(MAX_DIFF_LINES);
    let diff_lines: Vec<DiffLine> = lines[..take_lines]
        .iter()
        .enumerate()
        .map(|(idx, l)| DiffLine {
            line_type: DiffLineType::Addition,
            old_line_num: None,
            new_line_num: Some((idx + 1) as u32),
            content: l.to_string(),
        })
        .collect();

    let hunk = DiffHunk {
        old_start: 0,
        old_lines: 0,
        new_start: 1,
        new_lines: take_lines as u32,
        header: format!("@@ -0,0 +1,{take_lines} @@"),
        lines: diff_lines,
    };

    Ok(FileDiffResponse {
        path: relative_path.to_string(),
        is_binary: false,
        is_truncated,
        old_size_bytes: None,
        new_size_bytes: Some(size),
        insertions: take_lines as u32,
        deletions: 0,
        hunks: if take_lines > 0 {
            vec![hunk]
        } else {
            Vec::new()
        },
    })
}

/// Queries the list of files modified, added, or deleted in a specific commit with numstat metrics.
pub async fn query_commit_files(
    executor: &GitExecutor,
    repo_root: &Path,
    commit_oid: &str,
) -> Result<Vec<GitFileChange>, CoreError> {
    let safe_oid = validate_commit_oid(commit_oid)?;
    let mut status_cmd = executor.create_command(repo_root);
    status_cmd.args([
        "show",
        "-m",
        "--first-parent",
        "--name-status",
        "--format=",
        safe_oid.as_str(),
        "--",
    ]);
    let status_out = executor
        .output(&mut status_cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git show status: {e}")))?;

    if !status_out.status.success() {
        let err = crate::operations::redact_secrets(&String::from_utf8_lossy(&status_out.stderr));
        return Err(CoreError::Internal(format!(
            "git show status failed: {err}"
        )));
    }

    let mut numstat_cmd = executor.create_command(repo_root);
    numstat_cmd.args([
        "show",
        "-m",
        "--first-parent",
        "--numstat",
        "--format=",
        safe_oid.as_str(),
        "--",
    ]);
    let numstat_out = executor
        .output(&mut numstat_cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git show numstat: {e}")))?;

    let stats_map = parse_numstat_map(&String::from_utf8_lossy(&numstat_out.stdout));
    Ok(parse_commit_file_changes(
        &String::from_utf8_lossy(&status_out.stdout),
        &stats_map,
    ))
}

fn parse_numstat_map(numstat_text: &str) -> HashMap<String, (u32, u32)> {
    let mut stats_map = HashMap::new();
    for line in numstat_text.lines() {
        let parts: Vec<&str> = line.split('\t').collect();
        if parts.len() >= 3 {
            let ins = parts[0].parse::<u32>().unwrap_or(0);
            let del = parts[1].parse::<u32>().unwrap_or(0);
            stats_map.insert(parts[2].trim().to_string(), (ins, del));
        }
    }
    stats_map
}

fn parse_commit_file_changes(
    status_text: &str,
    stats_map: &HashMap<String, (u32, u32)>,
) -> Vec<GitFileChange> {
    let mut files = Vec::new();
    for line in status_text.lines() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let parts: Vec<&str> = line.split('\t').collect();
        if parts.is_empty() {
            continue;
        }

        let status_char = parts[0].chars().next().unwrap_or('M');
        let (path, orig_path) = if parts.len() >= 3 && (status_char == 'R' || status_char == 'C') {
            (
                parts[2].trim().to_string(),
                Some(parts[1].trim().to_string()),
            )
        } else if parts.len() >= 2 {
            (parts[1].trim().to_string(), None)
        } else {
            continue;
        };

        let (insertions, deletions) = stats_map.get(&path).copied().unwrap_or((0, 0));
        let category = match status_char {
            'A' => FileChangeCategory::Added,
            'D' => FileChangeCategory::Deleted,
            'R' => FileChangeCategory::Renamed,
            'C' => FileChangeCategory::Copied,
            _ => FileChangeCategory::Modified,
        };

        files.push(GitFileChange {
            path,
            original_path: orig_path,
            category,
            staged: false,
            insertions,
            deletions,
        });
    }
    files
}
