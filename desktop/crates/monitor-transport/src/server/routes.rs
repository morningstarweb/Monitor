use super::auth_middleware::{auth_middleware, native_client_only};
use crate::git_handlers::{
    execute_operation, get_branches, get_commit_files, get_file_diff, get_git_identity, get_graph,
    get_pull_requests, get_workspace, merge_pull_request_handler, set_git_identity,
};
use crate::handlers::{
    create_ws_ticket, get_device_snapshot, get_projects, health, kill_process, pair,
    request_pairing_code, websocket_stream,
};
use crate::local_handlers::{
    approve_device, clear_alerts_handler, clear_stale_devices_handler, disconnect_cloud_handler,
    dismiss_device_handler, generate_device_otp_handler, get_local_status, get_permissions_handler,
    register_project, remove_project, revoke_device, set_device_tier_handler,
    start_cloud_login_handler, toggle_checkpoints_access_handler, toggle_cloud_access_handler,
    toggle_delete_access_handler, toggle_git_access_handler, toggle_local_access_handler,
    toggle_terminal_access_handler,
};
use crate::state::AppState;
use crate::watchdog_handlers::{
    ack_alert_handler, create_checkpoint_handler, kill_agent_session_handler,
    list_agent_profiles_handler, list_agent_sessions_handler, list_alert_rules_handler,
    list_alerts_handler, list_checkpoints_handler, rollback_checkpoint_handler,
    snooze_alert_handler, update_alert_rule_handler,
};
use axum::{
    Json, Router,
    extract::State,
    http::StatusCode,
    middleware,
    routing::{delete, get, post},
};

/// Builds the Axum application router.
pub fn build_router(state: AppState) -> Router {
    let protected_routes = Router::new()
        .route("/auth/ws-ticket", post(create_ws_ticket))
        .route("/device/snapshot", get(get_device_snapshot))
        .route("/projects", get(get_projects))
        .route("/projects/{repo_id}/workspace", get(get_workspace))
        .route("/projects/{repo_id}/diff", get(get_file_diff))
        .route(
            "/projects/{repo_id}/commits/{commit_oid}/files",
            get(get_commit_files),
        )
        .route("/projects/{repo_id}/branches", get(get_branches))
        .route("/projects/{repo_id}/graph", get(get_graph))
        .route("/projects/{repo_id}/operations", post(execute_operation))
        .route("/projects/{repo_id}/pulls", get(get_pull_requests))
        .route(
            "/projects/{repo_id}/pulls/{pr_number}/merge",
            post(merge_pull_request_handler),
        )
        .route("/system/processes/{pid}/kill", post(kill_process))
        .route("/permissions", get(get_permissions_handler))
        .route(
            "/git/identity",
            get(get_git_identity).post(set_git_identity),
        )
        .route("/alerts", get(list_alerts_handler))
        .route("/alerts/{id}/ack", post(ack_alert_handler))
        .route("/alerts/{id}/snooze", post(snooze_alert_handler))
        .route("/watchdog/sessions", get(list_agent_sessions_handler))
        .route("/watchdog/rules", get(list_alert_rules_handler))
        .route("/watchdog/rules/{kind}", post(update_alert_rule_handler))
        .route("/watchdog/profiles", get(list_agent_profiles_handler))
        .route(
            "/git/checkpoints",
            get(list_checkpoints_handler).post(create_checkpoint_handler),
        )
        .route(
            "/git/checkpoints/{id}/rollback",
            post(rollback_checkpoint_handler),
        )
        .route(
            "/watchdog/sessions/{id}/kill",
            post(kill_agent_session_handler),
        )
        .route(
            "/projects/{repo_id}/terminal/run",
            post(crate::terminal_handlers::run_project_terminal_handler),
        )
        .route(
            "/projects/{repo_id}/terminal/stop",
            post(crate::terminal_handlers::stop_project_terminal_handler),
        )
        .route(
            "/settings/terminal",
            get(crate::terminal_handlers::get_terminal_setting_handler)
                .post(crate::terminal_handlers::set_terminal_setting_handler),
        )
        .route_layer(middleware::from_fn_with_state(
            state.clone(),
            auth_middleware,
        ));

    let public_routes = Router::new()
        .route("/health", get(health))
        .route("/auth/pair", post(pair))
        .route("/stream", get(websocket_stream));

    let v2_routes = Router::new()
        .route("/stream", get(crate::lan_v2::websocket_stream_v2))
        .route("/noise-key", get(get_noise_public_key_handler));

    Router::new()
        .route("/", get(health))
        .nest("/api/v1", public_routes.merge(protected_routes))
        .nest("/api/v2", v2_routes)
        .with_state(state)
}

/// Exposes the host's static Noise public key on LAN so local viewers can
/// perform the Noise IK handshake without roundtripping to the cloud broker.
async fn get_noise_public_key_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, StatusCode> {
    let Ok(host_keys) = crate::session_crypto::get_or_create_host_noise_keypair(&state.storage)
    else {
        return Err(StatusCode::INTERNAL_SERVER_ERROR);
    };
    Ok(Json(serde_json::json!({
        "public_key": crate::session_crypto::hex_encode(&host_keys.public_key)
    })))
}

pub fn build_local_router(state: AppState) -> Router {
    let local_routes = Router::new()
        .route("/auth/request-code", post(request_pairing_code))
        .route("/local/status", get(get_local_status))
        .route("/local/pairing-code", post(request_pairing_code))
        .route("/local/projects", post(register_project))
        .route("/local/projects/{repo_id}", delete(remove_project))
        .route("/local/devices/{device_id}", delete(revoke_device))
        .route(
            "/local/devices/{device_id}/dismiss",
            post(dismiss_device_handler),
        )
        .route(
            "/local/devices/clear-stale",
            post(clear_stale_devices_handler),
        )
        .route(
            "/local/devices/{device_id}/otp",
            post(generate_device_otp_handler),
        )
        .route(
            "/local/devices/{device_id}/tier",
            post(set_device_tier_handler),
        )
        .route("/local/devices/{device_id}/approve", post(approve_device))
        .route("/local/cloud/login", post(start_cloud_login_handler))
        .route("/local/cloud/disconnect", post(disconnect_cloud_handler))
        .route(
            "/local/permissions/toggle-git",
            post(toggle_git_access_handler),
        )
        .route(
            "/local/permissions/toggle-delete",
            post(toggle_delete_access_handler),
        )
        .route(
            "/local/permissions/toggle-cloud",
            post(toggle_cloud_access_handler),
        )
        .route(
            "/local/permissions/toggle-local",
            post(toggle_local_access_handler),
        )
        .route(
            "/local/permissions/toggle-terminal",
            post(toggle_terminal_access_handler),
        )
        .route(
            "/local/permissions/toggle-checkpoints",
            post(toggle_checkpoints_access_handler),
        )
        .route("/local/alerts/clear", post(clear_alerts_handler))
        .route_layer(middleware::from_fn(native_client_only));

    build_router(state.clone()).merge(
        Router::new()
            .nest("/api/v1", local_routes)
            .with_state(state),
    )
}
