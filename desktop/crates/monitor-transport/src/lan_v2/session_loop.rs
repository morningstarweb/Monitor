use crate::session_crypto::SecureSession;
use crate::state::AppState;
use axum::extract::ws::{Message, WebSocket};
use monitor_core::{EnvelopeV2, MessageType, ServerEvent, StreamKind};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tracing::{debug, info, warn};
use uuid::Uuid;

pub(super) async fn run_lan_v2_loop(
    mut socket: WebSocket,
    state: AppState,
    session: Arc<SecureSession>,
    session_uuid: Uuid,
    epoch: u64,
    viewer_device_id: String,
) {
    let mut rx = state.event_tx.subscribe();
    let mut telemetry_rx = state.telemetry_bus.subscribe();
    let mut revocation_rx = state.revocation_tx.subscribe();
    let mut authorization_check = tokio::time::interval(Duration::from_secs(5));
    let mut last_activity = Instant::now();

    push_initial_lan_state(&mut socket, &state, &session, session_uuid, epoch).await;

    loop {
        tokio::select! {
            revoked = revocation_rx.recv() => {
                match revoked {
                    Ok(identity) if identity == viewer_device_id => {
                        info!("Viewer {viewer_device_id} revoked by administrator; terminating LAN v2 session immediately");
                        break;
                    }
                    Ok(_)
                    | Err(tokio::sync::broadcast::error::RecvError::Lagged(_))
                    | Err(tokio::sync::broadcast::error::RecvError::Closed) => {}
                }
            }

            _ = authorization_check.tick() => {
                if state.is_revoked_locally(&viewer_device_id) {
                    info!("Viewer {viewer_device_id} was revoked; terminating v2 session");
                    break;
                }
                let local_allowed = state
                    .storage
                    .get_setting("allow_local_network")
                    .unwrap_or(None)
                    .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
                    .unwrap_or(false);
                if !local_allowed {
                    info!("Local network access was disabled; terminating v2 session for {viewer_device_id}");
                    break;
                }
                if let Ok(Some(trust)) = state.storage.get_viewer_trust(&viewer_device_id) {
                    if trust.revoked_at_utc_ms.is_some() {
                        info!("Viewer {viewer_device_id} was revoked; terminating v2 session");
                        break;
                    }
                } else {
                    break;
                }
            }

            _ = telemetry_rx.changed() => {
                if let Some(snapshot) = telemetry_rx.latest() {
                    let mut envelope = EnvelopeV2::new(
                        session_uuid,
                        epoch,
                        StreamKind::Telemetry,
                        MessageType::Snapshot,
                        session.send_sequence(),
                        session.recv_sequence(),
                        30_000,
                    );
                    envelope.payload = serde_json::to_value(&*snapshot).ok();
                    for part in crate::host_control::split_v2_envelope(
                        &envelope,
                        crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
                    ) {
                        if let Ok(json_bytes) = serde_json::to_vec(&part)
                            && let Ok(ciphertext) = session.encrypt(&json_bytes).await
                            && socket.send(Message::Binary(ciphertext.into())).await.is_err()
                        {
                            break;
                        }
                    }
                }
            }

            incoming = socket.recv() => {
                match incoming {
                    Some(Ok(Message::Binary(ciphertext))) => {
                        last_activity = Instant::now();
                        match session.decrypt(&ciphertext).await {
                            Ok(plaintext) => {
                                handle_client_message(&plaintext, &session, &state, &viewer_device_id, &mut socket).await;
                            }
                            Err(e) => {
                                warn!("Failed to decrypt/verify incoming frame from {viewer_device_id}: {e}");
                                break;
                            }
                        }
                    }
                    Some(Ok(Message::Ping(p))) => {
                        let _ = socket.send(Message::Pong(p)).await;
                    }
                    Some(Ok(Message::Close(_))) | None => {
                        debug!("Viewer {viewer_device_id} closed connection");
                        break;
                    }
                    Some(Err(e)) => {
                        warn!("WebSocket error from {viewer_device_id}: {e}");
                        break;
                    }
                    _ => {}
                }
            }

            event_res = rx.recv() => {
                match event_res {
                    Ok(event) => {
                        let (stream_kind, msg_type) = match &event {
                            ServerEvent::TelemetryTick(_) => (StreamKind::Telemetry, MessageType::Snapshot),
                            ServerEvent::AlertTriggered { .. } => (StreamKind::Telemetry, MessageType::WatchdogAlert),
                            _ => (StreamKind::Control, MessageType::Snapshot),
                        };

                        let mut envelope = EnvelopeV2::new(
                            session_uuid,
                            epoch,
                            stream_kind,
                            msg_type,
                            session.send_sequence(),
                            session.recv_sequence(),
                            30_000,
                        );
                        envelope.payload = serde_json::to_value(&event).ok();

                        for part in crate::host_control::split_v2_envelope(
                            &envelope,
                            crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
                        ) {
                            if let Ok(json_bytes) = serde_json::to_vec(&part)
                                && let Ok(ciphertext) = session.encrypt(&json_bytes).await
                                && socket.send(Message::Binary(ciphertext.into())).await.is_err()
                            {
                                break;
                            }
                        }
                    }
                    Err(tokio::sync::broadcast::error::RecvError::Lagged(lagged)) => {
                        warn!("Client {viewer_device_id} lagged by {lagged} events");
                    }
                    Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
                }
            }
        }

        if last_activity.elapsed() >= Duration::from_secs(90) {
            warn!("Client {viewer_device_id} idle for 90s; closing v2 session");
            break;
        }
    }
}

