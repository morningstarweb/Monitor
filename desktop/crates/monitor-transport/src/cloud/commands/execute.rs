/// Executes a single pending command dispatched from Cloudflare with durable idempotency.
pub async fn execute_pending_command(state: &AppState, cmd: &PendingCommand) -> CommandResult {
    execute_pending_command_for_session(state, cmd, "cloud_broker", "cloud_v1", 1, 60_000, false)
        .await
}

/// Records the actual viewer and transport session while deduplicating command IDs globally.
/// `is_lan` describes the actual transport and is supplied by the caller; it is
/// deliberately not inferred from the trust tier, which allowed privileged
/// cloud viewers to bypass the local "cloud operations disabled" policy.
pub async fn execute_pending_command_for_session(
    state: &AppState,
    cmd: &PendingCommand,
    viewer_device_id: &str,
    session_id: &str,
    epoch: u64,
    deadline_ms: u64,
    is_lan: bool,
) -> CommandResult {
    // AUD-01 / cross-platform parity: revocation must be enforced on the cloud
    // command path too, not only on the LAN v2 session path. A revoked viewer
    // keeps its tier row, so checking tier alone is insufficient.
    if let Ok(Some(trust)) = state.storage.get_viewer_trust(viewer_device_id)
        && trust.revoked_at_utc_ms.is_some()
    {
        tracing::warn!("Rejected cloud command from revoked viewer {viewer_device_id}");
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some("Device authorization has been revoked".to_string()),
        };
    }

    // Only the documented, tray-managed key may authorize cloud operations.
    // The legacy `allow_cloud_access` alias has no writer and must not grant
    // privileges through a dead fallback (suspected risk #1/#4).
    let cloud_allowed = state
        .storage
        .get_setting("allow_cloud_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);

    if !is_lan && !cloud_allowed {
        return CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(
                "Remote cloud operations disabled by desktop security policy (Local Network Only)"
                    .to_string(),
            ),
        };
    }

    // 1. Classify operation tier and compute canonical params hash
    let tier = monitor_core::classify_operation(&cmd.command_type);
    let params_hash = monitor_core::canonical_params_hash(&cmd.command_type, &cmd.payload);

    // Durable idempotency is scoped to (viewer, request_id): a replay from the
    // same viewer returns the stored result, while another viewer's identical
    // request ID remains an independent command.
    let command_key = monitor_storage::idempotency_key(viewer_device_id, &cmd.id);
    let ledger_entry = monitor_storage::CommandLedgerEntry {
        command_id: command_key.clone(),
        viewer_device_id: viewer_device_id.to_string(),
        operation: cmd.command_type.clone(),
        canonical_params_hash: params_hash,
        session_id: session_id.to_string(),
        accepted_epoch: epoch,
        tier: tier.as_str().to_string(),
        state: monitor_storage::CommandLedgerState::Executing,
        received_at_utc_ms: chrono::Utc::now().timestamp_millis(),
        started_at_utc_ms: Some(chrono::Utc::now().timestamp_millis()),
        finished_at_utc_ms: None,
        deadline_ms,
        result_code: None,
        result_digest: None,
        result_summary: None,
    };

    // 2. Atomic admission check against durable SQLite ledger
    match state
        .storage
        .record_idempotent_command(viewer_device_id, &cmd.id, &ledger_entry)
    {
        Ok(monitor_storage::LedgerAdmission::Duplicate(prev)) => {
            tracing::info!(
                "Durable ledger deduplicated already-processed command: {}",
                cmd.id
            );
            if prev.state == monitor_storage::CommandLedgerState::Executing {
                let wait_ms = deadline_ms.clamp(1_000, 120_000).saturating_add(5_000);
                let settled = tokio::time::timeout(
                    Duration::from_millis(wait_ms),
                    async {
                        loop {
                            if let Ok(Some(entry)) = state.storage.get_command(&command_key)
                                && entry.state != monitor_storage::CommandLedgerState::Executing {
                                    break entry;
                                }
                            tokio::time::sleep(Duration::from_millis(200)).await;
                        }
                    },
                )
                .await;
                return match settled {
                    Ok(entry) => CommandResult {
                        id: cmd.id.clone(),
                        status: if entry.state == monitor_storage::CommandLedgerState::Completed {
                            "completed".to_string()
                        } else {
                            "failed".to_string()
                        },
                        result: entry.result_summary,
                    },
                    Err(_) => CommandResult {
                        id: cmd.id.clone(),
                        status: "failed".to_string(),
                        result: Some("The original command is still running; its side effect was not repeated".to_string()),
                    },
                };
            }
            return CommandResult {
                id: cmd.id.clone(),
                status: if prev.state == monitor_storage::CommandLedgerState::Completed {
                    "completed".to_string()
                } else {
                    "failed".to_string()
                },
                result: prev.result_summary,
            };
        }
        Ok(monitor_storage::LedgerAdmission::Conflict) => {
            tracing::warn!(
                "Command ID {} collision with differing payload parameters; rejected",
                cmd.id
            );
            return CommandResult {
                id: cmd.id.clone(),
                status: "failed".to_string(),
                result: Some(
                    "Command ID conflict: payload parameters do not match previously recorded command"
                        .to_string(),
                ),
            };
        }
        Ok(monitor_storage::LedgerAdmission::Admitted) => {}
        Err(e) => {
            tracing::warn!("Failed to query/insert into durable command ledger: {e}");
        }
    }

    let cmd_res = execute_pending_command_inner(state, cmd).await;

    // 3. Finalize in durable SQLite ledger
    let final_state = if cmd_res.status == "completed" {
        monitor_storage::CommandLedgerState::Completed
    } else {
        monitor_storage::CommandLedgerState::Failed
    };
    let _ = state.storage.finalize_command(
        &command_key,
        final_state,
        Some(cmd_res.status.as_str()),
        None,
        cmd_res.result.as_deref(),
    );

    cmd_res
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
mod cloud_policy_tests {
    use super::*;
    use crate::AppState;
    use monitor_core::{DeviceId, DeviceRole};
    use monitor_storage::Storage;
    use monitor_telemetry::TelemetrySampler;

