use rusqlite::{Connection, Result};
use tracing::info;

pub const MIGRATIONS: &[(&str, &str)] = &[
    (
        "001_initial_schema",
        r#"
        CREATE TABLE IF NOT EXISTS schema_migrations (
            id TEXT PRIMARY KEY,
            applied_at_utc_ms INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS repositories (
            repo_id TEXT PRIMARY KEY,
            canonical_path TEXT NOT NULL UNIQUE,
            display_name TEXT NOT NULL,
            trust_profile TEXT NOT NULL DEFAULT 'Restricted',
            registered_at_utc_ms INTEGER NOT NULL,
            watch_enabled INTEGER NOT NULL DEFAULT 1,
            revision INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS devices (
            device_id TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            role TEXT NOT NULL DEFAULT 'Viewer',
            created_at_utc_ms INTEGER NOT NULL,
            last_seen_utc_ms INTEGER,
            revoked_at_utc_ms INTEGER
        );

        CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at_utc_ms INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS operation_log (
            operation_id TEXT PRIMARY KEY,
            repo_id TEXT,
            operation_type TEXT NOT NULL,
            state TEXT NOT NULL,
            started_at_utc_ms INTEGER NOT NULL,
            finished_at_utc_ms INTEGER,
            result_code TEXT,
            audit_metadata TEXT
        );

        CREATE TABLE IF NOT EXISTS activity_events (
            event_id TEXT PRIMARY KEY,
            event_type TEXT NOT NULL,
            repo_id TEXT,
            occurred_at_utc_ms INTEGER NOT NULL,
            sequence_num INTEGER NOT NULL,
            payload TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS alert_history (
            alert_id TEXT PRIMARY KEY,
            rule_name TEXT NOT NULL,
            threshold_value REAL,
            actual_value REAL,
            triggered_at_utc_ms INTEGER NOT NULL,
            delivered INTEGER NOT NULL DEFAULT 0,
            notification_channel TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_activity_events_seq ON activity_events(sequence_num);
        CREATE INDEX IF NOT EXISTS idx_operation_log_repo ON operation_log(repo_id, started_at_utc_ms);
        "#,
    ),
    (
        "002_watchdog_pipeline",
        r#"
        CREATE TABLE IF NOT EXISTS alert_rules (
            kind TEXT PRIMARY KEY,
            enabled INTEGER NOT NULL DEFAULT 1,
            severity TEXT NOT NULL,
            threshold REAL NOT NULL,
            for_duration_s INTEGER NOT NULL,
            cooldown_s INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS agent_profiles (
            name TEXT PRIMARY KEY,
            matchers TEXT NOT NULL,
            stall_threshold_s INTEGER NOT NULL DEFAULT 600,
            restart_command TEXT,
            keep_running INTEGER NOT NULL DEFAULT 0,
            max_retries INTEGER NOT NULL DEFAULT 3
        );

        CREATE TABLE IF NOT EXISTS agent_sessions (
            session_id TEXT PRIMARY KEY,
            profile_name TEXT NOT NULL,
            pid INTEGER NOT NULL,
            repo_path TEXT,
            state TEXT NOT NULL,
            started_at_utc_ms INTEGER NOT NULL,
            last_activity_utc_ms INTEGER NOT NULL,
            ended_at_utc_ms INTEGER,
            exit_code INTEGER
        );

        CREATE TABLE IF NOT EXISTS alerts (
            alert_id TEXT PRIMARY KEY,
            rule_kind TEXT NOT NULL,
            severity TEXT NOT NULL,
            title TEXT NOT NULL,
            body TEXT NOT NULL,
            dedup_key TEXT NOT NULL,
            session_id TEXT,
            value REAL,
            created_at_utc_ms INTEGER NOT NULL,
            delivered_at_utc_ms INTEGER,
            acked_at_utc_ms INTEGER,
            snoozed_until_utc_ms INTEGER,
            recovered_at_utc_ms INTEGER
        );

        CREATE INDEX IF NOT EXISTS idx_alerts_created ON alerts(created_at_utc_ms DESC);
        CREATE INDEX IF NOT EXISTS idx_alerts_dedup ON alerts(dedup_key, recovered_at_utc_ms);
        CREATE INDEX IF NOT EXISTS idx_agent_sessions_state ON agent_sessions(state);

        -- Seed Default Built-in Agent Profiles
        INSERT OR IGNORE INTO agent_profiles (name, matchers, stall_threshold_s) VALUES
            ('claude', '["claude", "claude-code"]', 600),
            ('codex', '["codex", "codex-cli"]', 600),
            ('gemini', '["gemini", "gemini-cli", "antigravity", "antigravity ide", "agy"]', 600),
            ('aider', '["aider", "aider.chat"]', 600),
            ('opencode', '["opencode", "open-code"]', 600);

        -- Seed Default Built-in Alert Rules
        INSERT OR IGNORE INTO alert_rules (kind, enabled, severity, threshold, for_duration_s, cooldown_s) VALUES
            ('agent.stalled', 1, 'warning', 600, 600, 1800),
            ('agent.ended', 1, 'info', 0, 0, 0),
            ('battery.low', 1, 'warning', 20.0, 60, 900),
            ('battery.unplugged', 1, 'warning', 0, 10, 900),
            ('disk.low', 1, 'warning', 10.0, 60, 3600),
            ('memory.high', 1, 'warning', 90.0, 300, 1800),
            ('cpu.pinned', 1, 'warning', 95.0, 900, 1800),
            ('network.lost', 1, 'warning', 0, 120, 900);
        "#,
    ),
    (
        "003_phase2_checkpoints",
        r#"
        CREATE TABLE IF NOT EXISTS checkpoints (
            checkpoint_id TEXT PRIMARY KEY,
            repo_path TEXT NOT NULL,
            branch TEXT NOT NULL,
            commit_sha TEXT NOT NULL,
            created_at_utc_ms INTEGER NOT NULL,
            files_changed_count INTEGER NOT NULL,
            lines_added INTEGER NOT NULL,
            lines_deleted INTEGER NOT NULL,
            is_safety INTEGER NOT NULL DEFAULT 0,
            description TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS watchdog_policies (
            policy_id TEXT PRIMARY KEY,
            rule_kind TEXT NOT NULL,
            action TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 0,
            dry_run INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS audit_log (
            id TEXT PRIMARY KEY,
            trigger TEXT NOT NULL,
            action TEXT NOT NULL,
            target TEXT NOT NULL,
            result TEXT NOT NULL,
            created_at_utc_ms INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_checkpoints_repo ON checkpoints(repo_path, created_at_utc_ms DESC);
        CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at_utc_ms DESC);

        INSERT OR IGNORE INTO alert_rules (kind, enabled, severity, threshold, for_duration_s, cooldown_s) VALUES
            ('repo.mass_change', 1, 'critical', 20.0, 0, 1800);
        "#,
    ),
    (
        "004_v3_2_command_ledger",
        r#"
        CREATE TABLE IF NOT EXISTS command_ledger (
            command_id TEXT PRIMARY KEY,
            viewer_device_id TEXT NOT NULL,
            operation TEXT NOT NULL,
            canonical_params_hash TEXT NOT NULL,
            session_id TEXT NOT NULL,
            accepted_epoch INTEGER NOT NULL,
            tier TEXT NOT NULL,
            state TEXT NOT NULL,
            received_at_utc_ms INTEGER NOT NULL,
            started_at_utc_ms INTEGER,
            finished_at_utc_ms INTEGER,
            deadline_ms INTEGER NOT NULL,
            result_code TEXT,
            result_digest TEXT,
            result_summary TEXT
        );

        CREATE TABLE IF NOT EXISTS viewer_trust (
            device_id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            public_key TEXT NOT NULL,
            trust_tier TEXT NOT NULL DEFAULT 'T0',
            enrolled_at_utc_ms INTEGER NOT NULL,
            approved_at_utc_ms INTEGER,
            revoked_at_utc_ms INTEGER
        );

        CREATE INDEX IF NOT EXISTS idx_command_ledger_lookup ON command_ledger(command_id, viewer_device_id);
        CREATE INDEX IF NOT EXISTS idx_viewer_trust_user ON viewer_trust(user_id, trust_tier);
        "#,
    ),
    (
        "002_fail_closed_permission_defaults",
        // AUD-04: materialize explicit, fail-closed defaults for every security
        // permission. INSERT OR IGNORE preserves any choice the user already made.
        // Powerful operations require an explicit opt-in from the desktop tray.
        r#"
        INSERT OR IGNORE INTO settings (key, value, updated_at_utc_ms) VALUES
            ('allow_local_network', 'false', 0),
            ('allow_git_operations', 'false', 0),
            ('allow_cloud_operations', 'false', 0),
            ('allow_checkpoint_rollback', 'false', 0),
            ('allow_delete_operations', 'false', 0),
            ('allow_remote_terminal', 'false', 0);
        "#,
    ),
];

pub fn run_migrations(conn: &mut Connection) -> Result<()> {
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS schema_migrations (
            id TEXT PRIMARY KEY,
            applied_at_utc_ms INTEGER NOT NULL
        );",
    )?;

    for (name, sql) in MIGRATIONS {
        let count: i64 = conn.query_row(
            "SELECT COUNT(*) FROM schema_migrations WHERE id = ?1",
            [name],
            |r| r.get(0),
        )?;

        if count == 0 {
            info!("Applying database migration: {}", name);
            conn.execute_batch(sql)?;
            let now = chrono::Utc::now().timestamp_millis();
            conn.execute(
                "INSERT INTO schema_migrations (id, applied_at_utc_ms) VALUES (?1, ?2)",
                (name, now),
            )?;
        }
    }

    Ok(())
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
mod tests {
    use super::*;

    const PERMISSION_KEYS: &[&str] = &[
        "allow_local_network",
        "allow_git_operations",
        "allow_cloud_operations",
        "allow_checkpoint_rollback",
        "allow_delete_operations",
        "allow_remote_terminal",
    ];

    #[test]
    fn fresh_install_has_fail_closed_permission_defaults() {
        let mut conn = Connection::open_in_memory().expect("in-memory db");
        run_migrations(&mut conn).expect("migrations");

        for key in PERMISSION_KEYS {
            let value: String = conn
                .query_row("SELECT value FROM settings WHERE key = ?1", [key], |row| {
                    row.get(0)
                })
                .expect("permission row must exist");
            assert_eq!(value, "false", "permission {key} must fail closed");
        }
    }

    /// Applies every migration strictly before `stop_before`, reproducing a
    /// database created by an older application version.
    fn run_migrations_before(conn: &mut Connection, stop_before: &str) {
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS schema_migrations (
                id TEXT PRIMARY KEY,
                applied_at_utc_ms INTEGER NOT NULL
            );",
        )
        .expect("migration tracking table");
        for (name, sql) in MIGRATIONS {
            if *name == stop_before {
                break;
            }
            let count: i64 = conn
                .query_row(
                    "SELECT COUNT(*) FROM schema_migrations WHERE id = ?1",
                    [name],
                    |row| row.get(0),
                )
                .expect("migration lookup");
            if count == 0 {
                conn.execute_batch(sql).expect("apply historical migration");
                conn.execute(
                    "INSERT INTO schema_migrations (id, applied_at_utc_ms) VALUES (?1, ?2)",
                    (name, chrono::Utc::now().timestamp_millis()),
                )
                .expect("record historical migration");
            }
        }
    }

    #[test]
    fn migration_preserves_explicit_user_choices_from_prior_schema() {
        let mut conn = Connection::open_in_memory().expect("in-memory db");
        // Recreate the state of a database that predates the fail-closed
        // migration, with an explicit user grant already stored.
        run_migrations_before(&mut conn, "002_fail_closed_permission_defaults");
        conn.execute(
            "INSERT OR REPLACE INTO settings (key, value, updated_at_utc_ms) \
             VALUES ('allow_git_operations', 'true', 1)",
            [],
        )
        .expect("user grants git access before upgrade");
        // A stale, undocumented alias must not be treated as an authorization.
        conn.execute(
            "INSERT OR REPLACE INTO settings (key, value, updated_at_utc_ms) \
             VALUES ('allow_cloud_access', 'true', 1)",
            [],
        )
        .expect("stale legacy alias");

        run_migrations(&mut conn).expect("upgrade migration");

        let git_value: String = conn
            .query_row(
                "SELECT value FROM settings WHERE key = 'allow_git_operations'",
                [],
                |row| row.get(0),
            )
            .expect("setting exists");
        assert_eq!(git_value, "true", "explicit prior grant must survive");

        let cloud_value: String = conn
            .query_row(
                "SELECT value FROM settings WHERE key = 'allow_cloud_operations'",
                [],
                |row| row.get(0),
            )
            .expect("setting exists");
        assert_eq!(cloud_value, "false", "missing new key must fail closed");
    }
}
