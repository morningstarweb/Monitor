use schemars::JsonSchema;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use uuid::Uuid;

pub const PROTOCOL_VERSION_2: u32 = 2;

/// Logical stream categories for multiplexing
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum StreamKind {
    Control,
    Telemetry,
    Bulk,
}

/// Typed message operation types for Protocol v2
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, JsonSchema)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum MessageType {
    // Control stream
    Hello,
    HelloAck,
    Capabilities,
    Ping,
    Pong,
    SessionClose,
    Error,
    Credit,

    // Telemetry stream
    Snapshot,
    Delta,
    ResyncRequest,
    WatchdogAlert,

    // Workspace & Bulk stream
    ProjectsRequest,
    ProjectsResult,
    GitGraphRequest,
    GitGraphResult,
    GitDiffRequest,
    GitDiffChunk,
    GitDiffEnd,

    // Command stream
    CommandRequest,
    CommandAccepted,
    CommandRejected,
    CommandProgress,
    CommandResult,
    CommandStatusRequest,
    CommandStatusResult,

    // Session & Path negotiation
    PathPrepare,
    PathCommit,
}

/// Host-enforced permission and trust tiers
#[derive(
    Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize, JsonSchema,
)]
pub enum OperationTier {
    /// Read-only telemetry: CPU, RAM, physical disks, basic process list
    T0Monitoring = 0,
    /// Read-only repository: Git branch lists, topological commit DAG, unified diffs
    T1ReadRepository = 1,
    /// State mutation: Stage, unstage, safe recoverable operations
    T2StateMutation = 2,
    /// Dangerous mutation: Process kill, branch delete, hard reset, PR merge, terminal execute
    T3Dangerous = 3,
}

impl OperationTier {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::T0Monitoring => "T0",
            Self::T1ReadRepository => "T1",
            Self::T2StateMutation => "T2",
            Self::T3Dangerous => "T3",
        }
    }
}

/// Transport-neutral envelope for Monitor Protocol v2
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct EnvelopeV2 {
    pub protocol_version: u32,
    pub session_id: Uuid,
    pub message_id: Uuid,
    pub epoch: u64,
    pub stream: StreamKind,
    #[serde(rename = "type")]
    pub message_type: MessageType,
    pub seq: u64,
    pub ack: u64,
    pub deadline_ms: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub command_id: Option<Uuid>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub payload: Option<serde_json::Value>,
}

impl EnvelopeV2 {
    pub fn new(
        session_id: Uuid,
        epoch: u64,
        stream: StreamKind,
        message_type: MessageType,
        seq: u64,
        ack: u64,
        deadline_ms: u64,
    ) -> Self {
        Self {
            protocol_version: PROTOCOL_VERSION_2,
            session_id,
            message_id: Uuid::new_v4(),
            epoch,
            stream,
            message_type,
            seq,
            ack,
            deadline_ms,
            command_id: None,
            payload: None,
        }
    }

    pub fn with_command(mut self, command_id: Uuid, payload: serde_json::Value) -> Self {
        self.command_id = Some(command_id);
        self.payload = Some(payload);
        self
    }
}

/// Classifies any given command operation name into its required OperationTier
pub fn classify_operation(operation: &str) -> OperationTier {
    match operation {
        "telemetry.poll" | "telemetry.subscribe" | "process.list" | "auth.verify_otp" => {
            OperationTier::T0Monitoring
        }

        "git.status"
        | "git.graph"
        | "git.diff"
        | "git.commit_files"
        | "git.branches"
        | "git.workspace"
        | "git.pull_requests"
        | "git.identity.get"
        | "projects.list"
        | "permissions.get"
        | "watchdog.sessions"
        | "watchdog.alerts"
        | "watchdog.checkpoints"
        | "terminal.setting.get" => OperationTier::T1ReadRepository,

        "git.stage"
        | "git.unstage"
        | "git.discard_unstaged"
        | "git.create_branch"
        | "git.switch_branch"
        | "git.fetch"
        | "git.pull"
        | "git.push"
        | "git.merge_branch"
        | "git.merge_all_branches"
        | "git.identity.set"
        | "watchdog.ack"
        | "watchdog.checkpoint_create"
        | "terminal.setting.set"
        | "watchdog.snooze"
        | "watchdog.checkpoint" => OperationTier::T2StateMutation,

        // Fail-safe: explicit dangerous operations ("process.kill", "git.hard_reset",
        // "git.merge_pr", "merge_pr", "watchdog.kill") and any unrecognized operation
        // default to the highest security tier (T3).
        _ => OperationTier::T3Dangerous,
    }
}

/// Calculates canonical SHA-256 digest of operation name and args for durable ledger deduplication
pub fn canonical_params_hash(operation: &str, args: &serde_json::Value) -> String {
    let mut hasher = Sha256::new();
    hasher.update(b"monitor-v2/op:");
    hasher.update(operation.as_bytes());
    hasher.update(b":params:");
    if let Ok(serialized) = serde_json::to_vec(args) {
        hasher.update(&serialized);
    }
    let digest = hasher.finalize();
    let mut out = String::with_capacity(64);
    for byte in digest {
        use std::fmt::Write;
        let _ = write!(out, "{byte:02x}");
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_envelope_creation_and_classification() {
        let session_id = Uuid::new_v4();
        let cmd_id = Uuid::new_v4();
        let envelope = EnvelopeV2::new(
            session_id,
            1,
            StreamKind::Control,
            MessageType::CommandRequest,
            100,
            99,
            5000,
        )
        .with_command(cmd_id, serde_json::json!({"repo_id": "test"}));

        assert_eq!(envelope.protocol_version, 2);
        assert_eq!(envelope.command_id, Some(cmd_id));

        assert_eq!(
            classify_operation("telemetry.poll"),
            OperationTier::T0Monitoring
        );
        assert_eq!(
            classify_operation("git.status"),
            OperationTier::T1ReadRepository
        );
        assert_eq!(
            classify_operation("git.stage"),
            OperationTier::T2StateMutation
        );
        assert_eq!(
            classify_operation("process.kill"),
            OperationTier::T3Dangerous
        );
        assert_eq!(
            classify_operation("git.hard_reset"),
            OperationTier::T3Dangerous
        );
        assert_eq!(
            classify_operation("watchdog.kill"),
            OperationTier::T3Dangerous
        );
        assert_eq!(
            classify_operation("git.merge_pr"),
            OperationTier::T3Dangerous
        );
        assert_eq!(classify_operation("merge_pr"), OperationTier::T3Dangerous);
        assert_eq!(classify_operation("unknown_op"), OperationTier::T3Dangerous);

        let hash1 = canonical_params_hash("git.status", &serde_json::json!({"branch": "main"}));
        let hash2 = canonical_params_hash("git.status", &serde_json::json!({"branch": "main"}));
        let hash3 = canonical_params_hash("git.status", &serde_json::json!({"branch": "dev"}));
        assert_eq!(hash1, hash2);
        assert_ne!(hash1, hash3);
    }
}
