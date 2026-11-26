mod handshake;
mod session_loop;

pub const LAN_V2_PROLOGUE: &[u8] = b"MONITOR_V3_2_LAN_V2";

use crate::state::AppState;
use axum::{
    extract::{State, ws::WebSocketUpgrade},
    http::StatusCode,
    response::{IntoResponse, Response},
};
use tracing::warn;

/// Upgrades an incoming LAN client to an end-to-end encrypted v2 WebSocket session.
/// No secret bearer token is passed in query parameters (DEF-06 resolution).
pub async fn websocket_stream_v2(ws: WebSocketUpgrade, State(state): State<AppState>) -> Response {
    let local_allowed = state
        .storage
        .get_setting("allow_local_network")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !local_allowed {
        warn!(
            "Local Wi-Fi WebSocket rejected: 'Local Wi-Fi Access' is turned OFF in desktop Access Permissions"
        );
        return (
            StatusCode::FORBIDDEN,
            "Local Wi-Fi access disabled by desktop security policy.",
        )
            .into_response();
    }
    ws.on_upgrade(move |socket| handshake::handle_socket_v2(socket, state))
        .into_response()
}
