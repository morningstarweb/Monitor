use super::{StoredAlert, StoredAlertRule};
use crate::Storage;
use rusqlite::{Result, params};

impl Storage {
    pub fn list_alerts(&self, limit: u32) -> Result<Vec<StoredAlert>> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut stmt = conn.prepare(
            "SELECT alert_id, rule_kind, severity, title, body, dedup_key, session_id, value, \
             created_at_utc_ms, delivered_at_utc_ms, acked_at_utc_ms, snoozed_until_utc_ms, recovered_at_utc_ms \
             FROM alerts ORDER BY created_at_utc_ms DESC LIMIT ?1",
        )?;
        let rows = stmt.query_map(params![limit], |row| {
            Ok(StoredAlert {
                alert_id: row.get(0)?,
                rule_kind: row.get(1)?,
                severity: row.get(2)?,
                title: row.get(3)?,
                body: row.get(4)?,
                dedup_key: row.get(5)?,
                session_id: row.get(6)?,
                value: row.get(7)?,
                created_at_utc_ms: row.get(8)?,
                delivered_at_utc_ms: row.get(9)?,
                acked_at_utc_ms: row.get(10)?,
                snoozed_until_utc_ms: row.get(11)?,
                recovered_at_utc_ms: row.get(12)?,
            })
        })?;
        rows.collect()
    }

    pub fn clear_alerts(&self) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute("DELETE FROM alerts", [])?;
        Ok(())
    }

    pub fn insert_alert(&self, alert: &StoredAlert) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "INSERT INTO alerts (alert_id, rule_kind, severity, title, body, dedup_key, session_id, \
             value, created_at_utc_ms, delivered_at_utc_ms, acked_at_utc_ms, snoozed_until_utc_ms, recovered_at_utc_ms) \
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
            params![
                alert.alert_id,
                alert.rule_kind,
                alert.severity,
                alert.title,
                alert.body,
                alert.dedup_key,
                alert.session_id,
                alert.value,
                alert.created_at_utc_ms,
                alert.delivered_at_utc_ms,
                alert.acked_at_utc_ms,
                alert.snoozed_until_utc_ms,
                alert.recovered_at_utc_ms,
            ],
        )?;
        Ok(())
    }

    pub fn ack_alert(&self, alert_id: &str, acked_at: i64) -> Result<bool> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let updated = conn.execute(
            "UPDATE alerts SET acked_at_utc_ms = ?1 WHERE alert_id = ?2",
            params![acked_at, alert_id],
        )?;
        Ok(updated > 0)
    }

    pub fn snooze_alert(&self, alert_id: &str, snoozed_until: i64) -> Result<bool> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let updated = conn.execute(
            "UPDATE alerts SET snoozed_until_utc_ms = ?1 WHERE alert_id = ?2",
            params![snoozed_until, alert_id],
        )?;
        Ok(updated > 0)
    }

    pub fn mark_alert_recovered(&self, dedup_key: &str, recovered_at: i64) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "UPDATE alerts SET recovered_at_utc_ms = ?1 WHERE dedup_key = ?2 AND recovered_at_utc_ms IS NULL",
            params![recovered_at, dedup_key],
        )?;
        Ok(())
    }

    pub fn list_alert_rules(&self) -> Result<Vec<StoredAlertRule>> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut stmt = conn.prepare(
            "SELECT kind, enabled, severity, threshold, for_duration_s, cooldown_s FROM alert_rules ORDER BY kind ASC",
        )?;
        let rows = stmt.query_map([], |row| {
            let enabled_int: i64 = row.get(1)?;
            Ok(StoredAlertRule {
                kind: row.get(0)?,
                enabled: enabled_int != 0,
                severity: row.get(2)?,
                threshold: row.get(3)?,
                for_duration_s: row.get::<_, i64>(4)? as u32,
                cooldown_s: row.get::<_, i64>(5)? as u32,
            })
        })?;
        rows.collect()
    }

    pub fn update_alert_rule(&self, rule: &StoredAlertRule) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "INSERT OR REPLACE INTO alert_rules (kind, enabled, severity, threshold, for_duration_s, cooldown_s) \
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                rule.kind,
                if rule.enabled { 1 } else { 0 },
                rule.severity,
                rule.threshold,
                rule.for_duration_s,
                rule.cooldown_s,
            ],
        )?;
        Ok(())
    }
}
