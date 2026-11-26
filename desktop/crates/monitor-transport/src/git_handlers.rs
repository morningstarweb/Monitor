use crate::state::{AppState, AuthenticatedDevice};
use axum::{
    Extension,
    extract::{Path as AxumPath, Query, State},
    http::StatusCode,
    response::Json,
};
use monitor_core::{
    ApiErrorResponse, BranchInfo, DeviceRole, ErrorCode, FileDiffResponse, GitFileChange,
    GitGraphPage, GitOperationRequest, GitOperationResult, GitWorkspaceSnapshot, MergePrRequest,
    MergePrResponse, PullRequestSummary, RepoId, Revision, ServerEvent,
};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
include!("git_handlers/workspace.rs");
include!("git_handlers/operations.rs");
include!("git_handlers/pull_requests.rs");
include!("git_handlers/identity.rs");
