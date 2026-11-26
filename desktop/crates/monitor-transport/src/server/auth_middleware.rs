use crate::auth::{TokenAuthenticator, TokenVerification};
use crate::network::is_allowed_bind_ip;
use crate::state::{AppState, AuthenticatedDevice};
use axum::{
    Json,
    extract::{ConnectInfo, Request, State},
    http::{HeaderMap, StatusCode},
    middleware::Next,
    response::Response,
};
use monitor_core::{ApiErrorResponse, ErrorCode};
use std::net::SocketAddr;
use tracing::error;

/// Middleware to validate Bearer token for protected API routes.
pub(super) async fn auth_middleware(
    State(state): State<AppState>,
    headers: HeaderMap,
    mut req: Request,
    next: Next,
) -> Result<Response, (StatusCode, Json<ApiErrorResponse>)> {
    let auth_header = headers.get("Authorization").and_then(|h| h.to_str().ok());

    let Some(header_val) = auth_header else {
        return Err((
            StatusCode::UNAUTHORIZED,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Missing 'Authorization: Bearer <token>' header.",
                false,
            )),
        ));
    };

    let Some(raw_token) = header_val.strip_prefix("Bearer ") else {
        return Err((
            StatusCode::UNAUTHORIZED,
            Json(ApiErrorResponse::new(
                ErrorCode::Unauthorized,
                "Authorization header must use Bearer scheme.",
                false,
            )),
        ));
    };

    let authenticator = TokenAuthenticator::new(state.storage.clone());
    let (device_id, role) = match authenticator.verify_token(raw_token.trim()) {
        Ok(TokenVerification::Authenticated(device_id, role)) => (device_id, role),
        Ok(TokenVerification::Revoked) => {
            return Err((
                StatusCode::UNAUTHORIZED,
                Json(ApiErrorResponse::new(
                    ErrorCode::AuthExpired,
                    "Invalid, expired, or revoked device token.",
                    false,
                )),
            ));
        }
        Ok(TokenVerification::Unknown) => {
            if let Some(pair) =
                crate::cloud::verify_remote_client_token(&state, raw_token.trim()).await
            {
                pair
            } else {
                return Err((
                    StatusCode::UNAUTHORIZED,
                    Json(ApiErrorResponse::new(
                        ErrorCode::AuthExpired,
                        "Invalid, expired, or revoked device token.",
                        false,
                    )),
                ));
            }
        }
        Err(error) => {
            error!("Local device token lookup failed: {error:#}");
            return Err((
                StatusCode::SERVICE_UNAVAILABLE,
                Json(ApiErrorResponse::new(
                    ErrorCode::InternalError,
                    "Authorization service is temporarily unavailable.",
                    true,
                )),
            ));
        }
    };

    req.extensions_mut()
        .insert(AuthenticatedDevice { device_id, role });

    let is_loopback = req
        .extensions()
        .get::<ConnectInfo<SocketAddr>>()
        .is_some_and(|peer| peer.0.ip().is_loopback());
    if !is_loopback {
        let local_allowed = state
            .storage
            .get_setting("allow_local_network")
            .unwrap_or(None)
            .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
            .unwrap_or(false);
        if !local_allowed {
            return Err((
                StatusCode::FORBIDDEN,
                Json(ApiErrorResponse::new(
                    ErrorCode::Unauthorized,
                    "Local Wi-Fi access disabled by desktop security policy.",
                    false,
                )),
            ));
        }
    }

    Ok(next.run(req).await)
}

/// Only native desktop clients may access local management. Web pages must not
/// be able to trigger pairing or register folders through cross-origin requests.
pub(super) async fn native_client_only(req: Request, next: Next) -> Result<Response, StatusCode> {
    let is_loopback = req
        .extensions()
        .get::<ConnectInfo<SocketAddr>>()
        .is_some_and(|peer| peer.0.ip().is_loopback());
    if !is_loopback
        || req.headers().contains_key("origin")
        || req.headers().contains_key("sec-fetch-site")
    {
        return Err(StatusCode::FORBIDDEN);
    }
    Ok(next.run(req).await)
}

/// A wildcard listener survives adapter changes, but still accepts only private peers.
pub(super) async fn private_peer_only(req: Request, next: Next) -> Result<Response, StatusCode> {
    let allowed = req
        .extensions()
        .get::<ConnectInfo<SocketAddr>>()
        .is_some_and(|peer| match peer.0.ip() {
            std::net::IpAddr::V4(ip) => !ip.is_unspecified() && is_allowed_bind_ip(ip),
            std::net::IpAddr::V6(ip) => ip.is_loopback(),
        });
    if !allowed {
        return Err(StatusCode::FORBIDDEN);
    }
    Ok(next.run(req).await)
}
