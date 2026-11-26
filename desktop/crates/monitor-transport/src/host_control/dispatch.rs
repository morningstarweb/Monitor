use crate::session_crypto::SecureSession;
use crate::state::AppState;
use base64::Engine;
use base64::engine::general_purpose::STANDARD as BASE64;
use monitor_core::{EnvelopeV2, MessageType, OperationTier, StreamKind, classify_operation};
use tracing::warn;

pub(crate) async fn process_v2_envelope(
    envelope: &EnvelopeV2,
    session: &SecureSession,
    state: &AppState,
    viewer_id: &str,
    is_lan: bool,
) -> Option<EnvelopeV2> {
    if envelope.protocol_version != monitor_core::PROTOCOL_VERSION_2
        || envelope.session_id.to_string() != session.session_id
        || envelope.epoch != session.epoch
    {
        warn!("Rejected EnvelopeV2 with a mismatched protocol session or epoch");
        return None;
    }
    let mut response = match envelope.message_type {
        MessageType::CommandRequest => {
            let command_payload = envelope.payload.clone().unwrap_or(serde_json::Value::Null);
            let command_type = command_payload
                .get("command_type")
                .and_then(serde_json::Value::as_str)
                .unwrap_or_default();
            if command_type.is_empty() {
                return Some(command_response(
                    envelope,
                    session,
                    MessageType::CommandRejected,
                    serde_json::json!({
                        "status": "rejected", "error": "Missing command_type"
                    }),
                ));
            }
            let tier = classify_operation(command_type);
            let viewer_trust = state.storage.get_viewer_trust(viewer_id).ok().flatten();

            if state.is_revoked_locally(viewer_id)
                || viewer_trust
                    .as_ref()
                    .is_some_and(|trust| trust.revoked_at_utc_ms.is_some())
            {
                warn!("Rejected command from revoked viewer {viewer_id}");
                return Some(command_response(
                    envelope,
                    session,
                    MessageType::CommandRejected,
                    serde_json::json!({
                        "status": "rejected", "error": "Device authorization has been revoked"
                    }),
                ));
            }

            let auth_target = if is_lan {
                crate::authorization::OperationTarget::LocalNetwork
            } else {
                crate::authorization::OperationTarget::CloudRelay
            };
            if let Err(denial) = crate::authorization::authorize_operation(
                state,
                viewer_id,
                command_type,
                auth_target,
            ) {
                warn!(
                    "Rejected {command_type} from {viewer_id}: {}",
                    denial.message()
                );
                return Some(command_response(
                    envelope,
                    session,
                    MessageType::CommandRejected,
                    serde_json::json!({
                        "status": "rejected", "error": denial.message()
                    }),
                ));
            }

            let viewer_tier = viewer_trust
                .map(|trust| trust.trust_tier)
                .unwrap_or_else(|| "T0".to_string());
            let permitted = match tier {
                OperationTier::T0Monitoring => true,
                OperationTier::T1ReadRepository => viewer_tier != "T0",
                OperationTier::T2StateMutation => viewer_tier == "T2" || viewer_tier == "T3",
                OperationTier::T3Dangerous => viewer_tier == "T3",
            };
            if !permitted {
                return Some(command_response(
                    envelope,
                    session,
                    MessageType::CommandRejected,
                    serde_json::json!({
                        "status": "rejected",
                        "error": format!("Insufficient viewer trust tier ({viewer_tier}) for operation tier ({tier:?})")
                    }),
                ));
            }
            if command_type == "auth.verify_otp" {
                let payload_val = command_payload
                    .get("payload")
                    .cloned()
                    .unwrap_or(serde_json::Value::Null);
                let otp_code = payload_val
                    .get("pairing_code")
                    .or_else(|| payload_val.get("otp"))
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or_default();
                let dev_name = payload_val
                    .get("device_name")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or("Android Device");
                let cloud_dev_id = payload_val
                    .get("cloud_device_id")
                    .and_then(serde_json::Value::as_str);
                let pub_key = payload_val
                    .get("public_key")
                    .and_then(serde_json::Value::as_str);
                let command_id = envelope.command_id.unwrap_or(envelope.message_id);

                let mut result = match crate::handlers::complete_device_verification(
                    state,
                    otp_code,
                    dev_name,
                    pub_key,
                    cloud_dev_id,
                    Some(viewer_id),
                ) {
                    Ok(pair_resp) => command_response(
                        envelope,
                        session,
                        MessageType::CommandResult,
                        serde_json::json!({
                            "command_id": command_id.to_string(),
                            "status": "completed",
                            "result": serde_json::json!({
                                "verified": true,
                                "device_token": pair_resp.device_token,
                                "device_id": pair_resp.device_id,
                                "role": pair_resp.role,
                                "trust_tier": "T3"
                            })
                        }),
                    ),
                    Err((_, err_body)) => command_response(
                        envelope,
                        session,
                        MessageType::CommandRejected,
                        serde_json::json!({
                            "status": "rejected",
                            "error": err_body.message
                        }),
                    ),
                };
                result.command_id = Some(command_id);
                return Some(result);
            }

            let command_id = envelope.command_id.unwrap_or(envelope.message_id);
            let pending = crate::cloud::PendingCommand {
                id: command_id.to_string(),
                command_type: command_type.to_string(),
                payload: command_payload
                    .get("payload")
                    .cloned()
                    .unwrap_or(serde_json::Value::Null),
            };
            let result = crate::cloud::execute_pending_command_for_session(
                state,
                &pending,
                viewer_id,
                &session.session_id,
                session.epoch,
                envelope.deadline_ms,
                is_lan,
            )
            .await;
            let mut result = command_response(
                envelope,
                session,
                MessageType::CommandResult,
                serde_json::json!({
                    "command_id": result.id,
                    "status": result.status,
                    "result": result.result,
                }),
            );
            result.command_id = Some(command_id);
            result
        }
        MessageType::Ping => {
            let mut pong = EnvelopeV2::new(
                envelope.session_id,
                envelope.epoch,
                StreamKind::Control,
                MessageType::Pong,
                session.send_sequence(),
                session.recv_sequence(),
                30_000,
            );
            pong.payload = Some(serde_json::json!({"ts": chrono::Utc::now().timestamp_millis()}));
            pong
        }
        _ => return None,
    };
    response.seq = session.send_sequence();
    Some(response)
}

