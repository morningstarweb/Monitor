use monitor_storage::Storage;
use monitor_telemetry::TelemetrySampler;
use monitor_transport::{AppState, build_router};
use std::net::SocketAddr;

pub fn create_test_state() -> AppState {
    let storage = Storage::in_memory().expect("in-memory storage");
    storage
        .set_setting("allow_local_network", "true")
        .expect("enable local network");
    let telemetry = TelemetrySampler::new();
    let git_executor = monitor_git::GitExecutor::new().expect("git executor");
    AppState::new(storage, telemetry, git_executor)
}

pub async fn spawn_v2_ws_server(state: AppState) -> String {
    let app = build_router(state);
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
        .await
        .expect("bind ephemeral port");
    let local_addr = listener.local_addr().expect("local addr");

    tokio::spawn(async move {
        let _ = axum::serve(
            listener,
            app.into_make_service_with_connect_info::<SocketAddr>(),
        )
        .await;
    });

    format!("ws://127.0.0.1:{}/api/v2/stream", local_addr.port())
}
