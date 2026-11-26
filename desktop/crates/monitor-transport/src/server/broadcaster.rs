use crate::state::AppState;
use std::time::Duration;
use tracing::error;

/// Spawns the background 2-second telemetry broadcaster.
pub fn spawn_telemetry_broadcast_task(state: AppState) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        // The JoinSet owns both loops, so aborting the agent also cancels its samplers.
        let mut tasks = tokio::task::JoinSet::new();
        let telemetry_state = state.clone();
        tasks.spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_secs(2));
            interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
            loop {
                interval.tick().await;
                if telemetry_state.event_tx.receiver_count() == 0
                    && telemetry_state.telemetry_bus.subscriber_count() == 0
                {
                    continue;
                }
                // PERF-01: telemetry is a latest-value stream. Publishing into
                // the shared broadcast channel queued one clone per tick per
                // slow consumer; the coalescing bus retains only the newest
                // snapshot and caps it by encoded byte size.
                let _ = telemetry_state
                    .telemetry_bus
                    .publish(telemetry_state.telemetry.sample_system_snapshot());
            }
        });
        tasks.spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_millis(1500));
            interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);

            // Pre-warm the in-memory cache immediately at startup
            let _ = crate::local_handlers::refresh_and_broadcast_repositories(&state).await;

            loop {
                interval.tick().await;
                if state.event_tx.receiver_count() == 0 {
                    continue;
                }
                let _ = crate::local_handlers::refresh_and_broadcast_repositories(&state).await;
            }
        });
        if let Some(Err(error)) = tasks.join_next().await {
            error!("Live broadcaster stopped: {error}");
        }
    })
}
