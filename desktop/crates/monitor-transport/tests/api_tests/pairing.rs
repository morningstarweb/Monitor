#[tokio::test]
async fn test_pairing_lifecycle_and_replay_protection() {
    let state = create_test_state();
    let app = build_router(state.clone());

    // 1. Pairing with no active desktop session fails
    let req_inactive = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/pair")
        .header("Content-Type", "application/json")
        .body(Body::from(
            serde_json::to_vec(&PairingRequest {
                pairing_code: "123456".to_string(),
                device_name: "Pixel 9".to_string(),
                public_key: None,
            })
            .unwrap(),
        ))
        .unwrap();

    let res_inactive = app.clone().oneshot(req_inactive).await.unwrap();
    assert_eq!(res_inactive.status(), StatusCode::BAD_REQUEST);

    // 2. Desktop activates a 6-digit pairing code
    let valid_code = state.generate_pairing_code();
    assert_eq!(valid_code.len(), 6);

    // 3. Attempt with wrong code fails with 401
    let req_wrong = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/pair")
        .header("Content-Type", "application/json")
        .body(Body::from(
            serde_json::to_vec(&PairingRequest {
                pairing_code: "000000".to_string(),
                device_name: "Pixel 9".to_string(),
                public_key: None,
            })
            .unwrap(),
        ))
        .unwrap();

    let res_wrong = app.clone().oneshot(req_wrong).await.unwrap();
    assert_eq!(res_wrong.status(), StatusCode::UNAUTHORIZED);

    // 4. Attempt with correct code succeeds
    let req_valid = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/pair")
        .header("Content-Type", "application/json")
        .body(Body::from(
            serde_json::to_vec(&PairingRequest {
                pairing_code: valid_code.clone(),
                device_name: "Pixel 9".to_string(),
                public_key: None,
            })
            .unwrap(),
        ))
        .unwrap();

    let res_valid = app.clone().oneshot(req_valid).await.unwrap();
    assert_eq!(res_valid.status(), StatusCode::OK);

    let body = res_valid.into_body().collect().await.unwrap().to_bytes();
    let pair_resp: PairingResponse = serde_json::from_slice(&body).unwrap();
    assert!(pair_resp.device_token.starts_with("mon_dev_"));
    assert_eq!(pair_resp.device_token.len(), 8 + 64);

    // 5. Replay attack: Re-submitting the exact same pairing code is rejected
    let req_replay = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/pair")
        .header("Content-Type", "application/json")
        .body(Body::from(
            serde_json::to_vec(&PairingRequest {
                pairing_code: valid_code,
                device_name: "Attacker Phone".to_string(),
                public_key: None,
            })
            .unwrap(),
        ))
        .unwrap();

    let res_replay = app.clone().oneshot(req_replay).await.unwrap();
    assert_eq!(res_replay.status(), StatusCode::BAD_REQUEST);

    // 6. Test protected telemetry endpoint with the paired token
    let req_telemetry = Request::builder()
        .uri("/api/v1/device/snapshot")
        .header(
            "Authorization",
            format!("Bearer {}", pair_resp.device_token),
        )
        .body(Body::empty())
        .unwrap();

    let res_telemetry = app.clone().oneshot(req_telemetry).await.unwrap();
    assert_eq!(res_telemetry.status(), StatusCode::OK);

    let telem_body = res_telemetry
        .into_body()
        .collect()
        .await
        .unwrap()
        .to_bytes();
    let snapshot: SystemSnapshot = serde_json::from_slice(&telem_body).unwrap();
    assert_eq!(snapshot.schema_version, 3);
    // Physical fan RPM sensors are unavailable via sysinfo, so `cooling` must stay
    // empty rather than synthesizing fake fan telemetry from CPU load (`AUD-012`).
    assert!(snapshot.cooling.is_none());
    assert!(!snapshot.disks.is_empty());

    // 7. Test single-use WS ticket issuance
    let req_ticket = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/ws-ticket")
        .header(
            "Authorization",
            format!("Bearer {}", pair_resp.device_token),
        )
        .body(Body::empty())
        .unwrap();

    let res_ticket = app.clone().oneshot(req_ticket).await.unwrap();
    assert_eq!(res_ticket.status(), StatusCode::OK);

    let ticket_body = res_ticket.into_body().collect().await.unwrap().to_bytes();
    let ticket_resp: WsTicketResponse = serde_json::from_slice(&ticket_body).unwrap();
    assert!(ticket_resp.ticket.starts_with("wst_"));

    // Consuming ticket in state succeeds once, then fails
    let first_consume = state.consume_ws_ticket(&ticket_resp.ticket);
    assert!(first_consume.is_some());
    let second_consume = state.consume_ws_ticket(&ticket_resp.ticket);
    assert!(second_consume.is_none());
}
