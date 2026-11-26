use super::{StoredAgentProfile, StoredAgentSession};
use crate::Storage;
use rusqlite::{Result, params};

impl Storage {
    pub fn list_agent_profiles(&self) -> Result<Vec<StoredAgentProfile>> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut stmt = conn.prepare(
            "SELECT name, matchers, stall_threshold_s, restart_command, keep_running, max_retries \
             FROM agent_profiles ORDER BY name ASC",
        )?;
        let rows = stmt.query_map([], |row| {
            let matchers_raw: String = row.get(1)?;
            let matchers: Vec<String> = serde_json::from_str(&matchers_raw).unwrap_or_default();
            let keep_running_int: i64 = row.get(4)?;
            Ok(StoredAgentProfile {
                name: row.get(0)?,
                matchers,
                stall_threshold_s: row.get::<_, i64>(2)? as u32,
                restart_command: row.get(3)?,
                keep_running: keep_running_int != 0,
                max_retries: row.get::<_, i64>(5)? as u32,
            })
        })?;
        rows.collect()
    }

    pub fn save_agent_session(&self, session: &StoredAgentSession) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "INSERT OR REPLACE INTO agent_sessions (session_id, profile_name, pid, repo_path, state, \
             started_at_utc_ms, last_activity_utc_ms, ended_at_utc_ms, exit_code) \
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            params![
                session.session_id,
                session.profile_name,
                session.pid,
                session.repo_path,
                session.state,
                session.started_at_utc_ms,
                session.last_activity_utc_ms,
                session.ended_at_utc_ms,
                session.exit_code,
            ],
        )?;
        Ok(())
    }

    pub fn end_agent_session(
        &self,
        session_id: &str,
        exit_code: Option<i32>,
        ended_at: i64,
    ) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "UPDATE agent_sessions SET state = 'Ended', exit_code = ?1, ended_at_utc_ms = ?2 WHERE session_id = ?3",
            params![exit_code, ended_at, session_id],
        )?;
        Ok(())
    }

    pub fn end_all_agent_sessions_for_repo(
        &self,
        repo_path: &str,
        exit_code: Option<i32>,
        ended_at: i64,
    ) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "UPDATE agent_sessions SET state = 'Ended', exit_code = ?1, ended_at_utc_ms = ?2 WHERE repo_path = ?3 AND state != 'Ended'",
            params![exit_code, ended_at, repo_path],
        )?;
        Ok(())
    }

    pub fn end_all_active_sessions(&self, exit_code: Option<i32>, ended_at: i64) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "UPDATE agent_sessions SET state = 'Ended', exit_code = ?1, ended_at_utc_ms = ?2 WHERE state != 'Ended'",
            params![exit_code, ended_at],
        )?;
        Ok(())
    }

    pub fn get_active_agent_sessions(&self) -> Result<Vec<StoredAgentSession>> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut stmt = conn.prepare(
            "SELECT session_id, profile_name, pid, repo_path, state, started_at_utc_ms, \
             last_activity_utc_ms, ended_at_utc_ms, exit_code FROM agent_sessions \
             WHERE state != 'Ended' ORDER BY started_at_utc_ms DESC",
        )?;
        let rows = stmt.query_map([], |row| {
            Ok(StoredAgentSession {
                session_id: row.get(0)?,
                profile_name: row.get(1)?,
                pid: row.get(2)?,
                repo_path: row.get(3)?,
                state: row.get(4)?,
                started_at_utc_ms: row.get(5)?,
                last_activity_utc_ms: row.get(6)?,
                ended_at_utc_ms: row.get(7)?,
                exit_code: row.get(8)?,
            })
        })?;
        rows.collect()
    }

    pub fn insert_audit_entry(
        &self,
        trigger: &str,
        action: &str,
        target: &str,
        result: &str,
    ) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let id = format!("audit_{}_{}", chrono::Utc::now().timestamp_millis(), target);
        conn.execute(
            "INSERT INTO audit_log (id, trigger, action, target, result, created_at_utc_ms) \
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                id,
                trigger,
                action,
                target,
                result,
                chrono::Utc::now().timestamp_millis()
            ],
        )?;
        Ok(())
    }
}
