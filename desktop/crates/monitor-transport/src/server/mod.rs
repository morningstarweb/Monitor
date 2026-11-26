mod auth_middleware;
mod broadcaster;
mod routes;

use crate::network::is_allowed_bind_ip;
use crate::state::AppState;
use axum::middleware;
use std::net::{Ipv4Addr, SocketAddr};
use tracing::{error, info};

pub use broadcaster::spawn_telemetry_broadcast_task;
pub use routes::{build_local_router, build_router};

/// Binds and runs the server on an approved IP address.
pub async fn run_server(
    bind_ip: Ipv4Addr,
    port: u16,
    state: AppState,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    if !is_allowed_bind_ip(bind_ip) {
        error!(
            "Refusing to bind to non-private / non-loopback IP: {}",
            bind_ip
        );
        return Err(format!(
            "Binding to {} is strictly prohibited by security policy",
            bind_ip
        )
        .into());
    }

    let app = if bind_ip.is_loopback() || bind_ip.is_unspecified() {
        build_local_router(state.clone())
    } else {
        build_router(state.clone())
    }
    .layer(middleware::from_fn(auth_middleware::private_peer_only));
    let addr = SocketAddr::from((bind_ip, port));
    info!(
        "Monitor API & WebSocket server listening on http://{}",
        addr
    );

    use axum::serve::{Listener, ListenerExt};

    let listener = tokio::net::TcpListener::bind(addr)
        .await?
        .tap_io(|tcp_stream| {
            let _ = tcp_stream.set_nodelay(true);
        });
    if let Ok(local_addr) = listener.local_addr() {
        state.set_listen_port(local_addr.port());
    }
    {
        let mut addresses = state
            .addresses
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let address = format!("http://{addr}");
        if !bind_ip.is_unspecified() && !addresses.contains(&address) {
            addresses.push(address);
        }
    }
    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .await?;

    Ok(())
}
