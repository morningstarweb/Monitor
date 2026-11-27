use crate::auth::TokenAuthenticator;
use crate::state::AppState;
use axum::{
    extract::State,
    http::StatusCode,
    response::{IntoResponse, Json},
};
use monitor_core::{
    ApiErrorResponse, DeviceId, DeviceRole, ErrorCode, HealthResponse, PairingRequest,
    PairingResponse,
    brand_config::{APP_NAME, APP_VERSION},
};
use tracing::{info, warn};

#[derive(Debug, serde::Deserialize)]
pub struct PairHttpPayload {
    #[serde(flatten)]
    pub core: PairingRequest,
    #[serde(default)]
    pub cloud_device_id: Option<String>,
}

/// `GET /api/v1/health`
pub async fn health() -> impl IntoResponse {
    Json(HealthResponse {
        app_name: APP_NAME.to_string(),
        version: APP_VERSION.to_string(),
        auth_required: true,
    })
}

/// `POST /api/v1/auth/request-code`
pub async fn request_pairing_code(State(state): State<AppState>) -> impl IntoResponse {
    let code = state.generate_pairing_code();
    info!("Created a desktop pairing session valid for 180 seconds");
    Json(serde_json::json!({
        "pairing_code": code,
        "expires_in_seconds": 180
    }))
}

