use crate::GitExecutor;
use monitor_core::CoreError;
use serde::{Deserialize, Serialize};
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};

static CHK_SEQ: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CheckpointInfo {
    pub checkpoint_id: String,
    pub repo_path: String,
    pub branch: String,
    pub commit_sha: String,
    pub created_at_utc_ms: i64,
    pub files_changed_count: u32,
    pub lines_added: u32,
    pub lines_deleted: u32,
    pub is_safety: bool,
    pub description: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MassChangeAlert {
    pub files_deleted: u32,
    pub lines_deleted: u32,
    pub message: String,
}

#[derive(Debug)]
pub struct CheckpointEngine;

impl CheckpointEngine {
    /// Creates a shadow git checkpoint without touching the user's working copy, HEAD, or branch.
    pub async fn create_checkpoint(
        executor: &GitExecutor,
        repo_path: &Path,
        description: &str,
        is_safety: bool,
    ) -> Result<CheckpointInfo, CoreError> {
        let now_ms = chrono::Utc::now().timestamp_millis();
        let timestamp_str = chrono::Utc::now().format("%Y%m%d_%H%M%S").to_string();

        // 1. Get current branch name
        let mut branch_cmd = executor.create_command(repo_path);
        branch_cmd.args(["symbolic-ref", "--short", "HEAD"]);
        let branch_out = executor
            .output(&mut branch_cmd)
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to query git branch: {e}")))?;
        let branch = if branch_out.status.success() {
            String::from_utf8_lossy(&branch_out.stdout)
                .trim()
                .to_string()
        } else {
            "detached".to_string()
        };

        // 2. Get current HEAD commit sha
        let mut head_cmd = executor.create_command(repo_path);
        head_cmd.args(["rev-parse", "HEAD"]);
        let head_out = executor
            .output(&mut head_cmd)
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to query HEAD commit: {e}")))?;
        let head_sha = String::from_utf8_lossy(&head_out.stdout).trim().to_string();

        // 3. Shadow index for non-destructive snapshot
        let git_dir = repo_path.join(".git");
        let seq = CHK_SEQ.fetch_add(1, Ordering::Relaxed);
        let shadow_index = git_dir.join(format!("chk_index_{now_ms}_{}_{seq}", std::process::id()));

        // Stage all working changes into shadow index
        let mut add_cmd = executor.create_command(repo_path);
        add_cmd.env("GIT_INDEX_FILE", &shadow_index);
        add_cmd.args(["add", "-A"]);
        let add_out = executor.output(&mut add_cmd).await.map_err(|e| {
            let _ = std::fs::remove_file(&shadow_index);
            CoreError::Internal(format!("Failed to stage shadow index: {e}"))
        })?;
        if !add_out.status.success() {
            let _ = std::fs::remove_file(&shadow_index);
            let err = String::from_utf8_lossy(&add_out.stderr);
            return Err(CoreError::Internal(format!("Shadow git add failed: {err}")));
        }

        // Write tree from shadow index
        let mut write_cmd = executor.create_command(repo_path);
        write_cmd.env("GIT_INDEX_FILE", &shadow_index);
        write_cmd.arg("write-tree");
        let write_out = executor.output(&mut write_cmd).await.map_err(|e| {
            let _ = std::fs::remove_file(&shadow_index);
            CoreError::Internal(format!("Failed to write tree from shadow index: {e}"))
        })?;
        let _ = std::fs::remove_file(&shadow_index);

        if !write_out.status.success() {
            let err = String::from_utf8_lossy(&write_out.stderr);
            return Err(CoreError::Internal(format!(
                "Shadow git write-tree failed: {err}"
            )));
        }
        let tree_sha = String::from_utf8_lossy(&write_out.stdout)
            .trim()
            .to_string();

        // Commit tree with parent HEAD
        let mut commit_cmd = executor.create_command(repo_path);
        let commit_msg = format!("Monitor Checkpoint: {description} ({timestamp_str})");
        if !head_sha.is_empty() {
            commit_cmd.args(["commit-tree", &tree_sha, "-p", &head_sha, "-m", &commit_msg]);
        } else {
            commit_cmd.args(["commit-tree", &tree_sha, "-m", &commit_msg]);
        }
        let commit_out = executor
            .output(&mut commit_cmd)
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to commit tree: {e}")))?;
        if !commit_out.status.success() {
            let err = String::from_utf8_lossy(&commit_out.stderr);
            return Err(CoreError::Internal(format!("commit-tree failed: {err}")));
        }
        let commit_sha = String::from_utf8_lossy(&commit_out.stdout)
            .trim()
            .to_string();

        // Store reference in dedicated monitor namespace
        let safe_branch = Self::sanitize_ref_component(&branch);
        let ref_name = format!("refs/monitor/checkpoints/{safe_branch}/{timestamp_str}_{seq}");
        let mut ref_cmd = executor.create_command(repo_path);
        ref_cmd.args(["update-ref", &ref_name, &commit_sha]);
        let _ = executor.output(&mut ref_cmd).await;

        // Diff stats vs HEAD
        let (files_changed, lines_added, lines_deleted) = if !head_sha.is_empty() {
            Self::get_diff_stats(executor, repo_path, &head_sha, &commit_sha).await
        } else {
            (0, 0, 0)
        };

        let checkpoint_id = format!(
            "chk_{}_{}_{}",
            now_ms,
            seq,
            &commit_sha[..7.min(commit_sha.len())]
        );

        Ok(CheckpointInfo {
            checkpoint_id,
            repo_path: repo_path.to_string_lossy().to_string(),
            branch,
            commit_sha,
            created_at_utc_ms: now_ms,
            files_changed_count: files_changed,
            lines_added,
            lines_deleted,
            is_safety,
            description: description.to_string(),
        })
    }

    /// Returns true if the working tree has modified or untracked files worth snapshotting.
    pub async fn has_working_changes(
        executor: &GitExecutor,
        repo_path: &Path,
    ) -> Result<bool, CoreError> {
        let mut cmd = executor.create_command(repo_path);
        cmd.args(["status", "--porcelain", "--untracked-files=normal"]);
        let out = executor
            .output(&mut cmd)
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to check git status: {e}")))?;
        if !out.status.success() {
            return Err(CoreError::Internal(
                String::from_utf8_lossy(&out.stderr).trim().to_string(),
            ));
        }
        Ok(!String::from_utf8_lossy(&out.stdout).trim().is_empty())
    }

    /// Computes a lightweight fingerprint of dirty/untracked files (path status + size + mtime)
    /// so automated checkpoints run for any AI/manual edit without creating duplicate snapshots when idle.
    pub async fn working_status_fingerprint(
        executor: &GitExecutor,
        repo_path: &Path,
    ) -> Result<Option<String>, CoreError> {
        let mut cmd = executor.create_command(repo_path);
        cmd.args(["status", "--porcelain", "--untracked-files=normal"]);
        let out = executor
            .output(&mut cmd)
            .await
            .map_err(|e| CoreError::Internal(format!("Failed to check git status: {e}")))?;
        if !out.status.success() {
            return Err(CoreError::Internal(
                String::from_utf8_lossy(&out.stderr).trim().to_string(),
            ));
        }
        let stdout = String::from_utf8_lossy(&out.stdout);
        let trimmed = stdout.trim();
        if trimmed.is_empty() {
            return Ok(None);
        }

        let mut fingerprint = String::with_capacity(trimmed.len() + 256);
        for line in trimmed.lines().take(120) {
            fingerprint.push_str(line);
            if let Some(raw_rel) = line.get(3..) {
                let rel = raw_rel
                    .rsplit(" -> ")
                    .next()
                    .unwrap_or(raw_rel)
                    .trim()
                    .trim_matches('"');
                if !rel.is_empty() {
                    let full = repo_path.join(rel);
                    if let Ok(meta) = std::fs::symlink_metadata(&full) {
                        let mtime = meta
                            .modified()
                            .ok()
                            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                            .map(|d| d.as_nanos())
                            .unwrap_or(0);
                        fingerprint.push('|');
                        fingerprint.push_str(&meta.len().to_string());
                        fingerprint.push(':');
                        fingerprint.push_str(&mtime.to_string());
                    }
                }
            }
            fingerprint.push('\n');
        }
        Ok(Some(fingerprint))
    }

    fn is_valid_git_oid(sha: &str) -> bool {
        (40..=64).contains(&sha.len()) && sha.bytes().all(|b| b.is_ascii_hexdigit())
    }

    fn sanitize_ref_component(branch: &str) -> String {
        let mut cleaned: String = branch
            .chars()
            .map(|c| {
                if c.is_ascii_alphanumeric() || matches!(c, '-' | '_') {
                    c
                } else {
                    '_'
                }
            })
            .collect();
        while cleaned.contains("__") {
            cleaned = cleaned.replace("__", "_");
        }
        let trimmed = cleaned.trim_matches(['_', '-', '.']);
        if trimmed.is_empty() {
            "detached".to_string()
        } else {
            trimmed.to_string()
        }
    }

    /// Safely restores the working directory to a checkpoint, auto-creating a safety checkpoint first.
    pub async fn rollback_to_checkpoint(
        executor: &GitExecutor,
        repo_path: &Path,
        commit_sha: &str,
    ) -> Result<CheckpointInfo, CoreError> {
        if !Self::is_valid_git_oid(commit_sha) {
            return Err(CoreError::Internal(
                "Invalid checkpoint commit identifier".to_string(),
            ));
        }

        // 1. Create safety snapshot of pre-rollback state
        let safety_checkpoint =
            Self::create_checkpoint(executor, repo_path, "Pre-rollback safety snapshot", true)
                .await?;

        // 2. Checkout files from checkpoint commit into working copy
        let mut restore_cmd = executor.create_command(repo_path);
        restore_cmd.args(["checkout", commit_sha, "--", "."]);
        let restore_out = executor.output(&mut restore_cmd).await.map_err(|e| {
            CoreError::Internal(format!("Failed to restore working directory: {e}"))
        })?;

        if !restore_out.status.success() {
            let err = String::from_utf8_lossy(&restore_out.stderr);
            return Err(CoreError::Internal(format!("Rollback failed: {err}")));
        }

        Ok(safety_checkpoint)
    }

    /// Evaluates if changes between two snapshots exceed mass-change thresholds (> 20 files deleted or > 1000 lines deleted).
    pub fn evaluate_mass_change(files_deleted: u32, lines_deleted: u32) -> Option<MassChangeAlert> {
        if files_deleted >= 20 || lines_deleted >= 1000 {
            Some(MassChangeAlert {
                files_deleted,
                lines_deleted,
                message: format!(
                    "Critical mass change detected: {} files and {} lines removed",
                    files_deleted, lines_deleted
                ),
            })
        } else {
            None
        }
    }

    async fn get_diff_stats(
        executor: &GitExecutor,
        repo_path: &Path,
        from_sha: &str,
        to_sha: &str,
    ) -> (u32, u32, u32) {
        let mut diff_cmd = executor.create_command(repo_path);
        diff_cmd.args(["diff", "--shortstat", from_sha, to_sha]);
        if let Ok(out) = executor.output(&mut diff_cmd).await
            && out.status.success()
        {
            let text = String::from_utf8_lossy(&out.stdout);
            Self::parse_shortstat(&text)
        } else {
            (0, 0, 0)
        }
    }

    fn parse_shortstat(text: &str) -> (u32, u32, u32) {
        let mut files = 0u32;
        let mut added = 0u32;
        let mut deleted = 0u32;

        for part in text.split(',') {
            let trimmed = part.trim();
            if trimmed.contains("file")
                && let Some(num) = trimmed
                    .split_whitespace()
                    .next()
                    .and_then(|s| s.parse().ok())
            {
                files = num;
            } else if trimmed.contains("insertion")
                && let Some(num) = trimmed
                    .split_whitespace()
                    .next()
                    .and_then(|s| s.parse().ok())
            {
                added = num;
            } else if trimmed.contains("deletion")
                && let Some(num) = trimmed
                    .split_whitespace()
                    .next()
                    .and_then(|s| s.parse().ok())
            {
                deleted = num;
            }
        }
        (files, added, deleted)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_hex_oids_and_sanitizes_branch_refs() {
        assert!(CheckpointEngine::is_valid_git_oid(
            "0123456789abcdef0123456789abcdef01234567"
        ));
        assert!(!CheckpointEngine::is_valid_git_oid("--flag"));
        assert!(!CheckpointEngine::is_valid_git_oid("short"));
        assert_eq!(
            CheckpointEngine::sanitize_ref_component("feature/auth..test"),
            "feature_auth_test"
        );
        assert_eq!(CheckpointEngine::sanitize_ref_component("---"), "detached");
    }
}
