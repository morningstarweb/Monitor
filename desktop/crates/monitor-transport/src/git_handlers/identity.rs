#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GitIdentityPayload {
    pub name: String,
    pub email: String,
    pub is_configured: bool,
}

#[derive(Debug, Deserialize)]
pub struct SetGitIdentityRequest {
    pub name: String,
    pub email: String,
}

/// `GET /api/v1/git/identity` - Queries global git user.name and user.email.
pub async fn get_git_identity(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
) -> Result<Json<GitIdentityPayload>, (StatusCode, Json<ApiErrorResponse>)> {
    if device.role == DeviceRole::Viewer {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Insufficient permissions: Viewer role cannot read Git identity configuration",
                false,
            )),
        ));
    }
    let name_out = state
        .git_executor
        .create_command(std::path::Path::new("."))
        .args(["config", "--global", "--get", "user.name"])
        .output()
        .await;

    let email_out = state
        .git_executor
        .create_command(std::path::Path::new("."))
        .args(["config", "--global", "--get", "user.email"])
        .output()
        .await;

    let name = match name_out {
        Ok(out) if out.status.success() => String::from_utf8_lossy(&out.stdout).trim().to_string(),
        _ => String::new(),
    };

    let email = match email_out {
        Ok(out) if out.status.success() => String::from_utf8_lossy(&out.stdout).trim().to_string(),
        _ => String::new(),
    };

    let is_configured = !name.is_empty() && !email.is_empty();

    Ok(Json(GitIdentityPayload {
        name,
        email,
        is_configured,
    }))
}

/// `POST /api/v1/git/identity` - Sets global git user.name and user.email from mobile.
pub async fn set_git_identity(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    Json(payload): Json<SetGitIdentityRequest>,
) -> Result<Json<GitIdentityPayload>, (StatusCode, Json<ApiErrorResponse>)> {
    if device.role == DeviceRole::Viewer {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Insufficient permissions: Viewer role cannot modify Git identity",
                false,
            )),
        ));
    }
    let git_allowed = state
        .storage
        .get_setting("allow_git_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !git_allowed {
        return Err((
            StatusCode::FORBIDDEN,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Git operations are disabled by desktop tray policy.",
                false,
            )),
        ));
    }
    let trimmed_name = payload.name.trim();
    let trimmed_email = payload.email.trim();

    if trimmed_name.is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                "Name cannot be empty".to_string(),
                false,
            )),
        ));
    }

    if trimmed_email.is_empty() || !trimmed_email.contains('@') {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                "A valid email address is required".to_string(),
                false,
            )),
        ));
    }

    let _ = state
        .git_executor
        .create_command(std::path::Path::new("."))
        .args(["config", "--global", "user.name", trimmed_name])
        .output()
        .await
        .map_err(|e| {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(ApiErrorResponse::new(
                    ErrorCode::InternalError,
                    format!("Failed to set git user.name: {e}"),
                    true,
                )),
            )
        })?;

    let _ = state
        .git_executor
        .create_command(std::path::Path::new("."))
        .args(["config", "--global", "user.email", trimmed_email])
        .output()
        .await
        .map_err(|e| {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(ApiErrorResponse::new(
                    ErrorCode::InternalError,
                    format!("Failed to set git user.email: {e}"),
                    true,
                )),
            )
        })?;

    Ok(Json(GitIdentityPayload {
        name: trimmed_name.to_string(),
        email: trimmed_email.to_string(),
        is_configured: true,
    }))
}
