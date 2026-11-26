/// Startup background reconciliation task.
/// Reconciles crash states and exits cleanly without running periodic polling.
/// Device presence is now 100% event-driven via Cloudflare HostRoom WebSocket lifecycle.
pub fn spawn_cloud_heartbeat_task(state: AppState) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        // Reconcile any in-flight commands from a prior agent crash or abrupt shutdown
        if let Err(e) = state.storage.reconcile_crash_states() {
            warn!("Failed to reconcile crash states on startup: {e}");
        }
    })
}
