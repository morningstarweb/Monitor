#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use monitor_storage::{Storage, StoredAgentSession, StoredAlert};
use tempfile::tempdir;

#[test]
fn test_watchdog_storage_lifecycle() {
    let dir = tempdir().expect("tempdir");
    let db_path = dir.path().join("test_watchdog.db");
    let storage = Storage::open(&db_path).expect("open db");

    // 1. Verify default seeded agent profiles exist
    let profiles = storage.list_agent_profiles().expect("list profiles");
    assert!(profiles.len() >= 5);
    assert!(profiles.iter().any(|p| p.name == "claude"));
    assert!(profiles.iter().any(|p| p.name == "codex"));
    assert!(profiles.iter().any(|p| p.name == "gemini"));

    // 2. Verify default seeded alert rules exist
    let rules = storage.list_alert_rules().expect("list rules");
    assert!(rules.len() >= 8);
    assert!(rules.iter().any(|r| r.kind == "agent.stalled"));
    assert!(rules.iter().any(|r| r.kind == "battery.low"));

    // 3. Insert and list alerts
    let alert = StoredAlert {
        alert_id: "alert_test_01".to_string(),
        rule_kind: "agent.stalled".to_string(),
        severity: "warning".to_string(),
        title: "Agent Stalled".to_string(),
        body: "Claude agent stalled on test_repo".to_string(),
        dedup_key: "agent.stalled:1234".to_string(),
        session_id: Some("sess_1234".to_string()),
        value: Some(600.0),
        created_at_utc_ms: 1000,
        delivered_at_utc_ms: None,
        acked_at_utc_ms: None,
        snoozed_until_utc_ms: None,
        recovered_at_utc_ms: None,
    };
    storage.insert_alert(&alert).expect("insert alert");

    let alerts = storage.list_alerts(10).expect("list alerts");
    assert_eq!(alerts.len(), 1);
    assert_eq!(alerts[0].alert_id, "alert_test_01");

    // 4. Acknowledge and snooze
    assert!(storage.ack_alert("alert_test_01", 2000).expect("ack"));
    assert!(storage.snooze_alert("alert_test_01", 5000).expect("snooze"));
    let updated_alerts = storage.list_alerts(10).expect("list alerts");
    assert_eq!(updated_alerts[0].acked_at_utc_ms, Some(2000));
    assert_eq!(updated_alerts[0].snoozed_until_utc_ms, Some(5000));

    // 5. Recovery
    storage
        .mark_alert_recovered("agent.stalled:1234", 6000)
        .expect("recover");
    let recovered_alerts = storage.list_alerts(10).expect("list alerts");
    assert_eq!(recovered_alerts[0].recovered_at_utc_ms, Some(6000));

    // 6. Agent Sessions
    let session = StoredAgentSession {
        session_id: "sess_1234".to_string(),
        profile_name: "claude".to_string(),
        pid: 9999,
        repo_path: Some("D:\\test_repo".to_string()),
        state: "Active".to_string(),
        started_at_utc_ms: 1000,
        last_activity_utc_ms: 1000,
        ended_at_utc_ms: None,
        exit_code: None,
    };
    storage.save_agent_session(&session).expect("save session");
    let active = storage
        .get_active_agent_sessions()
        .expect("active sessions");
    assert_eq!(active.len(), 1);
    assert_eq!(active[0].session_id, "sess_1234");
}
