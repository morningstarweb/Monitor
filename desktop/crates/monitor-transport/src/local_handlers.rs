//! Desktop management routes. Middleware restricts these to native loopback peers.
use crate::state::AppState;
use axum::{
    Json,
    extract::{Path, State},
    http::StatusCode,
};
use monitor_core::{
    ApiErrorResponse, DeviceId, DeviceRole, ErrorCode, FileChangeCategory, GitWorkspaceSnapshot,
    RepoId, RepoSummary, Revision, ServerEvent, SystemSnapshot, TrustProfile,
};
use monitor_storage::{StoredDevice, StoredRepository};
use serde::{Deserialize, Serialize};
use std::{
    path::PathBuf,
    time::{Duration, Instant},
};
use tracing::info;

type ApiFailure = (StatusCode, Json<ApiErrorResponse>);

fn failure(status: StatusCode, message: impl Into<String>) -> ApiFailure {
    (
        status,
        Json(ApiErrorResponse::new(
            if status.is_client_error() {
                ErrorCode::InvalidArgument
            } else {
                ErrorCode::InternalError
            },
            message,
            status.is_server_error(),
        )),
    )
}

include!("local_handlers/status.rs");
include!("local_handlers/access.rs");
include!("local_handlers/cloud_auth.rs");
include!("local_handlers/project_summaries.rs");
include!("local_handlers/project_registration.rs");
include!("local_handlers/project_management.rs");
