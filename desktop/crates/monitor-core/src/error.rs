use schemars::JsonSchema;
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Canonical machine-readable error codes across REST and WebSocket APIs.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ErrorCode {
    InvalidArgument,
    RepoNotFound,
    StaleRevision,
    OperationInProgress,
    AuthExpired,
    Unauthorized,
    RemoteUnavailable,
    ConflictUnresolved,
    DirtyWorktreeConflict,
    RepoLockedByExternalProcess,
    NeedsForceConfirmation,
    TrustRequired,
    CommittedPushFailed,
    InternalError,
}

/// Standardized API error envelope.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct ApiErrorResponse {
    pub error_code: ErrorCode,
    pub message: String,
    pub retryable: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub operation_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub correlation_id: Option<String>,
}

impl ApiErrorResponse {
    pub fn new(code: ErrorCode, message: impl Into<String>, retryable: bool) -> Self {
        Self {
            error_code: code,
            message: message.into(),
            retryable,
            operation_id: None,
            correlation_id: None,
        }
    }
}

/// Core domain error enum.
#[derive(Debug, Error)]
pub enum CoreError {
    #[error("Invalid argument: {0}")]
    InvalidArgument(String),

    #[error("Repository '{0}' not found")]
    RepoNotFound(String),

    #[error("Stale repository state (expected revision {expected}, current is {current})")]
    StaleRevision { expected: u64, current: u64 },

    #[error("Operation already in progress for repository '{0}'")]
    OperationInProgress(String),

    #[error("Authentication failed or token expired")]
    Unauthorized,

    #[error("Repository locked by external process: {0}")]
    RepoLocked(String),

    #[error("Conflict: {0}")]
    Conflict(String),

    #[error("Internal error: {0}")]
    Internal(String),
}
