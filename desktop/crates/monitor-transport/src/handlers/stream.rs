use crate::state::{AppState, AuthenticatedDevice};
use axum::{
    Extension,
    extract::{
        Query, State,
        ws::{Message, WebSocket, WebSocketUpgrade},
    },
    http::StatusCode,
    response::{IntoResponse, Json},
};
use monitor_core::{
    ApiErrorResponse, DeviceId, DeviceRole, ErrorCode, ProcessKillResponse, RepoSummary,
    ServerEvent, SystemSnapshot, WsTicketResponse,
};
use monitor_telemetry::ProcessKillError;
use std::time::Duration;
use tokio::time::{Instant, MissedTickBehavior};
use tracing::{info, warn};

/// `POST /api/v1/auth/ws-ticket`
pub async fn create_ws_ticket(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
) -> impl IntoResponse {
    let ticket = state.issue_ws_ticket(device.device_id, device.role);
    Json(WsTicketResponse {
        ticket,
        expires_in_seconds: 30,
    })
}

/// `GET /api/v1/device/snapshot`
pub async fn get_device_snapshot(
    State(state): State<AppState>,
    Extension(_device): Extension<AuthenticatedDevice>,
) -> Json<SystemSnapshot> {
    let snapshot = state.telemetry.sample_system_snapshot();
    Json(snapshot)
}

/// `GET /api/v1/projects`
pub async fn get_projects(
    State(state): State<AppState>,
    Extension(_device): Extension<AuthenticatedDevice>,
) -> Result<Json<Vec<RepoSummary>>, (StatusCode, Json<ApiErrorResponse>)> {
    Ok(Json(
        crate::local_handlers::list_project_summaries(&state).await?,
    ))
}

#[derive(Debug, serde::Deserialize)]
pub struct WsQuery {
    pub ticket: Option<String>,
}

/// `GET /api/v1/stream?ticket=<ws_ticket>`
pub async fn websocket_stream(
    ws: WebSocketUpgrade,
    Query(query): Query<WsQuery>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let Some(ticket) = query.ticket else {
        return (
            StatusCode::UNAUTHORIZED,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Missing 'ticket' query parameter for WebSocket stream.",
                false,
            )),
        )
            .into_response();
    };

    let Some((device_id, role)) = state.consume_ws_ticket(&ticket) else {
        return (
            StatusCode::UNAUTHORIZED,
            Json(ApiErrorResponse::new(
                ErrorCode::AuthExpired,
                "Invalid or expired WebSocket ticket.",
                false,
            )),
        )
            .into_response();
    };

    if !state.storage.is_device_active(&device_id).unwrap_or(false) {
        return StatusCode::UNAUTHORIZED.into_response();
    }

    info!(
        "WebSocket client connected: {} (Role: {:?})",
        device_id, role
    );

    ws.on_upgrade(move |socket| handle_socket(socket, state, device_id))
        .into_response()
}

