#[tokio::test]
async fn test_adversarial_pull_request_merge() {
    let state = create_test_state();
    let app = build_router(state.clone());

    let dir = tempfile::tempdir().expect("tempdir");
    let repo_path = dir.path();
    std::process::Command::new("git")
        .args(["init", "-b", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git init");

    let repo_id = RepoId::new("r_pr_adv");
    state
        .storage
        .register_repo(
            &repo_id,
            &repo_path.to_string_lossy(),
            "PR Adversarial Repo",
            TrustProfile::Trusted,
        )
        .expect("register repo");

    let op_token = "tok_op_pr_adv_secret_1234567890";
    let op_hash = TokenAuthenticator::hash_token(op_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_op_pr_adv"),
            "PR Op Adv",
            &op_hash,
            DeviceRole::Operator,
        )
        .expect("insert op device");

    let viewer_token = "tok_viewer_pr_adv_secret_12345678";
    let viewer_hash = TokenAuthenticator::hash_token(viewer_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_viewer_pr_adv"),
            "PR Viewer Adv",
            &viewer_hash,
            DeviceRole::Viewer,
        )
        .expect("insert viewer device");

    // 1. Missing Auth header -> 401
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_adv/pulls/1/merge")
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::UNAUTHORIZED);

    // 2. Viewer token -> 403 Forbidden
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_adv/pulls/1/merge")
        .header("Authorization", format!("Bearer {viewer_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);

    // 2b. Operator without allow_delete_operations (fail-closed T3 policy) -> 403 Forbidden
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_adv/pulls/1/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);

    // Explicitly grant destructive delete/merge policy for the remaining PR merge checks.
    state
        .storage
        .set_setting("allow_delete_operations", "1")
        .expect("grant allow_delete_operations");

    // 3. PR Number 0 -> 400 Bad Request
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_adv/pulls/0/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::BAD_REQUEST);

    // 4. Non-existent repo -> 404 Not Found
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_nonexistent/pulls/1/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::NOT_FOUND);

    // 5. Unsupported merge methods:
    let bad_methods = vec![
        "cherry-pick",
        "",
        "fast-forward",
        "merge; rm -rf /",
        "   ",
        "invalid_method_xyz",
    ];
    for m in bad_methods {
        let payload = MergePrRequest {
            method: Some(m.to_string()),
        };
        let req = Request::builder()
            .method("POST")
            .uri("/api/v1/projects/r_pr_adv/pulls/1/merge")
            .header("Authorization", format!("Bearer {op_token}"))
            .header("Content-Type", "application/json")
            .body(Body::from(serde_json::to_string(&payload).unwrap()))
            .unwrap();
        let res = app.clone().oneshot(req).await.unwrap();
        assert_eq!(
            res.status(),
            StatusCode::BAD_REQUEST,
            "Expected 400 for method '{m}'"
        );
    }

    // 6. Non-string JSON method (e.g. number 12345) -> 400 or 422
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_pr_adv/pulls/1/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(r#"{"method": 12345}"#))
        .unwrap();
    let res = app.clone().oneshot(req).await.unwrap();
    assert!(
        res.status() == StatusCode::BAD_REQUEST || res.status() == StatusCode::UNPROCESSABLE_ENTITY,
        "Expected 400 or 422 for numeric method, got {}",
        res.status()
    );

    // 7. Case-insensitive valid methods pass method validation
    // (They fail on git execution because PR doesn't exist on remote, but status is 500 or conflict, not 400 InvalidArgument)
    for m in ["MERGE", "Squash", "REBASE"] {
        let payload = MergePrRequest {
            method: Some(m.to_string()),
        };
        let req = Request::builder()
            .method("POST")
            .uri("/api/v1/projects/r_pr_adv/pulls/1/merge")
            .header("Authorization", format!("Bearer {op_token}"))
            .header("Content-Type", "application/json")
            .body(Body::from(serde_json::to_string(&payload).unwrap()))
            .unwrap();
        let res = app.clone().oneshot(req).await.unwrap();
        // Since there is no remote PR, it fails in git fetch/merge with 500, NOT 400
        assert_ne!(
            res.status(),
            StatusCode::BAD_REQUEST,
            "Method '{m}' should be recognized as valid"
        );
    }
}
