use super::helpers::create_test_state_and_token;
use axum::body::Body;
use axum::extract::ConnectInfo;
use axum::http::{Request, StatusCode};
use monitor_core::{DeviceId, DeviceRole};
use monitor_storage::StoredAlert;
use monitor_transport::build_router;
use std::net::SocketAddr;
use std::time::Duration;
use tower::ServiceExt;

#[tokio::test]
async fn revoking_device_notifies_live_session_owners() {
    let (state, _operator_token) = create_test_state_and_token().await;
    let dev_id = DeviceId::new("dev_revoke_notify");
    let token = "tok_revoke_notify_secret_12345678";
    state
        .storage
        .insert_device(
            &dev_id,
            "Revoke Notify",
            &monitor_transport::auth::TokenAuthenticator::hash_token(token),
            DeviceRole::Operator,
        )
        .unwrap();

    let mut notifications = state.revocation_tx.subscribe();
    let app = monitor_transport::build_local_router(state.clone());
    let req = Request::builder()
        .method("DELETE")
        .uri(format!("/api/v1/local/devices/{}", dev_id.as_str()))
        .extension(ConnectInfo(
            "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
        ))
        .body(Body::empty())
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::NO_CONTENT);

    assert!(state.is_revoked_locally(dev_id.as_str()));
    let notified = tokio::time::timeout(Duration::from_secs(1), notifications.recv())
        .await
        .expect("revocation notification must arrive")
        .expect("broadcast must carry a value");
    assert_eq!(notified, dev_id.as_str());
}

#[tokio::test]
async fn can_revoke_active_lan_device_immediately() {
    let (state, _op) = create_test_state_and_token().await;
    let dev_id = DeviceId::new("dev_active_lan_phone");
    let token = "tok_active_lan_secret_12345678";
    state
        .storage
        .insert_device(
            &dev_id,
            "Local Phone",
            &monitor_transport::auth::TokenAuthenticator::hash_token(token),
            DeviceRole::Operator,
        )
        .unwrap();

    state.mark_lan_device_connected(dev_id.as_str());
    let mut rx = state.revocation_tx.subscribe();

    let app = monitor_transport::build_local_router(state.clone());
    let req = Request::builder()
        .method("DELETE")
        .uri(format!("/api/v1/local/devices/{}", dev_id.as_str()))
        .extension(ConnectInfo(
            "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
        ))
        .body(Body::empty())
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::NO_CONTENT);
    assert!(state.is_revoked_locally(dev_id.as_str()));
    assert_eq!(rx.try_recv().unwrap(), dev_id.as_str());
}

