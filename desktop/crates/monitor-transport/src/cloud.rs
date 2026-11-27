use crate::state::AppState;
use monitor_core::{CloudAccount, GitWorkspaceSnapshot, PullRequestSummary, RepoId, Revision};
use monitor_storage::Storage;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;
use tracing::{info, warn};

pub const GOOGLE_CLIENT_ID: &str = "YOUR_GOOGLE_DESKTOP_CLIENT_ID.apps.googleusercontent.com";
pub const BROKER_BASE_URL: &str = "https://your-monitor-broker.workers.dev";
pub const OAUTH_REDIRECT_URI: &str = "http://127.0.0.1:9481/oauth/callback";

include!("cloud/sync.rs");
include!("cloud/commands/execute.rs");
include!("cloud/commands/dispatch.rs");
include!("cloud/commands/v2_operations.rs");
include!("cloud/commands/merge_pr.rs");
include!("cloud/commands/kill_process.rs");
include!("cloud/commands/git_operation.rs");
include!("cloud/commands/kill_agent_session.rs");
include!("cloud/commands/terminal_command.rs");
include!("cloud/commands/terminal_stop.rs");
include!("cloud/identity.rs");
include!("cloud/heartbeat.rs");
include!("cloud/verify_client.rs");
include!("cloud/oauth/task.rs");
include!("cloud/oauth/launcher.rs");
include!("cloud/oauth/callback.rs");
include!("cloud/oauth/token_exchange.rs");
include!("cloud/oauth/broker_registration.rs");
include!("cloud/oauth/success_response.rs");
include!("cloud/oauth/flow.rs");
include!("cloud/oauth/disconnect.rs");
include!("cloud/oauth/pages.rs");
include!("cloud/oauth/parse_callback.rs");

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_token_params_public_client_pkce() {
        let auth_code = "4/0AWgavdf...";
        let code_verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        let client_secret: Option<String> = None;

        let mut token_params: Vec<(&str, &str)> = vec![
            ("code", auth_code),
            ("client_id", GOOGLE_CLIENT_ID),
            ("code_verifier", code_verifier),
            ("redirect_uri", OAUTH_REDIRECT_URI),
            ("grant_type", "authorization_code"),
        ];

        if let Some(ref secret) = client_secret {
            let trimmed = secret.trim();
            if !trimmed.is_empty() {
                token_params.push(("client_secret", trimmed));
            }
        }

        assert!(!token_params.iter().any(|(k, _)| *k == "client_secret"));
        assert!(
            token_params
                .iter()
                .any(|(k, v)| *k == "client_id" && *v == GOOGLE_CLIENT_ID)
        );
        assert!(
            token_params
                .iter()
                .any(|(k, v)| *k == "code_verifier" && *v == code_verifier)
        );
        assert!(
            token_params
                .iter()
                .any(|(k, v)| *k == "grant_type" && *v == "authorization_code")
        );
    }

    #[test]
    fn test_token_params_with_env_secret() {
        let auth_code = "4/test...";
        let code_verifier = "verifier_test";
        let client_secret = Some("GOCSPX-configured_in_env".to_string());

        let mut token_params: Vec<(&str, &str)> = vec![
            ("code", auth_code),
            ("client_id", GOOGLE_CLIENT_ID),
            ("code_verifier", code_verifier),
            ("redirect_uri", OAUTH_REDIRECT_URI),
            ("grant_type", "authorization_code"),
        ];

        if let Some(ref secret) = client_secret {
            let trimmed = secret.trim();
            if !trimmed.is_empty() {
                token_params.push(("client_secret", trimmed));
            }
        }

        assert!(
            token_params
                .iter()
                .any(|(k, v)| *k == "client_secret" && *v == "GOCSPX-configured_in_env")
        );
    }

    #[tokio::test]
    #[allow(clippy::unwrap_used, clippy::expect_used)]
    async fn test_register_rotates_device_id_on_409_conflict() {
        use axum::{Json, Router, http::StatusCode, routing::post};
        use std::sync::{Arc, Mutex as StdMutex};

        let dir = tempfile::tempdir().unwrap();
        let storage = Storage::open(dir.path().join("test.db")).unwrap();
        let old_id = "win_stale_previous_user_id";
        storage.set_setting("machine_device_id", old_id).unwrap();

        let seen_ids = Arc::new(StdMutex::new(Vec::<String>::new()));
        let seen_clone = Arc::clone(&seen_ids);

        let app = Router::new().route(
            "/api/v1/auth/google-verify",
            post(move |Json(payload): Json<serde_json::Value>| {
                let seen = Arc::clone(&seen_clone);
                async move {
                    let dev_id = payload["device_id"]
                        .as_str()
                        .unwrap_or_default()
                        .to_string();
                    seen.lock().unwrap().push(dev_id.clone());
                    if dev_id == old_id {
                        (
                            StatusCode::CONFLICT,
                            Json(serde_json::json!({
                                "error": "Device ID is already registered to another account"
                            })),
                        )
                    } else {
                        (
                            StatusCode::OK,
                            Json(serde_json::json!({
                                "success": true,
                                "token": "session_tok_123",
                                "user": { "email": "newuser@example.com", "name": "New User" }
                            })),
                        )
                    }
                }
            }),
        );

        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        tokio::spawn(async move {
            let _ = axum::serve(listener, app).await;
        });

        let client = reqwest::Client::new();
        let slot = StdMutex::new(None);
        let base_url = format!("http://{addr}");
        let (account, _) =
            register_with_cloud_broker_at(&storage, &slot, 9480, &base_url, &client, "tok", None)
                .await
                .expect("409 conflict should rotate device_id and succeed on retry");

        assert_eq!(account.email, "newuser@example.com");
        assert_ne!(account.device_id, old_id);
        assert!(account.device_id.starts_with("win_"));
        assert_eq!(
            storage.get_setting("machine_device_id").unwrap().as_deref(),
            Some(account.device_id.as_str())
        );
        let attempts = seen_ids.lock().unwrap().clone();
        assert_eq!(attempts.len(), 2);
        assert_eq!(attempts[0], old_id);
        assert_eq!(attempts[1], account.device_id);
    }
}
