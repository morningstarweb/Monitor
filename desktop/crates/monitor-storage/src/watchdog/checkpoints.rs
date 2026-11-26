use super::StoredCheckpoint;
use crate::Storage;
use rusqlite::{Result, params};

impl Storage {
    pub fn insert_checkpoint(&self, checkpoint: &StoredCheckpoint) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "INSERT INTO checkpoints (checkpoint_id, repo_path, branch, commit_sha, created_at_utc_ms, \
             files_changed_count, lines_added, lines_deleted, is_safety, description) \
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
            params![
                checkpoint.checkpoint_id,
                checkpoint.repo_path,
                checkpoint.branch,
                checkpoint.commit_sha,
                checkpoint.created_at_utc_ms,
                checkpoint.files_changed_count,
                checkpoint.lines_added,
                checkpoint.lines_deleted,
                checkpoint.is_safety as i64,
                checkpoint.description,
            ],
        )?;
        Ok(())
    }

    pub fn list_checkpoints(&self, repo_path: &str, limit: u32) -> Result<Vec<StoredCheckpoint>> {
        fn normalize_key(raw: &str) -> String {
            let trimmed = raw.trim();
            let no_verbatim = trimmed
                .strip_prefix(r"\\?\")
                .or_else(|| trimmed.strip_prefix("//?/"))
                .unwrap_or(trimmed);
            let normalized_slashes = no_verbatim.replace('/', "\\");
            let trimmed_slash = normalized_slashes.trim_end_matches('\\');
            trimmed_slash.to_lowercase()
        }

        let target_key = normalize_key(repo_path);
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut stmt = conn.prepare(
            "SELECT checkpoint_id, repo_path, branch, commit_sha, created_at_utc_ms, \
             files_changed_count, lines_added, lines_deleted, is_safety, description \
             FROM checkpoints ORDER BY created_at_utc_ms DESC",
        )?;
        let rows = stmt.query_map([], |row| {
            let is_safety_raw: i64 = row.get(8)?;
            Ok(StoredCheckpoint {
                checkpoint_id: row.get(0)?,
                repo_path: row.get(1)?,
                branch: row.get(2)?,
                commit_sha: row.get(3)?,
                created_at_utc_ms: row.get(4)?,
                files_changed_count: row.get::<_, i64>(5)? as u32,
                lines_added: row.get::<_, i64>(6)? as u32,
                lines_deleted: row.get::<_, i64>(7)? as u32,
                is_safety: is_safety_raw != 0,
                description: row.get(9)?,
            })
        })?;
        let mut matched = Vec::new();
        for row in rows {
            let item = row?;
            if target_key.is_empty() || normalize_key(&item.repo_path) == target_key {
                matched.push(item);
                if matched.len() >= limit as usize {
                    break;
                }
            }
        }
        Ok(matched)
    }

    pub fn get_checkpoint(&self, checkpoint_id: &str) -> Result<Option<StoredCheckpoint>> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut stmt = conn.prepare(
            "SELECT checkpoint_id, repo_path, branch, commit_sha, created_at_utc_ms, \
             files_changed_count, lines_added, lines_deleted, is_safety, description \
             FROM checkpoints WHERE checkpoint_id = ?1",
        )?;
        let mut rows = stmt.query_map(params![checkpoint_id], |row| {
            let is_safety_raw: i64 = row.get(8)?;
            Ok(StoredCheckpoint {
                checkpoint_id: row.get(0)?,
                repo_path: row.get(1)?,
                branch: row.get(2)?,
                commit_sha: row.get(3)?,
                created_at_utc_ms: row.get(4)?,
                files_changed_count: row.get::<_, i64>(5)? as u32,
                lines_added: row.get::<_, i64>(6)? as u32,
                lines_deleted: row.get::<_, i64>(7)? as u32,
                is_safety: is_safety_raw != 0,
                description: row.get(9)?,
            })
        })?;
        if let Some(r) = rows.next() {
            Ok(Some(r?))
        } else {
            Ok(None)
        }
    }
}