fn command_response(
    request: &EnvelopeV2,
    session: &SecureSession,
    message_type: MessageType,
    payload: serde_json::Value,
) -> EnvelopeV2 {
    let mut response = EnvelopeV2::new(
        request.session_id,
        request.epoch,
        StreamKind::Control,
        message_type,
        session.send_sequence(),
        session.recv_sequence(),
        request.deadline_ms,
    );
    response.command_id = request.command_id.or(Some(request.message_id));
    response.payload = Some(payload);
    response
}

pub(crate) fn split_v2_envelope(envelope: &EnvelopeV2, max_frame_size: usize) -> Vec<EnvelopeV2> {
    let Ok(encoded) = serde_json::to_vec(envelope) else {
        return Vec::new();
    };
    if encoded.len() <= max_frame_size {
        return vec![envelope.clone()];
    }
    let Some(payload) = envelope.payload.as_ref() else {
        return Vec::new();
    };
    let Ok(payload_bytes) = serde_json::to_vec(payload) else {
        return Vec::new();
    };
    const CHUNK_BYTES: usize = 8 * 1024;
    const MAX_PAYLOAD_BYTES: usize = 4 * 1024 * 1024;
    if payload_bytes.len() > MAX_PAYLOAD_BYTES {
        warn!(
            "Dropping oversized V2 response payload ({} bytes)",
            payload_bytes.len()
        );
        let mut failure = envelope.clone();
        failure.message_type = if envelope.command_id.is_some() {
            MessageType::CommandRejected
        } else {
            MessageType::Error
        };
        failure.payload = Some(serde_json::json!({
            "status": "failed",
            "error": "Response exceeded the 4 MiB transport limit",
        }));
        return vec![failure];
    }
    let chunk_count = payload_bytes.len().div_ceil(CHUNK_BYTES);
    payload_bytes
        .chunks(CHUNK_BYTES)
        .enumerate()
        .map(|(index, data)| {
            let mut part = envelope.clone();
            part.seq = envelope.seq.saturating_add(index as u64);
            part.payload = Some(serde_json::json!({
                "_frame_chunk": true,
                "chunk_id": envelope.message_id,
                "index": index,
                "count": chunk_count,
                "data": BASE64.encode(data),
            }));
            part
        })
        .collect()
}
