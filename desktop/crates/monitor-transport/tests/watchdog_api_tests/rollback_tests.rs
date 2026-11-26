use super::helpers::{
    create_test_state_and_token, rollback_request, run_git, seed_checkpoint_repo,
};
use axum::body::Body;
use axum::http::header::CONTENT_TYPE;
use axum::http::{Request, StatusCode};
use monitor_core::{DeviceId, DeviceRole};
use monitor_transport::build_router;
use tower::ServiceExt;

#[tokio::test]
async fn checkpoint_rollback_denied_when_permission_missing_or_disabled() {
    let (state, op_token) = create_test_state_and_token().await;
    let app = build_router(state.clone());
    let (tmp, checkpoint_id) = seed_checkpoint_repo(&state).await;
    let state_file = tmp.path().join("state.txt");
    std::fs::write(&state_file, "v2").expect("write v2");

    // Permission absent (fail-closed) -> 403, repository untouched.
    assert_eq!(
        rollback_request(&app, &op_token, &checkpoint_id).await,
        StatusCode::FORBIDDEN
    );
    assert_eq!(std::fs::read_to_string(&state_file).unwrap(), "v2");

    // Permission explicitly disabled -> 403, repository untouched.
    state
        .storage
        .set_setting("allow_checkpoint_rollback", "0")
        .unwrap();
    assert_eq!(
        rollback_request(&app, &op_token, &checkpoint_id).await,
        StatusCode::FORBIDDEN
    );
    assert_eq!(std::fs::read_to_string(&state_file).unwrap(), "v2");
}

#[tokio::test]
async fn checkpoint_rollback_denied_for_viewer_and_revoked_device() {
    let (state, op_token) = create_test_state_and_token().await;
    state
        .storage
        .set_setting("allow_checkpoint_rollback", "1")
        .unwrap();
    let app = build_router(state.clone());
    let (tmp, checkpoint_id) = seed_checkpoint_repo(&state).await;
    let state_file = tmp.path().join("state.txt");
    std::fs::write(&state_file, "v2").expect("write v2");

    let viewer_token = "tok_viewer_checkpoint_test_secret_12345678";
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_viewer_checkpoint"),
            "Viewer",
            &monitor_transport::auth::TokenAuthenticator::hash_token(viewer_token),
            DeviceRole::Viewer,
        )
        .expect("insert viewer");

    assert_eq!(
        rollback_request(&app, viewer_token, &checkpoint_id).await,
        StatusCode::FORBIDDEN
    );
    assert_eq!(std::fs::read_to_string(&state_file).unwrap(), "v2");

    // A revoked operator is rejected by the auth middleware with 401.
    let dev_id = DeviceId::new("dev_revoked_checkpoint");
    let revoked_token = "tok_revoked_checkpoint_test_secret_1234";
    state
        .storage
        .insert_device(
            &dev_id,
            "Revoked",
            &monitor_transport::auth::TokenAuthenticator::hash_token(revoked_token),
            DeviceRole::Operator,
        )
        .expect("insert operator");
    state.storage.revoke_device(&dev_id).expect("revoke");
    assert_eq!(
        rollback_request(&app, revoked_token, &checkpoint_id).await,
        StatusCode::UNAUTHORIZED
    );
    assert_eq!(std::fs::read_to_string(&state_file).unwrap(), "v2");

    // Sanity: the enabled operator token is not denied by authorization.
    assert_ne!(
        rollback_request(&app, &op_token, &checkpoint_id).await,
        StatusCode::FORBIDDEN
    );
}

#[tokio::test]
async fn checkpoint_rollback_allowed_for_authorized_operator_and_permission_take_effect() {
    let (state, op_token) = create_test_state_and_token().await;
    let app = build_router(state.clone());
    let (tmp, checkpoint_id) = seed_checkpoint_repo(&state).await;
    let state_file = tmp.path().join("state.txt");

    // First deny while the permission is off.
    std::fs::write(&state_file, "v2").expect("write v2");
    assert_eq!(
        rollback_request(&app, &op_token, &checkpoint_id).await,
        StatusCode::FORBIDDEN
    );
    assert_eq!(std::fs::read_to_string(&state_file).unwrap(), "v2");

    // Grant the permission; the very next request succeeds and the working
    // tree is actually restored to the checkpoint (v1).
    state
        .storage
        .set_setting("allow_checkpoint_rollback", "1")
        .unwrap();
    assert_eq!(
        rollback_request(&app, &op_token, &checkpoint_id).await,
        StatusCode::OK
    );
    assert_eq!(std::fs::read_to_string(&state_file).unwrap(), "v1");
}

#[tokio::test]
async fn checkpoint_create_denied_when_permission_missing() {
    let (state, op_token) = create_test_state_and_token().await;
    let app = build_router(state);
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/git/checkpoints")
        .header("Authorization", format!("Bearer {op_token}"))
        .header(CONTENT_TYPE, "application/json")
        .body(Body::from(
            serde_json::json!({"repo_path": "D:\\nonexistent", "description": "x"}).to_string(),
        ))
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn checkpoint_create_denied_for_unregistered_repo_even_when_permitted() {
    let (state, op_token) = create_test_state_and_token().await;
    state
        .storage
        .set_setting("allow_checkpoint_rollback", "1")
        .unwrap();

    let tmp = tempfile::tempdir().expect("temp repo");
    run_git(tmp.path(), &["init", "-q"]);
    let app = build_router(state);

    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/git/checkpoints")
        .header("Authorization", format!("Bearer {op_token}"))
        .header(CONTENT_TYPE, "application/json")
        .body(Body::from(
            serde_json::json!({
                "repo_path": tmp.path().to_string_lossy(),
                "description": "unregistered path test"
            })
            .to_string(),
        ))
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);
}