async fn push_initial_lan_state(
    socket: &mut WebSocket,
    state: &AppState,
    session: &SecureSession,
    session_uuid: Uuid,
    epoch: u64,
) {
    let initial_snapshot = state.telemetry.sample_system_snapshot();
    let mut initial_envelope = EnvelopeV2::new(
        session_uuid,
        epoch,
        StreamKind::Telemetry,
        MessageType::Snapshot,
        session.send_sequence(),
        session.recv_sequence(),
        30_000,
    );
    initial_envelope.payload = serde_json::to_value(&initial_snapshot).ok();

    for part in crate::host_control::split_v2_envelope(
        &initial_envelope,
        crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
    ) {
        if let Ok(json_bytes) = serde_json::to_vec(&part)
            && let Ok(ciphertext) = session.encrypt(&json_bytes).await
        {
            let _ = socket.send(Message::Binary(ciphertext.into())).await;
        }
    }

    if let Ok(projects) = crate::local_handlers::list_project_summaries(state).await {
        for summary in &projects {
            let mut proj_env = EnvelopeV2::new(
                session_uuid,
                epoch,
                StreamKind::Control,
                MessageType::ProjectsResult,
                session.send_sequence(),
                session.recv_sequence(),
                30_000,
            );
            proj_env.payload = serde_json::to_value(summary).ok();
            for part in crate::host_control::split_v2_envelope(
                &proj_env,
                crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
            ) {
                if let Ok(json_bytes) = serde_json::to_vec(&part)
                    && let Ok(ciphertext) = session.encrypt(&json_bytes).await
                {
                    let _ = socket.send(Message::Binary(ciphertext.into())).await;
                }
            }
        }

        if let Some(first_repo) = projects.first() {
            let repo_id = first_repo.repo_id.clone();
            let cached_ws = {
                let cache = state.workspace_cache.lock().await;
                cache.get(&repo_id).cloned()
            };
            let snap_opt = match cached_ws {
                Some((_, s)) => Some(s),
                None => {
                    let lock = state
                        .repo_coordinator
                        .get_or_create_repo_lock(&repo_id, monitor_core::Revision(1))
                        .await;
                    let guard = lock.lock().await;
                    monitor_git::query_git_status(
                        &state.git_executor,
                        std::path::Path::new(&first_repo.path),
                        &repo_id,
                        guard.revision,
                    )
                    .await
                    .ok()
                }
            };
            if let Some(snapshot) = snap_opt {
                let mut ws_env = EnvelopeV2::new(
                    session_uuid,
                    epoch,
                    StreamKind::Control,
                    MessageType::Snapshot,
                    session.send_sequence(),
                    session.recv_sequence(),
                    30_000,
                );
                ws_env.payload = serde_json::to_value(ServerEvent::WorkspaceUpdated {
                    repo_id: repo_id.clone(),
                    snapshot,
                })
                .ok();
                for part in crate::host_control::split_v2_envelope(
                    &ws_env,
                    crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
                ) {
                    if let Ok(json_bytes) = serde_json::to_vec(&part)
                        && let Ok(ciphertext) = session.encrypt(&json_bytes).await
                    {
                        let _ = socket.send(Message::Binary(ciphertext.into())).await;
                    }
                }
            }
        }
    }
}

async fn handle_client_message(
    plaintext: &[u8],
    session: &SecureSession,
    state: &AppState,
    viewer_device_id: &str,
    socket: &mut WebSocket,
) {
    let Ok(envelope) = serde_json::from_slice::<EnvelopeV2>(plaintext) else {
        warn!("Failed to parse EnvelopeV2 from decrypted plaintext");
        return;
    };

    let preflight_denial = if envelope.message_type == MessageType::CommandRequest {
        let command_type = envelope
            .payload
            .as_ref()
            .and_then(|payload| payload.get("command_type"))
            .and_then(serde_json::Value::as_str)
            .unwrap_or_default();
        if command_type.is_empty() {
            None
        } else {
            crate::authorization::authorize_operation(
                state,
                viewer_device_id,
                command_type,
                crate::authorization::OperationTarget::LocalNetwork,
            )
            .err()
        }
    } else {
        None
    };

    let response = if let Some(denial) = preflight_denial {
        warn!(
            "LAN v2 command rejected for {viewer_device_id}: {}",
            denial.message()
        );
        let mut rejected = EnvelopeV2::new(
            envelope.session_id,
            envelope.epoch,
            StreamKind::Control,
            MessageType::CommandRejected,
            session.send_sequence(),
            session.recv_sequence(),
            envelope.deadline_ms,
        );
        rejected.command_id = envelope.command_id.or(Some(envelope.message_id));
        rejected.payload = Some(serde_json::json!({
            "status": "rejected", "error": denial.message()
        }));
        Some(rejected)
    } else {
        crate::host_control::process_v2_envelope(&envelope, session, state, viewer_device_id, true)
            .await
    };

    if let Some(response) = response {
        for part in crate::host_control::split_v2_envelope(
            &response,
            crate::session_crypto::MAX_PLAINTEXT_FRAME_SIZE,
        ) {
            if let Ok(bytes) = serde_json::to_vec(&part)
                && let Ok(ciphertext) = session.encrypt(&bytes).await
                && socket
                    .send(Message::Binary(ciphertext.into()))
                    .await
                    .is_err()
            {
                break;
            }
        }
    }
}
