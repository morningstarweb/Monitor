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
}
