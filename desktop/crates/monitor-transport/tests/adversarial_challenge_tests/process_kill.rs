#[tokio::test]
async fn test_adversarial_process_kill() {
    let state = create_test_state();
    let app = build_router(state.clone());

    let op_token = "tok_op_kill_adv_secret_1234567890";
    let op_hash = TokenAuthenticator::hash_token(op_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_op_kill_adv"),
            "Kill Operator Adv",
            &op_hash,
            DeviceRole::Operator,
        )
        .expect("insert op device");

    let viewer_token = "tok_viewer_kill_adv_secret_12345678";
    let viewer_hash = TokenAuthenticator::hash_token(viewer_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_viewer_kill_adv"),
            "Kill Viewer Adv",
            &viewer_hash,
            DeviceRole::Viewer,
        )
        .expect("insert viewer device");

    // 1. Missing Authorization header -> 401
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/100/kill")
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::UNAUTHORIZED);

    // 2. Viewer token -> 403 Forbidden
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/100/kill")
        .header("Authorization", format!("Bearer {viewer_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);

    // 3. Viewer token trying to kill PID 0 -> 403 Forbidden (RBAC must precede PID check)
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/0/kill")
        .header("Authorization", format!("Bearer {viewer_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);

    // 4. Operator token with tray delete disabled -> 403 Forbidden
    state
        .storage
        .set_setting("allow_delete_operations", "0")
        .unwrap();
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/100/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);

    // Enable delete operations for Operator
    state
        .storage
        .set_setting("allow_delete_operations", "1")
        .unwrap();

    // 5. Protected PID 0 -> 400 Bad Request
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/0/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::BAD_REQUEST);

    // 6. Protected PID 4 -> 400 Bad Request
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/4/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::BAD_REQUEST);

    // 7. Self PID -> 400 Bad Request
    let self_pid = std::process::id();
    let req = Request::builder()
        .method("POST")
        .uri(format!("/api/v1/system/processes/{self_pid}/kill"))
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::BAD_REQUEST);

    // 8. Non-existent random high PID -> 404 Not Found
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/999999999/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::NOT_FOUND);

    // 9. u32::MAX (4294967295) -> 404 Not Found
    let req = Request::builder()
        .method("POST")
        .uri(format!("/api/v1/system/processes/{}/kill", u32::MAX))
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::NOT_FOUND);

    // 10. Kill real process, then retry same PID -> 200 OK then 404 Not Found
    let mut child = std::process::Command::new("cmd")
        .args(["/c", "ping", "127.0.0.1", "-n", "30"])
        .spawn()
        .expect("spawn dummy process");
    let child_pid = child.id();

    let req_kill = Request::builder()
        .method("POST")
        .uri(format!("/api/v1/system/processes/{child_pid}/kill"))
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_kill = app.clone().oneshot(req_kill).await.unwrap();
    assert_eq!(res_kill.status(), StatusCode::OK);
    let body = res_kill.into_body().collect().await.unwrap().to_bytes();
    let kill_resp: ProcessKillResponse = serde_json::from_slice(&body).unwrap();
    assert!(kill_resp.success);
    assert_eq!(kill_resp.pid, child_pid);

    let _ = child.kill();
    let _ = child.wait();

    // Small delay to ensure OS process cleanup
    tokio::time::sleep(std::time::Duration::from_millis(100)).await;

    // Second kill must return 404 Not Found
    let req_second = Request::builder()
        .method("POST")
        .uri(format!("/api/v1/system/processes/{child_pid}/kill"))
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_second = app.clone().oneshot(req_second).await.unwrap();
    assert_eq!(res_second.status(), StatusCode::NOT_FOUND);
}
