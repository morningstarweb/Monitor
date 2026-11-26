use super::helpers::create_test_state_and_token;
use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use monitor_core::{AlertItem, AlertRuleConfig, SnoozeAlertRequest};
use monitor_storage::{StoredAgentSession, StoredAlert};
use monitor_transport::build_router;
use tower::ServiceExt;

#[tokio::test]
async fn test_watchdog_alerts_and_rules_api_lifecycle() {
    let (state, token) = create_test_state_and_token().await;
    let app = build_router(state.clone());

    // 1. Seed an alert
    let alert = StoredAlert {
        alert_id: "alert_01".to_string(),
        rule_kind: "agent.stalled".to_string(),
        severity: "warning".to_string(),
        title: "Claude Stalled".to_string(),
        body: "No activity for 10m on monitor".to_string(),
        dedup_key: "agent.stalled:sess_01".to_string(),
        session_id: Some("sess_01".to_string()),
        value: Some(600.0),
        created_at_utc_ms: 1000,
        delivered_at_utc_ms: None,
        acked_at_utc_ms: None,
        snoozed_until_utc_ms: None,
        recovered_at_utc_ms: None,
    };
    state.storage.insert_alert(&alert).unwrap();

    // 2. GET /api/v1/alerts
    let req = Request::builder()
        .uri("/api/v1/alerts?limit=10")
        .header("Authorization", format!("Bearer {token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let body = res.into_body().collect().await.unwrap().to_bytes();
    let alerts: Vec<AlertItem> = serde_json::from_slice(&body).unwrap();
    assert_eq!(alerts.len(), 1);
    assert_eq!(alerts[0].alert_id, "alert_01");

    // 3. POST /api/v1/alerts/{id}/ack
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/alerts/alert_01/ack")
        .header("Authorization", format!("Bearer {token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);

    // 4. POST /api/v1/alerts/{id}/snooze
    let snooze_req = SnoozeAlertRequest { duration_s: 3600 };
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/alerts/alert_01/snooze")
        .header("Authorization", format!("Bearer {token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(serde_json::to_vec(&snooze_req).unwrap()))
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);

    // 5. GET /api/v1/watchdog/rules
    let req = Request::builder()
        .uri("/api/v1/watchdog/rules")
        .header("Authorization", format!("Bearer {token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let body = res.into_body().collect().await.unwrap().to_bytes();
    let rules: Vec<AlertRuleConfig> = serde_json::from_slice(&body).unwrap();
    assert!(rules.len() >= 8);

    // 6. Seed and GET /api/v1/watchdog/sessions
    let session = StoredAgentSession {
        session_id: "sess_01".to_string(),
        profile_name: "claude".to_string(),
        pid: 1234,
        repo_path: Some("D:\\Monitor".to_string()),
        state: "Active".to_string(),
        started_at_utc_ms: 1000,
        last_activity_utc_ms: 1000,
        ended_at_utc_ms: None,
        exit_code: None,
    };
    state.storage.save_agent_session(&session).unwrap();

    let req = Request::builder()
        .uri("/api/v1/watchdog/sessions")
        .header("Authorization", format!("Bearer {token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
}
