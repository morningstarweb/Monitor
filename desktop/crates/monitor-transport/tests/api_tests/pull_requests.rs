#[tokio::test]
async fn test_pull_request_api_lifecycle_and_rbac() {
    use monitor_core::{
        DeviceId, DeviceRole, MergePrRequest, PullRequestSummary, RepoId, TrustProfile,
    };
    use monitor_transport::auth::TokenAuthenticator;

    let state = create_test_state();
    let app = build_router(state.clone());

    let dir = tempfile::tempdir().expect("tempdir");
    let repo_path = dir.path();
    std::process::Command::new("git")
        .args(["init", "-b", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git init");

    let repo_id = RepoId::new("r_pr_test");
    state
        .storage
        .register_repo(
            &repo_id,
            &repo_path.to_string_lossy(),
            "PR Test Repo",
            TrustProfile::Trusted,
        )
        .expect("register repo");

    let op_token = "tok_op_pr_test_secret_value_12345678";
    let op_hash = TokenAuthenticator::hash_token(op_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_op_pr"),
            "PR Operator",
            &op_hash,
            DeviceRole::Operator,
        )
        .expect("insert op device");

    let viewer_token = "tok_viewer_pr_test_secret_value_12345678";
    let viewer_hash = TokenAuthenticator::hash_token(viewer_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_viewer_pr"),
            "PR Viewer",
            &viewer_hash,
            DeviceRole::Viewer,
        )
        .expect("insert viewer device");

    // 1. Unauthenticated requests fail with 401 Unauthorized
    let req_unauth_get = Request::builder()
        .uri("/api/v1/projects/r_pr_test/pulls")
        .body(Body::empty())
        .unwrap();
    let res_unauth_get = app.clone().oneshot(req_unauth_get).await.unwrap();
    assert_eq!(res_unauth_get.status(), StatusCode::UNAUTHORIZED);

    let req_unauth_post = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_test/pulls/1/merge")
        .body(Body::empty())
        .unwrap();
    let res_unauth_post = app.clone().oneshot(req_unauth_post).await.unwrap();
    assert_eq!(res_unauth_post.status(), StatusCode::UNAUTHORIZED);

    // 2. Non-existent repo returns 404 Not Found
    let req_404 = Request::builder()
        .uri("/api/v1/projects/r_nonexistent/pulls")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_404 = app.clone().oneshot(req_404).await.unwrap();
    assert_eq!(res_404.status(), StatusCode::NOT_FOUND);

    // 3. Viewer role can list PRs (read-only) -> 200 OK
    let req_viewer_get = Request::builder()
        .uri("/api/v1/projects/r_pr_test/pulls")
        .header("Authorization", format!("Bearer {viewer_token}"))
        .body(Body::empty())
        .unwrap();
    let res_viewer_get = app.clone().oneshot(req_viewer_get).await.unwrap();
    assert_eq!(res_viewer_get.status(), StatusCode::OK);
    let body_bytes = res_viewer_get
        .into_body()
        .collect()
        .await
        .unwrap()
        .to_bytes();
    let prs: Vec<PullRequestSummary> = serde_json::from_slice(&body_bytes).unwrap();
    assert!(prs.is_empty()); // local repo without remote returns empty list gracefully

    // 4. Viewer role attempting to merge PR returns 403 Forbidden
    let req_viewer_merge = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_test/pulls/1/merge")
        .header("Authorization", format!("Bearer {viewer_token}"))
        .body(Body::empty())
        .unwrap();
    let res_viewer_merge = app.clone().oneshot(req_viewer_merge).await.unwrap();
    assert_eq!(res_viewer_merge.status(), StatusCode::FORBIDDEN);

    // 5. Invalid PR number (0) returns 400 Bad Request
    let req_zero_pr = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_test/pulls/0/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_zero_pr = app.clone().oneshot(req_zero_pr).await.unwrap();
    assert_eq!(res_zero_pr.status(), StatusCode::BAD_REQUEST);

    // 6. Invalid merge method returns 400 Bad Request
    let bad_method_req = MergePrRequest {
        method: Some("fast-forward".to_string()),
    };
    let req_bad_method = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_test/pulls/1/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(serde_json::to_string(&bad_method_req).unwrap()))
        .unwrap();
    let res_bad_method = app.clone().oneshot(req_bad_method).await.unwrap();
    assert_eq!(res_bad_method.status(), StatusCode::BAD_REQUEST);

    // 7. System tray policy blocking git operations returns 403 Forbidden
    state
        .storage
        .set_setting("allow_git_operations", "0")
        .unwrap();
    let req_tray_blocked = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_test/pulls/1/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_tray_blocked = app.clone().oneshot(req_tray_blocked).await.unwrap();
    assert_eq!(res_tray_blocked.status(), StatusCode::FORBIDDEN);
}
