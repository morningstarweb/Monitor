#![allow(
    clippy::all,
    clippy::unwrap_used,
    clippy::expect_used,
    clippy::print_stdout,
    unused_imports,
    unused_variables
)]

use monitor_core::{RepoId, SystemSnapshot, TrustProfile};
use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::AppState;
use monitor_transport::cloud::{
    CommandResult, PendingCommand, PrCacheEntry, collect_pull_requests, collect_workspaces,
    execute_pending_command,
};
use std::collections::{HashMap, HashSet};
use std::time::Duration;

fn create_challenge_test_state() -> AppState {
    let storage = Storage::in_memory().expect("in-memory storage");
    // AUD-04: these tests exercise an explicitly authorized operator.
    storage
        .set_setting("allow_delete_operations", "1")
        .expect("set allow_delete_operations");
    storage
        .set_setting("allow_cloud_operations", "1")
        .expect("grant cloud operations");
    storage
        .set_setting("allow_local_network", "1")
        .expect("grant local network");
    storage
        .set_setting("allow_git_operations", "1")
        .expect("grant git operations");
    let telemetry = TelemetrySampler::new();
    let git_executor = monitor_git::GitExecutor::new().expect("git executor");
    AppState::new(storage, telemetry, git_executor)
}

include!("empirical_challenge_m1/telemetry.rs");
include!("empirical_challenge_m1/pull_request_cache.rs");
include!("empirical_challenge_m1/heartbeat_payload.rs");
include!("empirical_challenge_m1/command_deduplication.rs");
include!("empirical_challenge_m1/command_edges.rs");
include!("empirical_challenge_m1/tray_policy.rs");
