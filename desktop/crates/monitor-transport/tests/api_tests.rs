#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use monitor_core::{
    HealthResponse, PairingRequest, PairingResponse, SystemSnapshot, WsTicketResponse,
};
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::{AppState, build_router};
use tower::ServiceExt;

fn create_test_state() -> AppState {
    let storage = Storage::in_memory().expect("in-memory storage");
    // Tests exercise an explicitly authorized operator session. Defaults are
    // now fail-closed (AUD-04), so grant the needed permissions explicitly.
    storage
        .set_setting("allow_git_operations", "1")
        .expect("grant git");
    storage
        .set_setting("allow_cloud_operations", "1")
        .expect("grant cloud");
    storage
        .set_setting("allow_local_network", "1")
        .expect("grant LAN");
    let telemetry = TelemetrySampler::new();
    let git_executor = monitor_git::GitExecutor::new().expect("git executor");
    AppState::new(storage, telemetry, git_executor)
}

include!("api_tests/management_routes.rs");
include!("api_tests/health.rs");
include!("api_tests/pairing.rs");
include!("api_tests/git.rs");
include!("api_tests/pull_requests.rs");
include!("api_tests/process_control.rs");
include!("api_tests/cloud.rs");
