mod dispatch;
mod frame_handler;
mod handshake;
mod loop_worker;
mod session;
#[cfg(test)]
mod tests;

pub(crate) use dispatch::{process_v2_envelope, split_v2_envelope};
use frame_handler::{handle_viewer_frame, queue_relay_frame};
use session::handle_host_room_session;

use crate::state::AppState;

pub const CLOUD_V2_PROLOGUE: &[u8] = b"MONITOR_V3_2_CLOUD_V2";

/// Spawns the background host control worker that connects outbound to the Cloudflare HostRoom DO.
/// Automatically handles single-use ticket acquisition, reconnects with exponential backoff,
/// and responds to viewer commands and telemetry requests with end-to-end encryption.
pub fn spawn_host_control_task(state: AppState) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        loop_worker::run_host_control_loop(state).await;
    })
}
