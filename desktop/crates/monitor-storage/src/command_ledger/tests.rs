#![allow(clippy::panic, clippy::unwrap_used)]

use super::*;
use crate::migrations::run_migrations;
use rusqlite::Connection;

fn setup_test_db() -> Connection {
    let mut conn = Connection::open_in_memory().unwrap_or_else(|e| panic!("in memory db: {e}"));
    let _ = run_migrations(&mut conn);
    conn
}

#[test]
fn test_durable_command_ledger_lifecycle() {
    let conn = setup_test_db();
    let cmd = CommandLedgerEntry {
        command_id: "cmd-uuid-1".to_string(),
        viewer_device_id: "phone-1".to_string(),
        operation: "git.stage".to_string(),
        canonical_params_hash: "hash_abc_123".to_string(),
        session_id: "session-1".to_string(),
        accepted_epoch: 1,
        tier: "T2".to_string(),
        state: CommandLedgerState::New,
        received_at_utc_ms: 1000,
        started_at_utc_ms: Some(1000),
        finished_at_utc_ms: None,
        deadline_ms: 5000,
        result_code: None,
        result_digest: None,
        result_summary: None,
    };

    // First admission: Admitted
    let admission = record_incoming_command(&conn, &cmd);
    assert_eq!(
        admission.unwrap_or(LedgerAdmission::Conflict),
        LedgerAdmission::Admitted
    );

    // Finalize
    let _ = finalize_command(
        &conn,
        "cmd-uuid-1",
        CommandLedgerState::Completed,
        Some("SUCCESS"),
        Some("digest_xyz"),
        Some("Staged 1 file"),
    );

    // Duplicate admission with identical hash: Duplicate with Completed state
    let dup = record_incoming_command(&conn, &cmd).unwrap_or(LedgerAdmission::Conflict);
    match dup {
        LedgerAdmission::Duplicate(prev) => {
            assert_eq!(prev.state, CommandLedgerState::Completed);
            assert_eq!(prev.result_code.as_deref(), Some("SUCCESS"));
        }
        _ => panic!("Expected duplicate"),
    }

    // Reused command_id with different hash: Conflict
    let mut conflicting = cmd.clone();
    conflicting.canonical_params_hash = "different_hash".to_string();
    let conflict =
        record_incoming_command(&conn, &conflicting).unwrap_or(LedgerAdmission::Admitted);
    assert_eq!(conflict, LedgerAdmission::Conflict);
}

#[test]
fn idempotent_recording_is_keyed_by_viewer_and_request_id() {
    let mut conn = setup_test_db();
    let entry = CommandLedgerEntry {
        command_id: "ignored-client-id".to_string(),
        viewer_device_id: "viewer-a".to_string(),
        operation: "process.kill".to_string(),
        canonical_params_hash: "hash-kill-1".to_string(),
        session_id: "session-1".to_string(),
        accepted_epoch: 1,
        tier: "T3".to_string(),
        state: CommandLedgerState::Executing,
        received_at_utc_ms: 1000,
        started_at_utc_ms: Some(1000),
        finished_at_utc_ms: None,
        deadline_ms: 5000,
        result_code: None,
        result_digest: None,
        result_summary: None,
    };

    assert_eq!(
        record_idempotent_command(&mut conn, "viewer-a", "req-1", &entry)
            .unwrap_or(LedgerAdmission::Conflict),
        LedgerAdmission::Admitted
    );
    let key = idempotency_key("viewer-a", "req-1");
    finalize_command(
        &conn,
        &key,
        CommandLedgerState::Completed,
        Some("SUCCESS"),
        None,
        Some("killed pid 42"),
    )
    .unwrap();

    // Same (viewer, request) replays the prior result and never re-executes.
    let replay = record_idempotent_command(&mut conn, "viewer-a", "req-1", &entry)
        .unwrap_or(LedgerAdmission::Conflict);
    match replay {
        LedgerAdmission::Duplicate(prev) => {
            assert_eq!(prev.state, CommandLedgerState::Completed);
            assert_eq!(prev.result_summary.as_deref(), Some("killed pid 42"));
        }
        other => panic!("expected duplicate, got {other:?}"),
    }

    // A different viewer owns an independent idempotency scope.
    assert_eq!(
        record_idempotent_command(&mut conn, "viewer-b", "req-1", &entry)
            .unwrap_or(LedgerAdmission::Conflict),
        LedgerAdmission::Admitted
    );

    // Reusing the key with different parameters is a conflict, not a replay.
    let mut conflicting = entry.clone();
    conflicting.canonical_params_hash = "different".to_string();
    assert_eq!(
        record_idempotent_command(&mut conn, "viewer-a", "req-1", &conflicting)
            .unwrap_or(LedgerAdmission::Admitted),
        LedgerAdmission::Conflict
    );
}

#[test]
fn test_crash_recovery_reconciliation() {
    let conn = setup_test_db();
    let cmd = CommandLedgerEntry {
        command_id: "crash-cmd-1".to_string(),
        viewer_device_id: "phone-1".to_string(),
        operation: "git.hard_reset".to_string(),
        canonical_params_hash: "hash_reset_999".to_string(),
        session_id: "session-1".to_string(),
        accepted_epoch: 1,
        tier: "T3".to_string(),
        state: CommandLedgerState::New,
        received_at_utc_ms: 2000,
        started_at_utc_ms: Some(2000),
        finished_at_utc_ms: None,
        deadline_ms: 5000,
        result_code: None,
        result_digest: None,
        result_summary: None,
    };

    let _ = record_incoming_command(&conn, &cmd);

    // Simulate crash recovery
    let reconciled = reconcile_crash_states(&conn).unwrap_or(0);
    assert_eq!(reconciled, 1);

    let retrieved = get_command(&conn, "crash-cmd-1").unwrap_or(None);
    assert_eq!(
        retrieved.map(|c| c.state),
        Some(CommandLedgerState::UnknownAfterCrash)
    );
}
