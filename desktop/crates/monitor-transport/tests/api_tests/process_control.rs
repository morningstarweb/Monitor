#[tokio::test]
async fn test_process_kill_api_and_guards() {
    use monitor_core::{DeviceId, DeviceRole, ProcessKillResponse};
    use monitor_transport::auth::TokenAuthenticator;

    let state = create_test_state();
    let app = build_router(state.clone());

    let op_token = "tok_op_kill_test_secret_12345678";
    let op_hash = TokenAuthenticator::hash_token(op_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_op_kill"),
            "Kill Operator",
            &op_hash,
            DeviceRole::Operator,
        )
        .expect("insert op device");

    let viewer_token = "tok_viewer_kill_test_secret_12345678";
    let viewer_hash = TokenAuthenticator::hash_token(viewer_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_viewer_kill"),
            "Kill Viewer",
            &viewer_hash,
            DeviceRole::Viewer,
        )
        .expect("insert viewer device");

    // 1. Unauthenticated request -> 401 Unauthorized
    let req_unauth = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/1234/kill")
        .body(Body::empty())
        .unwrap();
    let res_unauth = app.clone().oneshot(req_unauth).await.unwrap();
    assert_eq!(res_unauth.status(), StatusCode::UNAUTHORIZED);

    // 2. Viewer role -> 403 Forbidden
    let req_viewer = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/1234/kill")
        .header("Authorization", format!("Bearer {viewer_token}"))
        .body(Body::empty())
        .unwrap();
    let res_viewer = app.clone().oneshot(req_viewer).await.unwrap();
    assert_eq!(res_viewer.status(), StatusCode::FORBIDDEN);

    // 3. Tray delete access disabled (default) -> 403 Forbidden
    let req_tray_blocked = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/1234/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_tray_blocked = app.clone().oneshot(req_tray_blocked).await.unwrap();
    assert_eq!(res_tray_blocked.status(), StatusCode::FORBIDDEN);

    // Enable delete access
    state
        .storage
        .set_setting("allow_delete_operations", "1")
        .unwrap();

    // 4. Protected PID 0 -> 400 Bad Request
    let req_pid0 = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/0/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_pid0 = app.clone().oneshot(req_pid0).await.unwrap();
    assert_eq!(res_pid0.status(), StatusCode::BAD_REQUEST);

    // 5. Protected PID 4 -> 400 Bad Request
    let req_pid4 = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/4/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_pid4 = app.clone().oneshot(req_pid4).await.unwrap();
    assert_eq!(res_pid4.status(), StatusCode::BAD_REQUEST);

    // 6. Self PID -> 400 Bad Request
    let self_pid = std::process::id();
    let req_self = Request::builder()
        .method("POST")
        .uri(format!("/api/v1/system/processes/{self_pid}/kill"))
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_self = app.clone().oneshot(req_self).await.unwrap();
    assert_eq!(res_self.status(), StatusCode::BAD_REQUEST);

    // 7. Non-existent PID -> 404 Not Found
    let req_not_found = Request::builder()
        .method("POST")
        .uri("/api/v1/system/processes/999999999/kill")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_not_found = app.clone().oneshot(req_not_found).await.unwrap();
    assert_eq!(res_not_found.status(), StatusCode::NOT_FOUND);

    // 8. Successful kill of child process -> 200 OK
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
    let kill_body = res_kill.into_body().collect().await.unwrap().to_bytes();
    let kill_resp: ProcessKillResponse = serde_json::from_slice(&kill_body).unwrap();
    assert!(kill_resp.success);
    assert_eq!(kill_resp.pid, child_pid);

    let _ = child.kill();
    let _ = child.wait();
}
