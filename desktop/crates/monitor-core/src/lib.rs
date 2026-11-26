pub mod brand_config;
pub mod error;
pub mod events;
pub mod git;
pub mod protocol_v2;
pub mod telemetry;
pub mod terminal;
pub mod types;
pub mod watchdog;

// Re-exports for ergonomics
pub use error::{ApiErrorResponse, CoreError, ErrorCode};
pub use events::{
    HealthResponse, PairingRequest, PairingResponse, ProcessKillResponse, ServerEvent,
    WsTicketResponse,
};
pub use git::{
    BranchInfo, CommitNode, DiffHunk, DiffLine, DiffLineType, FileChangeCategory, FileDiffResponse,
    GitFileChange, GitGraphPage, GitOperation, GitOperationRequest, GitOperationResult,
    GitWorkspaceSnapshot, GraphEdge, GraphEdgeType, MergePrRequest, MergePrResponse,
    OperationStatus, PullRequestSummary, RepoSummary, TrustProfile,
};
pub use protocol_v2::{
    EnvelopeV2, MessageType, OperationTier, PROTOCOL_VERSION_2, StreamKind, canonical_params_hash,
    classify_operation,
};
pub use telemetry::{
    AcPowerStatus, BatterySnapshot, ChargingState, CoolingSnapshot, CpuSnapshot, DiskSnapshot,
    MemorySnapshot, ProcessSnapshot, SupportLevel, SystemSnapshot,
};
pub use terminal::{
    TerminalCommandRequest, TerminalCommandResult, TerminalSettingPayload, TerminalStopRequest,
    TerminalStopResult,
};
pub use types::{
    CloudAccount, CloudAccountSummary, DeviceId, DeviceRole, DeviceTokenHash, OperationId,
    PairingCode, RepoId, Revision,
};
pub use watchdog::{
    AgentProfileConfig, AgentSessionSummary, AlertItem, AlertRuleConfig, CheckpointSummary,
    CreateCheckpointRequest, RollbackResponse, SnoozeAlertRequest,
};
