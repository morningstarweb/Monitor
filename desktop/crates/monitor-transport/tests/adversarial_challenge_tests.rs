#![allow(clippy::unwrap_used, clippy::expect_used, clippy::print_stdout)]

use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use monitor_core::{
    DeviceId, DeviceRole, MergePrRequest, ProcessKillResponse, RepoId, TrustProfile,
};
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::auth::TokenAuthenticator;
use monitor_transport::{AppState, build_router};
use tower::ServiceExt;

fn create_test_state() -> AppState {
    let storage = Storage::in_memory().expect("in-memory storage");
    // AUD-04: adversarial tests exercise authorized policy states explicitly.
    storage
        .set_setting("allow_git_operations", "1")
        .expect("grant git");
    storage
        .set_setting("allow_cloud_operations", "1")
        .expect("grant cloud");
    // Tests use oneshot() without ConnectInfo, so they are treated as remote
    // peers and need the LAN permission explicitly granted.
    storage
        .set_setting("allow_local_network", "1")
        .expect("grant LAN");
    let telemetry = TelemetrySampler::new();
    let git_executor = monitor_git::GitExecutor::new().expect("git executor");
    AppState::new(storage, telemetry, git_executor)
}
include!("adversarial_challenge_tests/process_kill.rs");
include!("adversarial_challenge_tests/pull_request_merge.rs");
include!("adversarial_challenge_tests/merge_conflicts.rs");
include!("adversarial_challenge_tests/rebase_conflicts.rs");
include!("adversarial_challenge_tests/squash_conflicts.rs");
include!("adversarial_challenge_tests/branch_cleanliness.rs");
include!("adversarial_challenge_tests/binary_conflicts.rs");
include!("adversarial_challenge_tests/structural_conflicts/add_add.rs");
include!("adversarial_challenge_tests/structural_conflicts/modify_delete.rs");
include!("adversarial_challenge_tests/structural_conflicts/test.rs");
include!("adversarial_challenge_tests/dirty_working_tree.rs");
include!("adversarial_challenge_tests/multi_commit_rebase.rs");
include!("adversarial_challenge_tests/sequential_recovery.rs");
