pub mod auth;
pub mod authorization;
pub mod cloud;
pub mod connection_controller;
pub mod git_handlers;
pub mod handlers;
pub mod host_control;
pub mod lan_v2;
pub mod local_handlers;
pub mod network;
pub mod server;
pub mod session_crypto;
pub mod state;
pub mod telemetry_bus;
pub mod terminal_handlers;
pub mod watchdog_handlers;
pub mod webrtc_peer;

pub use auth::TokenAuthenticator;
pub use cloud::{disconnect_cloud, spawn_cloud_heartbeat_task, trigger_google_login};
pub use host_control::spawn_host_control_task;
pub use network::{is_allowed_bind_ip, is_tailscale_ip};
pub use server::{build_local_router, build_router, run_server};
pub use session_crypto::{
    HostHandshakeResponder, MAX_PLAINTEXT_FRAME_SIZE, NOISE_PATTERN, NoiseKeypair, SecureSession,
    ViewerHandshakeInitiator,
};
pub use state::AppState;
