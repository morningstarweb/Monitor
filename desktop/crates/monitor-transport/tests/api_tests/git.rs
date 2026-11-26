#[tokio::test]
async fn test_git_api_endpoints_and_rbac() {
    use monitor_core::{
        DeviceId, DeviceRole, GitOperation, GitOperationRequest, GitOperationResult,
        GitWorkspaceSnapshot, RepoId, Revision, TrustProfile,
    };
    use monitor_transport::auth::TokenAuthenticator;
    use std::fs;

    let state = create_test_state();
    let app = build_router(state.clone());

    // 1. Create a temporary git repo on disk
    let dir = tempfile::tempdir().expect("tempdir");
    let repo_path = dir.path();
    std::process::Command::new("git")
        .args(["init", "-b", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git init");
    std::process::Command::new("git")
        .args(["config", "user.name", "API Tester"])
        .current_dir(repo_path)
        .status()
        .expect("git config user.name");
    std::process::Command::new("git")
        .args(["config", "user.email", "api@monitor.local"])
        .current_dir(repo_path)
        .status()
        .expect("git config user.email");

    let repo_id = RepoId::new("r_api_test");
    state
        .storage
        .register_repo(
            &repo_id,
            &repo_path.to_string_lossy(),
            "API Test Repo",
            TrustProfile::Trusted,
        )
        .expect("register repo");

    // 2. Create Operator token and Viewer token
    let op_token = "op_token_12345678901234567890";
    let op_hash = TokenAuthenticator::hash_token(op_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_operator"),
            "Pixel Operator",
            &op_hash,
            DeviceRole::Operator,
        )
        .expect("register operator");

    let viewer_token = "viewer_token_12345678901234567890";
    let viewer_hash = TokenAuthenticator::hash_token(viewer_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_viewer"),
            "Tablet Viewer",
            &viewer_hash,
            DeviceRole::Viewer,
        )
        .expect("register viewer");

    // 3. Test GET workspace as Operator
    let req_ws = Request::builder()
        .uri("/api/v1/projects/r_api_test/workspace")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();

    let res_ws = app.clone().oneshot(req_ws).await.unwrap();
    assert_eq!(res_ws.status(), StatusCode::OK);
    let ws_body = res_ws.into_body().collect().await.unwrap().to_bytes();
    let ws_snapshot: GitWorkspaceSnapshot = serde_json::from_slice(&ws_body).unwrap();
    assert_eq!(ws_snapshot.current_branch, "main");

    // 4. Create a test file
    fs::write(repo_path.join("test.txt"), "hello world").expect("write test file");

    // 5. Test mutating operation as Operator (StageAll)
    let op_req = GitOperationRequest {
        request_id: "req_stage".to_string(),
        expected_repo_revision: Revision(1),
        operation: GitOperation::StageAll,
    };
    let req_op = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_api_test/operations")
        .header("Authorization", format!("Bearer {op_token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(serde_json::to_string(&op_req).unwrap()))
        .unwrap();

    let res_op = app.clone().oneshot(req_op).await.unwrap();
    assert_eq!(res_op.status(), StatusCode::OK);
    let op_body = res_op.into_body().collect().await.unwrap().to_bytes();
    let op_result: GitOperationResult = serde_json::from_slice(&op_body).unwrap();
    assert_eq!(op_result.status, monitor_core::OperationStatus::Succeeded);
    assert_eq!(op_result.new_repo_revision, Revision(2));

    // Commit staged changes
    let commit_req = GitOperationRequest {
        request_id: "req_commit".to_string(),
        expected_repo_revision: Revision(2),
        operation: GitOperation::Commit {
            message_summary: "feat(test): initial commit for graph".to_string(),
            message_body: None,
            stage_all_tracked: false,
            paths: None,
        },
    };
    let req_commit = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_api_test/operations")
        .header("Authorization", format!("Bearer {op_token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(serde_json::to_string(&commit_req).unwrap()))
        .unwrap();
    let res_commit = app.clone().oneshot(req_commit).await.unwrap();
    assert_eq!(res_commit.status(), StatusCode::OK);

    // Test GET graph endpoint
    let req_graph = Request::builder()
        .uri("/api/v1/projects/r_api_test/graph?limit=10")
        .header("Authorization", format!("Bearer {op_token}"))
        .body(Body::empty())
        .unwrap();
    let res_graph = app.clone().oneshot(req_graph).await.unwrap();
    assert_eq!(res_graph.status(), StatusCode::OK);
    let graph_body = res_graph.into_body().collect().await.unwrap().to_bytes();
    let graph_page: monitor_core::GitGraphPage = serde_json::from_slice(&graph_body).unwrap();
    assert_eq!(graph_page.commits.len(), 1);
    assert_eq!(graph_page.commits[0].lane, 0);

    // 6. Test RBAC: Viewer attempt to execute mutating operation must be rejected with 403 Forbidden
    let viewer_op_req = GitOperationRequest {
        request_id: "req_viewer_forbidden".to_string(),
        expected_repo_revision: Revision(3),
        operation: GitOperation::StageAll,
    };
    let req_viewer = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_api_test/operations")
        .header("Authorization", format!("Bearer {viewer_token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(serde_json::to_string(&viewer_op_req).unwrap()))
        .unwrap();

    let res_viewer = app.clone().oneshot(req_viewer).await.unwrap();
    assert_eq!(res_viewer.status(), StatusCode::FORBIDDEN);
}
