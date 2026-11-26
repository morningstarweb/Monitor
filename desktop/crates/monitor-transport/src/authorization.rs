//! Cross-transport authorization (SEC-01 / SEC-02).
//!
//! Every command, regardless of the transport that carried it (LAN v2,
//! HostRoom WSS, WebRTC data channel), is authorized here against the same
//! authoritative inputs: the authenticated Noise identity, durable revocation,
//! the current trust tier/capability and the transport policy stored in
//! SQLite. Missing settings, malformed values and storage errors deny.
//!
//! Existing per-handler checks remain in place as defence in depth; this module
//! is the single, shared decision point rather than a replacement for them.

use monitor_core::{OperationTier, classify_operation};
use monitor_storage::Storage;

use crate::session_crypto::verify_static_key_pin_hex;
use crate::state::AppState;

/// The policy domain a command arrived through. The caller supplies this from
/// the transport itself; it is never inferred from the viewer's trust tier.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OperationTarget {
    LocalNetwork,
    CloudRelay,
}

impl OperationTarget {
    fn policy_setting(self) -> &'static str {
        match self {
            Self::LocalNetwork => "allow_local_network",
            Self::CloudRelay => "allow_cloud_operations",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AuthorizationDenial {
    Unauthenticated,
    IdentityMismatch,
    Revoked,
    InsufficientTier {
        granted: String,
        required: OperationTier,
    },
    /// The setting is absent, malformed, or explicitly disabled.
    PolicyDisabled(&'static str),
    /// The setting could not be read at all (durable store failure).
    StorageError(String),
}

impl AuthorizationDenial {
    pub fn message(&self) -> String {
        match self {
            Self::Unauthenticated => "No authenticated viewer identity".to_string(),
            Self::IdentityMismatch => {
                "Authenticated identity does not match enrolled key".to_string()
            }
            Self::Revoked => "Device authorization has been revoked".to_string(),
            Self::InsufficientTier { granted, required } => format!(
                "Insufficient viewer trust tier ({granted}) for operation tier ({required:?})"
            ),
            Self::PolicyDisabled(setting) => {
                format!("Operation disabled by desktop security policy ({setting})")
            }
            Self::StorageError(_) => {
                "Authorization state is unavailable; access denied (fail closed)".to_string()
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AuthorizationDecision {
    pub tier: OperationTier,
    pub target: OperationTarget,
}

fn parse_tier(raw: &str) -> Option<OperationTier> {
    match raw {
        "T0" => Some(OperationTier::T0Monitoring),
        "T1" => Some(OperationTier::T1ReadRepository),
        "T2" => Some(OperationTier::T2StateMutation),
        "T3" => Some(OperationTier::T3Dangerous),
        _ => None,
    }
}

fn require_setting(storage: &Storage, key: &'static str) -> Result<(), AuthorizationDenial> {
    let raw = storage
        .get_setting(key)
        .map_err(|e| AuthorizationDenial::StorageError(format!("{key}: {e}")))?
        .ok_or(AuthorizationDenial::PolicyDisabled(key))?;
    if raw == "1" || raw.eq_ignore_ascii_case("true") {
        Ok(())
    } else {
        Err(AuthorizationDenial::PolicyDisabled(key))
    }
}

/// Capability gates layered on top of the transport/tier checks. Each gate is
/// a tray-managed setting; absence or an unexpected value denies the command.
fn capability_setting(operation: &str) -> Option<&'static str> {
    if operation == "process.kill"
        || operation == "watchdog.kill"
        || operation == "git.merge_pr"
        || operation == "merge_pr"
        || operation.contains(".delete")
        || operation == "git.hard_reset"
    {
        Some("allow_delete_operations")
    } else if operation == "watchdog.checkpoint_rollback" {
        Some("allow_checkpoint_rollback")
    } else if operation.starts_with("terminal.") {
        Some("allow_remote_terminal")
    } else if operation.starts_with("git.") {
        Some("allow_git_operations")
    } else {
        None
    }
}

/// Authorizes a single operation. `viewer_id` must be the identity proven by
/// the Noise handshake (hex-encoded static public key on LAN v2 and HostRoom).
pub fn authorize_operation(
    state: &AppState,
    viewer_id: &str,
    operation: &str,
    target: OperationTarget,
) -> Result<AuthorizationDecision, AuthorizationDenial> {
    // 1. Authenticated identity: never authorize an empty/synthetic caller.
    if viewer_id.is_empty() || viewer_id.chars().any(char::is_control) {
        return Err(AuthorizationDenial::Unauthenticated);
    }

    // 2. In-memory revocation closes the gap until the durable poll sees it.
    if state.is_revoked_locally(viewer_id) {
        return Err(AuthorizationDenial::Revoked);
    }

    // 3. Durable trust state. Any DB failure denies (fail closed).
    let trust = state
        .storage
        .get_viewer_trust(viewer_id)
        .map_err(|e| AuthorizationDenial::StorageError(e.to_string()))?
        .ok_or(AuthorizationDenial::Unauthenticated)?;
    if trust.revoked_at_utc_ms.is_some() {
        return Err(AuthorizationDenial::Revoked);
    }

    // 4. Device identity pin: when the enrolled key is a full X25519 key, the
    // authenticated static key must match it exactly (constant-time).
    if trust.public_key.len() == 64 && !verify_static_key_pin_hex(viewer_id, &trust.public_key) {
        return Err(AuthorizationDenial::IdentityMismatch);
    }

    // 5. Current tier/capability. Unknown tiers never grant privilege.
    let required = classify_operation(operation);
    let granted_tier =
        parse_tier(&trust.trust_tier).ok_or_else(|| AuthorizationDenial::InsufficientTier {
            granted: trust.trust_tier.clone(),
            required,
        })?;
    if required > granted_tier {
        return Err(AuthorizationDenial::InsufficientTier {
            granted: trust.trust_tier.clone(),
            required,
        });
    }

    // 6. Transport policy and capability gates.
    require_setting(&state.storage, target.policy_setting())?;
    if let Some(key) = capability_setting(operation) {
        require_setting(&state.storage, key)?;
    }

    Ok(AuthorizationDecision {
        tier: required,
        target,
    })
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
mod tests {
    use super::*;
    use monitor_storage::Storage;
    use monitor_telemetry::TelemetrySampler;

    const VIEWER: &str = "1111111111111111111111111111111111111111111111111111111111111111";
    const OTHER_VIEWER: &str = "2222222222222222222222222222222222222222222222222222222222222222";

    fn state_with_viewer(tier: &str) -> AppState {
        let storage = Storage::in_memory().expect("in-memory storage");
        storage
            .set_setting("allow_local_network", "1")
            .expect("LAN policy");
        storage
            .set_setting("allow_cloud_operations", "1")
            .expect("cloud policy");
        storage
            .set_setting("allow_git_operations", "1")
            .expect("git policy");
        storage
            .set_setting("allow_remote_terminal", "1")
            .expect("terminal policy");
        storage
            .set_setting("allow_delete_operations", "1")
            .expect("delete policy");
        storage
            .set_setting("allow_checkpoint_rollback", "1")
            .expect("rollback policy");
        storage
            .enroll_viewer_with_tier(VIEWER, "user-1", VIEWER, tier)
            .expect("enroll viewer");
        AppState::new(
            storage,
            TelemetrySampler::new(),
            monitor_git::GitExecutor::new().expect("git executor"),
        )
    }

    #[test]
    fn t0_viewer_is_denied_privileged_operations() {
        let state = state_with_viewer("T0");
        assert_eq!(
            authorize_operation(
                &state,
                VIEWER,
                "telemetry.poll",
                OperationTarget::LocalNetwork
            ),
            Ok(AuthorizationDecision {
                tier: OperationTier::T0Monitoring,
                target: OperationTarget::LocalNetwork,
            })
        );
        let denial =
            authorize_operation(&state, VIEWER, "git.stage", OperationTarget::LocalNetwork)
                .expect_err("T0 must not stage files");
        assert!(matches!(
            denial,
            AuthorizationDenial::InsufficientTier {
                required: OperationTier::T2StateMutation,
                ..
            }
        ));
        assert!(
            authorize_operation(&state, VIEWER, "process.kill", OperationTarget::CloudRelay)
                .is_err()
        );
    }

    #[test]
    fn durable_and_in_memory_revocations_are_denied() {
        let state = state_with_viewer("T3");
        assert!(
            authorize_operation(&state, VIEWER, "git.stage", OperationTarget::LocalNetwork).is_ok()
        );

        state.storage.revoke_viewer(VIEWER).expect("revoke");
        assert_eq!(
            authorize_operation(&state, VIEWER, "git.status", OperationTarget::LocalNetwork),
            Err(AuthorizationDenial::Revoked)
        );

        let state2 = state_with_viewer("T3");
        state2.notify_revoked(VIEWER);
        assert_eq!(
            authorize_operation(
                &state2,
                VIEWER,
                "telemetry.poll",
                OperationTarget::CloudRelay
            ),
            Err(AuthorizationDenial::Revoked)
        );
    }

    #[test]
    fn missing_or_malformed_settings_deny() {
        let state = state_with_viewer("T3");
        state
            .storage
            .delete_setting("allow_local_network")
            .expect("delete setting");
        assert_eq!(
            authorize_operation(
                &state,
                VIEWER,
                "telemetry.poll",
                OperationTarget::LocalNetwork
            ),
            Err(AuthorizationDenial::PolicyDisabled("allow_local_network"))
        );

        let other = state_with_viewer("T3");
        other
            .storage
            .set_setting("allow_cloud_operations", "yes")
            .expect("bad value");
        assert_eq!(
            authorize_operation(
                &other,
                VIEWER,
                "telemetry.poll",
                OperationTarget::CloudRelay
            ),
            Err(AuthorizationDenial::PolicyDisabled(
                "allow_cloud_operations"
            ))
        );

        let no_capability = state_with_viewer("T3");
        no_capability
            .storage
            .set_setting("allow_git_operations", "0")
            .expect("disable git");
        assert_eq!(
            authorize_operation(
                &no_capability,
                VIEWER,
                "git.stage",
                OperationTarget::LocalNetwork
            ),
            Err(AuthorizationDenial::PolicyDisabled("allow_git_operations"))
        );
    }

    #[test]
    fn t2_and_t3_viewers_are_allowed_within_policy() {
        let t2 = state_with_viewer("T2");
        assert!(
            authorize_operation(&t2, VIEWER, "git.stage", OperationTarget::LocalNetwork).is_ok()
        );
        assert_eq!(
            authorize_operation(&t2, VIEWER, "process.kill", OperationTarget::CloudRelay),
            Err(AuthorizationDenial::InsufficientTier {
                granted: "T2".to_string(),
                required: OperationTier::T3Dangerous,
            })
        );

        let t3 = state_with_viewer("T3");
        assert!(
            authorize_operation(&t3, VIEWER, "git.stage", OperationTarget::LocalNetwork).is_ok()
        );
        assert!(
            authorize_operation(&t3, VIEWER, "process.kill", OperationTarget::CloudRelay).is_ok()
        );
    }

    #[test]
    fn unknown_or_mismatched_identities_are_denied() {
        let state = state_with_viewer("T3");
        assert_eq!(
            authorize_operation(&state, "", "telemetry.poll", OperationTarget::LocalNetwork),
            Err(AuthorizationDenial::Unauthenticated)
        );
        assert_eq!(
            authorize_operation(
                &state,
                OTHER_VIEWER,
                "telemetry.poll",
                OperationTarget::LocalNetwork
            ),
            Err(AuthorizationDenial::Unauthenticated)
        );

        state
            .storage
            .enroll_viewer_with_tier(OTHER_VIEWER, "user-2", VIEWER, "T3")
            .expect("enroll pinned mismatch");
        assert_eq!(
            authorize_operation(
                &state,
                OTHER_VIEWER,
                "telemetry.poll",
                OperationTarget::LocalNetwork
            ),
            Err(AuthorizationDenial::IdentityMismatch)
        );
    }
}