/// Shared OTP / QR verification completion helper used by both LAN HTTP `/auth/pair`
/// and E2EE Protocol v2 `auth.verify_otp` (cloud/LAN).
pub(crate) fn complete_device_verification(
    state: &AppState,
    pairing_code: &str,
    device_name: &str,
    public_key: Option<&str>,
    cloud_device_id: Option<&str>,
    authenticated_viewer_id: Option<&str>,
) -> Result<PairingResponse, (StatusCode, ApiErrorResponse)> {
    let trimmed_name = device_name.trim();
    if trimmed_name.is_empty() || device_name.len() > 128 {
        return Err((
            StatusCode::BAD_REQUEST,
            ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                "Enter a device name of 1 to 128 characters.",
                false,
            ),
        ));
    }

    if let Some(pk) = public_key
        && (pk.len() != 64 || !pk.bytes().all(|b| b.is_ascii_hexdigit()))
    {
        return Err((
            StatusCode::BAD_REQUEST,
            ApiErrorResponse::new(
                ErrorCode::InvalidArgument,
                "Invalid Noise public_key: expected 64 hex characters.",
                false,
            ),
        ));
    }

    let pk_lower = public_key
        .map(str::to_ascii_lowercase)
        .or_else(|| authenticated_viewer_id.map(str::to_ascii_lowercase));
    let cloud_id_clean = cloud_device_id
        .map(str::trim)
        .filter(|s| !s.is_empty() && s.len() <= 128)
        .map(str::to_string);

    let mut candidate_storage: Vec<String> = Vec::new();
    if let Some(ref cid) = cloud_id_clean {
        candidate_storage.push(cid.clone());
        if let Ok(Some(linked_pk)) = state
            .storage
            .get_setting(&format!("cloud_device_link:{cid}"))
        {
            candidate_storage.push(linked_pk);
        }
    }
    if let Some(ref pk) = pk_lower {
        candidate_storage.push(pk.clone());
        if let Ok(Some(linked_cid)) = state.storage.get_setting(&format!("device_cloud_id:{pk}")) {
            candidate_storage.push(linked_cid);
        }
    }
    let candidate_ids: Vec<&str> = candidate_storage.iter().map(String::as_str).collect();

    let matched_target = match state.verify_otp_submission(pairing_code, &candidate_ids) {
        Ok(m) => m,
        Err(crate::state::OtpVerifyError::NoActiveSession) => {
            return Err((
                StatusCode::BAD_REQUEST,
                ApiErrorResponse::new(
                    ErrorCode::Unauthorized,
                    "No OTP session active on desktop. Click your device or 'QR Code' in Desktop Tray first.",
                    false,
                ),
            ));
        }
        Err(crate::state::OtpVerifyError::Expired) => {
            return Err((
                StatusCode::BAD_REQUEST,
                ApiErrorResponse::new(
                    ErrorCode::AuthExpired,
                    "OTP expired. Generate a new code in Desktop Tray.",
                    false,
                ),
            ));
        }
        Err(crate::state::OtpVerifyError::TooManyAttempts) => {
            return Err((
                StatusCode::TOO_MANY_REQUESTS,
                ApiErrorResponse::new(
                    ErrorCode::Unauthorized,
                    "Too many failed OTP attempts. Session invalidated.",
                    false,
                ),
            ));
        }
        Err(crate::state::OtpVerifyError::InvalidCode) => {
            return Err((
                StatusCode::UNAUTHORIZED,
                ApiErrorResponse::new(ErrorCode::Unauthorized, "Incorrect OTP code.", true),
            ));
        }
    };

    let token_entropy: [u8; 32] = rand::random();
    let raw_token = format!(
        "mon_dev_{}",
        token_entropy
            .iter()
            .map(|b| format!("{b:02x}"))
            .collect::<String>()
    );
    let token_hash = TokenAuthenticator::hash_token(&raw_token);
    let role = DeviceRole::Operator;

    let canonical_id = pk_lower
        .clone()
        .unwrap_or_else(|| format!("dev_{}", uuid::Uuid::new_v4().simple()));
    let device_id = DeviceId::new(&canonical_id);
    let final_display_name = matched_target
        .as_ref()
        .map(|(_, matched_name)| {
            if trimmed_name.eq_ignore_ascii_case("Android Phone") && !matched_name.is_empty() {
                matched_name.as_str()
            } else {
                trimmed_name
            }
        })
        .unwrap_or(trimmed_name);

    if let Err(e) = state.storage.set_verified_device_credential(
        &device_id,
        final_display_name,
        &token_hash,
        role,
    ) {
        warn!("Failed to store verified device: {}", e);
        return Err((
            StatusCode::INTERNAL_SERVER_ERROR,
            ApiErrorResponse::new(
                ErrorCode::InternalError,
                "Database error while saving device verification.",
                false,
            ),
        ));
    }

    let promote_identity = |identity: &str| {
        state.unrevoke_device(identity);
        crate::local_handlers::clear_device_dismissed(&state.storage, identity);
        let _ = state
            .storage
            .enroll_viewer_with_tier(identity, final_display_name, identity, "T3");
        let _ = state.storage.reauthorize_viewer(identity, "T3");
        let _ = state.storage.approve_viewer_tier(identity, "T3");
    };

    promote_identity(device_id.as_str());
    if let Some(ref pk) = pk_lower
        && pk != device_id.as_str()
    {
        promote_identity(pk);
    }
    if let Some((ref matched_id, _)) = matched_target {
        if matched_id.starts_with("android-") {
            if let Some(ref pk) = pk_lower {
                let _ = state
                    .storage
                    .set_setting(&format!("cloud_device_link:{matched_id}"), pk);
                let _ = state
                    .storage
                    .set_setting(&format!("device_cloud_id:{pk}"), matched_id);
            } else {
                promote_identity(matched_id);
            }
        } else if matched_id != device_id.as_str() {
            promote_identity(matched_id);
        }
    }
    if let Some(ref cid) = cloud_id_clean {
        if let Some(ref pk) = pk_lower {
            let _ = state
                .storage
                .set_setting(&format!("cloud_device_link:{cid}"), pk);
            let _ = state
                .storage
                .set_setting(&format!("device_cloud_id:{pk}"), cid);
        } else {
            promote_identity(cid);
        }
    }

    info!(
        "Verified mobile device via OTP/QR: {} ({}) -> T3 Admin",
        final_display_name, device_id
    );

    Ok(PairingResponse {
        device_token: raw_token,
        device_id,
        role,
    })
}

/// `POST /api/v1/auth/pair`
pub async fn pair(
    State(state): State<AppState>,
    Json(payload): Json<PairHttpPayload>,
) -> Result<Json<PairingResponse>, (StatusCode, Json<ApiErrorResponse>)> {
    complete_device_verification(
        &state,
        &payload.core.pairing_code,
        &payload.core.device_name,
        payload.core.public_key.as_deref(),
        payload.cloud_device_id.as_deref(),
        None,
    )
    .map(Json)
    .map_err(|(status, err)| (status, Json(err)))
}
