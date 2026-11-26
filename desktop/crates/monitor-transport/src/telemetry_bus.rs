//! Latest-value telemetry distribution (PERF-01).
//!
//! Telemetry ticks are snapshots: only the newest one is useful to a client.
//! Publishing every 2-second tick into the general `broadcast::Sender` queues
//! one clone per consumer per tick and lets a slow socket grow memory without
//! bound. This bus keeps a single, byte-bounded latest value instead. Discrete
//! events (alerts, project/workspace changes) stay on the existing broadcast
//! channel and are unaffected.
//!
//! Integration points:
//!   * `server::spawn_telemetry_broadcast_task` publishes to this bus.
//!   * `handlers::handle_socket` and `lan_v2::handle_socket_v2` subscribe and
//!     send the latest snapshot when the client can accept it.
//!   * `host_control` / `webrtc_peer` sample directly for their data-channel
//!     cadence and can opt into `latest()` to avoid a second sampler read.

use monitor_core::{ServerEvent, SystemSnapshot};
use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};
use tokio::sync::watch;

/// Default ceiling for the single retained telemetry value (~256 KiB).
pub const DEFAULT_MAX_TELEMETRY_BYTES: usize = 256 * 1024;

#[derive(Debug, Clone)]
pub struct TelemetryBus {
    tx: watch::Sender<Option<Arc<SystemSnapshot>>>,
    local_rx: watch::Receiver<Option<Arc<SystemSnapshot>>>,
    bytes: Arc<AtomicUsize>,
    max_bytes: usize,
}

#[derive(Debug, Clone)]
pub struct TelemetryReceiver {
    rx: watch::Receiver<Option<Arc<SystemSnapshot>>>,
}

impl TelemetryBus {
    pub fn new(max_bytes: usize) -> Self {
        let (tx, local_rx) = watch::channel(None);
        Self {
            tx,
            local_rx,
            bytes: Arc::new(AtomicUsize::new(0)),
            max_bytes,
        }
    }

    /// Replaces the latest telemetry value. Returns false when the snapshot is
    /// too large to retain; in that case the slot is cleared (fail closed).
    pub fn publish(&self, snapshot: SystemSnapshot) -> bool {
        let Ok(encoded) = serde_json::to_vec(&snapshot) else {
            self.clear();
            return false;
        };
        if encoded.len() > self.max_bytes {
            self.clear();
            return false;
        }
        self.bytes.store(encoded.len(), Ordering::Release);
        self.tx.send_replace(Some(Arc::new(snapshot)));
        true
    }

    pub fn subscribe(&self) -> TelemetryReceiver {
        TelemetryReceiver {
            rx: self.tx.subscribe(),
        }
    }

    pub fn latest(&self) -> Option<Arc<SystemSnapshot>> {
        self.local_rx.borrow().clone()
    }

    /// Bytes currently retained by the coalescing slot (never exceeds the cap).
    pub fn buffered_bytes(&self) -> usize {
        self.bytes.load(Ordering::Acquire)
    }

    pub fn max_bytes(&self) -> usize {
        self.max_bytes
    }

    /// Active consumers (the bus's own internal receiver is not counted).
    pub fn subscriber_count(&self) -> usize {
        self.tx.receiver_count().saturating_sub(1)
    }

    fn clear(&self) {
        self.bytes.store(0, Ordering::Release);
        self.tx.send_replace(None);
    }
}

impl TelemetryReceiver {
    /// Waits for a newer value. `watch` coalesces intermediate publishes, so a
    /// slow consumer observes only the most recent snapshot.
    pub async fn changed(&mut self) -> bool {
        self.rx.changed().await.is_ok()
    }

    pub fn latest(&self) -> Option<Arc<SystemSnapshot>> {
        self.rx.borrow().clone()
    }

    pub fn latest_event(&self) -> Option<ServerEvent> {
        self.latest()
            .map(|snapshot| ServerEvent::TelemetryTick((*snapshot).clone()))
    }
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
mod tests {
    use super::*;
    use monitor_telemetry::TelemetrySampler;
    use std::time::Duration;

    fn snapshot_named(name: &str) -> SystemSnapshot {
        // Sampling refreshes every process; cache one base snapshot so the
        // stress tests exercise the channel rather than the OS sampler.
        static BASE: std::sync::OnceLock<SystemSnapshot> = std::sync::OnceLock::new();
        let mut snapshot = BASE
            .get_or_init(|| TelemetrySampler::new().sample_system_snapshot())
            .clone();
        snapshot.hostname = name.to_string();
        snapshot
    }

    #[tokio::test]
    async fn slow_consumer_only_sees_the_latest_value_within_byte_cap() {
        let bus = TelemetryBus::new(8 * 1024);
        let mut slow = bus.subscribe();

        // Publish far more data than the cap while the consumer is idle.
        for index in 0..500 {
            assert!(bus.publish(snapshot_named(&format!("host-{index:04}"))));
            assert!(bus.buffered_bytes() <= bus.max_bytes());
        }
        // The retained byte count is the encoded size of one snapshot, not the
        // sum of all 500 publishes (which would be ~1 MB).
        assert!(bus.buffered_bytes() > 0);
        assert!(bus.buffered_bytes() <= 8 * 1024);

        // A single wake-up yields the newest snapshot; no history is queued.
        assert!(
            tokio::time::timeout(Duration::from_millis(50), slow.changed())
                .await
                .expect("notified")
        );
        let latest = slow.latest().expect("latest snapshot");
        assert_eq!(latest.hostname, "host-0499");
        assert!(slow.latest_event().is_some());
    }

    #[tokio::test]
    async fn dropped_receiver_cannot_leave_unbounded_history() {
        let bus = TelemetryBus::new(8 * 1024);
        {
            let _subscriber = bus.subscribe();
        }
        for index in 0..2_000 {
            assert!(bus.publish(snapshot_named(&format!("h{index}"))));
        }
        assert!(bus.buffered_bytes() <= bus.max_bytes());
        assert!(bus.latest().is_some());
    }

    #[test]
    fn oversized_snapshot_is_dropped_and_slot_is_cleared() {
        let bus = TelemetryBus::new(4 * 1024);
        assert!(bus.publish(snapshot_named("small")));
        assert!(bus.buffered_bytes() > 0);
        assert!(!bus.publish(snapshot_named(&"x".repeat(16 * 1024))));
        assert_eq!(bus.buffered_bytes(), 0);
        assert!(bus.latest().is_none());
    }
}