#[tokio::test]
async fn approve_device_route_unrevokes_and_promotes_to_operator() {
    let (state, _op) = create_test_state_and_token().await;
    let dev_id = DeviceId::new("dev_to_approve");
    let token = "tok_approve_test_secret_12345678";
    state
        .storage
        .insert_device(
            &dev_id,
            "Remote Phone",
            &monitor_transport::auth::TokenAuthenticator::hash_token(token),
            DeviceRole::Viewer,
        )
        .unwrap();
    state.storage.revoke_device(&dev_id).unwrap();
    state.notify_revoked(dev_id.as_str());
    assert!(state.is_revoked_locally(dev_id.as_str()));

    let app = monitor_transport::build_local_router(state.clone());
    let req = Request::builder()
        .method("POST")
        .uri(format!("/api/v1/local/devices/{}/approve", dev_id.as_str()))
        .extension(ConnectInfo(
            "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
        ))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    assert!(!state.is_revoked_locally(dev_id.as_str()));
    assert!(state.storage.is_device_active(&dev_id).unwrap());

    // AUD-028: Approving a nonexistent ID must return 404 Not Found.
    let req_missing = Request::builder()
        .method("POST")
        .uri("/api/v1/local/devices/nonexistent_id/approve")
        .extension(ConnectInfo(
            "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
        ))
        .body(Body::empty())
        .unwrap();
    let res_missing = app.oneshot(req_missing).await.unwrap();
    assert_eq!(res_missing.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn rbac_enforced_across_git_identity_alerts_sessions_and_merge_pr() {
    let (state, op_token) = create_test_state_and_token().await;
    let viewer_token = "tok_readonly_viewer_12345678";
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_viewer"),
            "Viewer Phone",
            &monitor_transport::auth::TokenAuthenticator::hash_token(viewer_token),
            DeviceRole::Viewer,
        )
        .unwrap();
    state
        .storage
        .insert_alert(&StoredAlert {
            alert_id: "al_rbac_1".to_string(),
            rule_kind: "agent.stalled".to_string(),
            severity: "warning".to_string(),
            title: "Test".to_string(),
            body: "Body".to_string(),
            dedup_key: "k1".to_string(),
            session_id: None,
            value: None,
            created_at_utc_ms: 1000,
            delivered_at_utc_ms: None,
            acked_at_utc_ms: None,
            snoozed_until_utc_ms: None,
            recovered_at_utc_ms: None,
        })
        .unwrap();
    state
        .storage
        .set_setting("allow_git_operations", "1")
        .unwrap();
    state
        .storage
        .set_setting("allow_delete_operations", "0")
        .unwrap();
    let app = build_router(state);

    for (method, uri, body) in [
        ("GET", "/api/v1/git/identity", None),
        (
            "POST",
            "/api/v1/git/identity",
            Some(r#"{"name":"X","email":"x@y.z"}"#),
        ),
        ("POST", "/api/v1/alerts/al_rbac_1/ack", None),
        (
            "POST",
            "/api/v1/alerts/al_rbac_1/snooze",
            Some(r#"{"duration_s":60}"#),
        ),
        ("POST", "/api/v1/watchdog/sessions/s1/kill", None),
        (
            "POST",
            "/api/v1/projects/r1/pulls/1/merge",
            Some(r#"{"method":"merge"}"#),
        ),
    ] {
        let mut req = Request::builder()
            .method(method)
            .uri(uri)
            .header("Authorization", format!("Bearer {viewer_token}"));
        if body.is_some() {
            req = req.header("Content-Type", "application/json");
        }
        let res = app
            .clone()
            .oneshot(req.body(Body::from(body.unwrap_or(""))).unwrap())
            .await
            .unwrap();
        assert_eq!(
            res.status(),
            StatusCode::FORBIDDEN,
            "Viewer must be denied {method} {uri}"
        );
    }

    // Operator without allow_delete_operations is still blocked on kill and merge_pr.
    for uri in [
        "/api/v1/watchdog/sessions/s1/kill",
        "/api/v1/projects/r1/pulls/1/merge",
    ] {
        let req = Request::builder()
            .method("POST")
            .uri(uri)
            .header("Authorization", format!("Bearer {op_token}"))
            .header("Content-Type", "application/json")
            .body(Body::from(r#"{"method":"merge"}"#))
            .unwrap();
        let res = app.clone().oneshot(req).await.unwrap();
        assert_eq!(
            res.status(),
            StatusCode::FORBIDDEN,
            "Operator without allow_delete_operations must be denied {uri}"
        );
    }
}

#[tokio::test]
async fn dismiss_and_clear_stale_devices_hide_clutter_while_preserving_active_and_revoked_tokens() {
    use http_body_util::BodyExt;
    use monitor_transport::state::CloudDeviceInfo;

    let (state, _op) = create_test_state_and_token().await;
    let active_id = DeviceId::new("dev_active_phone");
    state
        .storage
        .insert_device(
            &active_id,
            "Active Phone",
            &monitor_transport::auth::TokenAuthenticator::hash_token(
                "tok_active_keep_secret_123456",
            ),
            DeviceRole::Admin,
        )
        .unwrap();

    let revoked_id = DeviceId::new("dev_old_revoked_phone");
    let revoked_token_hash =
        monitor_transport::auth::TokenAuthenticator::hash_token("tok_revoked_stay_dead_123456");
    state
        .storage
        .insert_device(
            &revoked_id,
            "Old Revoked",
            &revoked_token_hash,
            DeviceRole::Operator,
        )
        .unwrap();
    state.storage.revoke_device(&revoked_id).unwrap();

    {
        let mut cache = state.cloud_devices_cache.lock().unwrap();
        *cache = (
            Some(std::time::Instant::now()),
            vec![
                CloudDeviceInfo {
                    id: "android-dup-1".to_string(),
                    name: "OnePlus CPH2401".to_string(),
                    platform: "android".to_string(),
                    role: "client".to_string(),
                    is_online: 0,
                    last_heartbeat: 100,
                },
                CloudDeviceInfo {
                    id: "android-dup-2".to_string(),
                    name: "OnePlus CPH2401".to_string(),
                    platform: "android".to_string(),
                    role: "client".to_string(),
                    is_online: 0,
                    last_heartbeat: 99,
                },
                CloudDeviceInfo {
                    id: "android-dup-3".to_string(),
                    name: "V2511".to_string(),
                    platform: "android".to_string(),
                    role: "client".to_string(),
                    is_online: 0,
                    last_heartbeat: 98,
                },
            ],
        );
    }

    let app = monitor_transport::build_local_router(state.clone());

    // 1. Dismiss single cloud-only unverified device
    let dismiss_res = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/v1/local/devices/android-dup-1/dismiss")
                .extension(ConnectInfo(
                    "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
                ))
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(dismiss_res.status(), StatusCode::NO_CONTENT);

    // 2. Bulk-clear remaining unverified devices
    let clear_unverified = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/v1/local/devices/clear-stale")
                .header("Content-Type", "application/json")
                .extension(ConnectInfo(
                    "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
                ))
                .body(Body::from(r#"{"scope":"unverified"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(clear_unverified.status(), StatusCode::OK);

    // 3. Bulk-clear revoked devices
    let clear_revoked = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/v1/local/devices/clear-stale")
                .header("Content-Type", "application/json")
                .extension(ConnectInfo(
                    "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
                ))
                .body(Body::from(r#"{"scope":"revoked"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(clear_revoked.status(), StatusCode::OK);

    // 4. Verify status only shows Active Phone (all 3 cloud dups & revoked device are hidden)
    let status_res = app
        .clone()
        .oneshot(
            Request::builder()
                .method("GET")
                .uri("/api/v1/local/status")
                .extension(ConnectInfo(
                    "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
                ))
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(status_res.status(), StatusCode::OK);
    let body_bytes = status_res.into_body().collect().await.unwrap().to_bytes();
    let status_json: serde_json::Value = serde_json::from_slice(&body_bytes).unwrap();
    let listed_ids: Vec<&str> = status_json["devices"]
        .as_array()
        .unwrap()
        .iter()
        .filter_map(|d| d["device_id"].as_str())
        .collect();
    assert!(listed_ids.contains(&"dev_active_phone"));
    assert!(!listed_ids.contains(&"android-dup-1"));
    assert!(!listed_ids.contains(&"android-dup-2"));
    assert!(!listed_ids.contains(&"android-dup-3"));
    assert!(!listed_ids.contains(&"dev_old_revoked_phone"));

    // AUD-01 invariant: revoked device credential remains permanently revoked in SQLite.
    assert!(!state.storage.is_device_active(&revoked_id).unwrap());

    // Invalid scope rejected cleanly
    let bad_scope = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/v1/local/devices/clear-stale")
                .header("Content-Type", "application/json")
                .extension(ConnectInfo(
                    "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
                ))
                .body(Body::from(r#"{"scope":"invalid"}"#))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(bad_scope.status(), StatusCode::BAD_REQUEST);
}