    fn state_with_privileged_viewer() -> AppState {
        let storage = Storage::in_memory().unwrap();
        // Fail-closed: cloud operations disabled, local network granted.
        storage.set_setting("allow_cloud_operations", "0").unwrap();
        let state = AppState::new(
            storage,
            TelemetrySampler::new(),
            monitor_git::GitExecutor::new().unwrap(),
        );
        state
            .storage
            .insert_device(
                &DeviceId::new("privileged-cloud-viewer"),
                "Privileged viewer",
                &crate::auth::TokenAuthenticator::hash_token("tok_privileged_cloud_viewer_1234"),
                DeviceRole::Operator,
            )
            .unwrap();
        state
            .storage
            .enroll_viewer_with_tier(
                "privileged-cloud-viewer",
                "cloud",
                "privileged-cloud-viewer",
                "T2",
            )
            .unwrap();
        state
    }

    #[tokio::test]
    async fn cloud_permission_is_enforced_for_privileged_non_lan_viewers() {
        let state = state_with_privileged_viewer();
        let cmd = PendingCommand {
            id: "cloud-policy-bypass-attempt".to_string(),
            command_type: "git.status".to_string(),
            payload: serde_json::json!({}),
        };

        // A T2 viewer over a cloud session (session_id is a UUID, not "lan_")
        // must still obey the cloud-operations policy.
        let result = execute_pending_command_for_session(
            &state,
            &cmd,
            "privileged-cloud-viewer",
            "f6a1b2c3-0000-4000-8000-000000000000",
            1,
            60_000,
            false,
        )
        .await;
        assert_eq!(result.status, "failed");
        assert!(
            result
                .result
                .as_deref()
                .is_some_and(|message| message.contains("Remote cloud operations disabled")),
            "unexpected result: {:?}",
            result.result
        );
    }

    #[tokio::test]
    async fn duplicate_request_id_returns_prior_result_without_reexecution() {
        let state = state_with_privileged_viewer();
        let cmd = PendingCommand {
            id: "idempotent-request-1".to_string(),
            command_type: "telemetry.poll".to_string(),
            payload: serde_json::json!({}),
        };
        let first = execute_pending_command_for_session(
            &state,
            &cmd,
            "privileged-cloud-viewer",
            "lan-session",
            1,
            60_000,
            true,
        )
        .await;
        assert_eq!(first.status, "completed");

        let key = monitor_storage::idempotency_key("privileged-cloud-viewer", &cmd.id);
        // Replace the recorded result with a sentinel. A re-execution would
        // overwrite it; a replay must return it verbatim.
        state
            .storage
            .finalize_command(
                &key,
                monitor_storage::CommandLedgerState::Completed,
                Some("SUCCESS"),
                None,
                Some("SENTINEL_PRIOR_RESULT"),
            )
            .unwrap();

        let replay = execute_pending_command_for_session(
            &state,
            &cmd,
            "privileged-cloud-viewer",
            "lan-session",
            1,
            60_000,
            true,
        )
        .await;
        assert_eq!(replay.id, cmd.id);
        assert_eq!(replay.status, "completed");
        assert_eq!(replay.result.as_deref(), Some("SENTINEL_PRIOR_RESULT"));
        assert_eq!(
            state
                .storage
                .get_command(&key)
                .unwrap()
                .and_then(|entry| entry.result_summary),
            Some("SENTINEL_PRIOR_RESULT".to_string())
        );
    }

    #[tokio::test]
    async fn idempotency_scope_is_per_viewer() {
        let state = state_with_privileged_viewer();
        let cmd = PendingCommand {
            id: "shared-request-id".to_string(),
            command_type: "telemetry.poll".to_string(),
            payload: serde_json::json!({}),
        };
        let first = execute_pending_command_for_session(
            &state,
            &cmd,
            "privileged-cloud-viewer",
            "lan-session-1",
            1,
            60_000,
            true,
        )
        .await;
        let second = execute_pending_command_for_session(
            &state,
            &cmd,
            "another-viewer",
            "lan-session-2",
            1,
            60_000,
            true,
        )
        .await;
        assert_eq!(first.status, "completed");
        assert_eq!(second.status, "completed");
        assert!(state
            .storage
            .get_command(&monitor_storage::idempotency_key(
                "privileged-cloud-viewer",
                &cmd.id
            ))
            .unwrap()
            .is_some());
        assert!(state
            .storage
            .get_command(&monitor_storage::idempotency_key("another-viewer", &cmd.id))
            .unwrap()
            .is_some());
    }

    #[tokio::test]
    async fn lan_transport_is_allowed_to_use_privileged_viewer() {
        let state = state_with_privileged_viewer();
        let cmd = PendingCommand {
            id: "lan-policy-allowed".to_string(),
            command_type: "definitely.unsupported.operation".to_string(),
            payload: serde_json::json!({}),
        };
        let result = execute_pending_command_for_session(
            &state,
            &cmd,
            "privileged-cloud-viewer",
            "lan-session",
            1,
            60_000,
            true,
        )
        .await;
        assert!(
            !result
                .result
                .as_deref()
                .is_some_and(|message| message.contains("Remote cloud operations disabled")),
            "valid LAN session must not be blocked by the cloud policy"
        );
    }
}