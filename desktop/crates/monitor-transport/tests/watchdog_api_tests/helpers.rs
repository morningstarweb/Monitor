use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use monitor_core::{PairingRequest, PairingResponse};
use monitor_git::CheckpointEngine;
use monitor_storage::{Storage, StoredCheckpoint};
use monitor_telemetry::TelemetrySampler;
use monitor_transport::{AppState, build_router};
use tower::ServiceExt;

pub async fn create_test_state_and_token() -> (AppState, String) {
    let storage = Storage::in_memory().unwrap();
    // AUD-04: oneshot() requests lack ConnectInfo; grant LAN explicitly so
    // tests exercise the intended operator behavior.
    storage.set_setting("allow_local_network", "1").unwrap();
    let state = AppState::new(
        storage,
        TelemetrySampler::new(),
        monitor_git::GitExecutor::new().unwrap(),
    );
    let app = build_router(state.clone());

    let valid_code = state.generate_pairing_code();
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/pair")
        .header("Content-Type", "application/json")
        .body(Body::from(
            serde_json::to_vec(&PairingRequest {
                pairing_code: valid_code,
                device_name: "Test Phone".to_string(),
                public_key: None,
            })
            .unwrap(),
        ))
        .unwrap();

    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let body = res.into_body().collect().await.unwrap().to_bytes();
    let pair_resp: PairingResponse = serde_json::from_slice(&body).unwrap();
    (state, pair_resp.device_token)
}

pub fn run_git(dir: &std::path::Path, args: &[&str]) {
    let out = std::process::Command::new("git")
        .args(args)
        .current_dir(dir)
        .output()
        .expect("git executable");
    assert!(
        out.status.success(),
        "git {args:?} failed: {}",
        String::from_utf8_lossy(&out.stderr)
    );
}

pub async fn seed_checkpoint_repo(state: &AppState) -> (tempfile::TempDir, String) {
    let tmp = tempfile::tempdir().expect("temp repo");
    let repo = tmp.path();
    run_git(repo, &["init", "-q"]);
    run_git(repo, &["config", "user.email", "audit@example.com"]);
    run_git(repo, &["config", "user.name", "Audit"]);
    std::fs::write(repo.join("state.txt"), "v1").expect("write v1");
    run_git(repo, &["add", "."]);
    run_git(repo, &["commit", "-q", "-m", "v1"]);

    let executor = monitor_git::GitExecutor::new().expect("git executor");
    let info = CheckpointEngine::create_checkpoint(&executor, repo, "audit checkpoint", false)
        .await
        .expect("create checkpoint");

    state
        .storage
        .insert_checkpoint(&StoredCheckpoint {
            checkpoint_id: info.checkpoint_id.clone(),
            repo_path: repo.to_string_lossy().to_string(),
            branch: info.branch,
            commit_sha: info.commit_sha,
            created_at_utc_ms: info.created_at_utc_ms,
            files_changed_count: info.files_changed_count,
            lines_added: info.lines_added,
            lines_deleted: info.lines_deleted,
            is_safety: info.is_safety,
            description: info.description,
        })
        .expect("store checkpoint");

    (tmp, info.checkpoint_id)
}

pub async fn rollback_request(app: &axum::Router, token: &str, checkpoint_id: &str) -> StatusCode {
    let req = Request::builder()
        .method("POST")
        .uri(format!("/api/v1/git/checkpoints/{checkpoint_id}/rollback"))
        .header("Authorization", format!("Bearer {token}"))
        .body(Body::empty())
        .unwrap();
    app.clone().oneshot(req).await.unwrap().status()
}