async fn handle_socket(mut socket: WebSocket, state: AppState, device_id: DeviceId) {
    let mut rx = state.event_tx.subscribe();
    let mut telemetry_rx = state.telemetry_bus.subscribe();
    let mut authorization_check = tokio::time::interval(Duration::from_secs(5));
    authorization_check.set_missed_tick_behavior(MissedTickBehavior::Skip);
    let mut heartbeat = tokio::time::interval_at(
        Instant::now() + Duration::from_secs(15),
        Duration::from_secs(15),
    );
    heartbeat.set_missed_tick_behavior(MissedTickBehavior::Skip);

    let initial_snapshot = state.telemetry.sample_system_snapshot();
    if !send_event(&mut socket, &ServerEvent::TelemetryTick(initial_snapshot)).await {
        return;
    }

    if let Ok(projects) = crate::local_handlers::list_project_summaries(&state).await {
        for project in &projects {
            let _ = send_event(&mut socket, &ServerEvent::ProjectUpdated(project.clone())).await;
        }
        if let Some(first_repo) = projects.first() {
            let repo_id = first_repo.repo_id.clone();
            let snap_opt = {
                let cache = state.workspace_cache.lock().await;
                cache.get(&repo_id).map(|(_, s)| s.clone())
            };
            if let Some(snapshot) = snap_opt {
                let _ = send_event(
                    &mut socket,
                    &ServerEvent::WorkspaceUpdated { repo_id, snapshot },
                )
                .await;
            }
        }
    }
    let mut last_activity = Instant::now();

    loop {
        tokio::select! {
            _ = telemetry_rx.changed() => {
                if let Some(event) = telemetry_rx.latest_event()
                    && !send_event(&mut socket, &event).await
                {
                    break;
                }
                last_activity = Instant::now();
            }
            _ = authorization_check.tick() => {
                if !state.storage.is_device_active(&device_id).unwrap_or(false) {
                    let _ = send_frame(&mut socket, Message::Close(None)).await;
                    break;
                }
            }
            _ = heartbeat.tick() => {
                if last_activity.elapsed() >= Duration::from_secs(90) {
                    warn!("WebSocket client {device_id} idle for 90s without traffic; disconnecting");
                    break;
                }
                if !send_frame(&mut socket, Message::Ping(Vec::new().into())).await {
                    break;
                }
            }
            event = rx.recv() => {
                match event {
                    Ok(ev) => {
                        if !send_event(&mut socket, &ev).await {
                            break;
                        }
                        last_activity = Instant::now();
                    }
                    Err(tokio::sync::broadcast::error::RecvError::Lagged(_)) => {
                        if !send_event(&mut socket, &ServerEvent::ResyncRequired).await {
                            break;
                        }
                        last_activity = Instant::now();
                    }
                    Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
                }
            }
            msg = socket.recv() => {
                match msg {
                    Some(Ok(Message::Close(_))) => {
                        info!("Client {device_id} sent Close frame");
                        break;
                    }
                    Some(Err(error)) => {
                        warn!("WebSocket error from {device_id}: {error}");
                        break;
                    }
                    None => break,
                    Some(Ok(_)) => {
                        last_activity = Instant::now();
                    }
                }
            }
        }
    }

    info!("WebSocket client disconnected: {}", device_id);
}

async fn send_frame(socket: &mut WebSocket, frame: Message) -> bool {
    matches!(
        tokio::time::timeout(Duration::from_secs(5), socket.send(frame)).await,
        Ok(Ok(()))
    )
}

async fn send_event(socket: &mut WebSocket, event: &ServerEvent) -> bool {
    match serde_json::to_string(event) {
        Ok(json) => send_frame(socket, Message::Text(json.into())).await,
        Err(error) => {
            warn!("Could not encode live event: {error}");
            false
        }
    }
}

/// `POST /api/v1/system/processes/{pid}/kill`
pub async fn kill_process(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    axum::extract::Path(pid): axum::extract::Path<u32>,
) -> Result<Json<ProcessKillResponse>, (StatusCode, Json<ApiErrorResponse>)> {
    if device.role == DeviceRole::Viewer {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Insufficient permissions: Viewer role cannot terminate system processes.",
                false,
            )),
        ));
    }

    let delete_allowed = state
        .storage
        .get_setting("allow_delete_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);

    if !delete_allowed {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Process termination is blocked by desktop tray policy. Please enable Delete Access in the Windows system tray.",
                false,
            )),
        ));
    }

    match state.telemetry.kill_process(pid) {
        Ok(()) => Ok(Json(ProcessKillResponse { success: true, pid })),
        Err(ProcessKillError::ProtectedProcess) => Err((
            StatusCode::BAD_REQUEST,
            Json(ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                format!("PID {pid} is a protected system process and cannot be terminated."),
                false,
            )),
        )),
        Err(ProcessKillError::NotFound) => Err((
            StatusCode::NOT_FOUND,
            Json(ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                format!("Process with PID {pid} was not found."),
                false,
            )),
        )),
        Err(ProcessKillError::AccessDenied) => Err((
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(ApiErrorResponse::new(
                ErrorCode::InternalError,
                format!(
                    "Failed to terminate process {pid}. Access denied or insufficient OS privileges."
                ),
                true,
            )),
        )),
    }
}
