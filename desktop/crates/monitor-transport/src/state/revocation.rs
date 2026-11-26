use super::AppState;
use monitor_core::DeviceId;

impl AppState {
    /// Explicitly marks the host connection controller terminally revoked (e.g. account sign-out).
    pub fn shutdown_controller(&self) {
        if let Ok(mut controller) = self.connection_controller.lock() {
            controller.revoke();
        }
    }

    /// Record a revocation locally and notify every live transport so active
    /// sessions for THAT identity are torn down immediately without tearing
    /// down unrelated viewer sessions on the host (`AUD-007`).
    pub fn notify_revoked(&self, identity: &str) {
        if let Ok(mut revoked) = self.revoked_devices.lock() {
            revoked.insert(identity.to_string());
        }
        // A send failure only means no live session is subscribed; the in-memory
        // set and the database still enforce the revocation.
        let _ = self.revocation_tx.send(identity.to_string());
    }

    /// Revokes an identity durably (device token and viewer trust) and
    /// notifies every live transport in the same operation. All administrative
    /// revocation paths must use this helper so no path can forget to tear down
    /// active cloud/LAN sessions (AUD-01 / NEW-06).
    pub fn revoke_identity(&self, identity: &str) -> Result<bool, String> {
        let device = self
            .storage
            .revoke_device(&DeviceId::new(identity))
            .map_err(|e| e.to_string())?;
        let viewer = self
            .storage
            .revoke_viewer(identity)
            .map_err(|e| e.to_string())?;
        let changed = device || viewer;
        if changed {
            self.notify_revoked(identity);
        }
        Ok(changed)
    }

    /// Fast local revocation lookup. The caller must still consult durable
    /// storage for identities not present in this set (e.g. after a restart).
    pub fn is_revoked_locally(&self, identity: &str) -> bool {
        self.revoked_devices
            .lock()
            .map(|revoked| revoked.contains(identity))
            .unwrap_or(false)
    }

    /// Removes an identity from the in-memory revocation cache.
    pub fn unrevoke_device(&self, identity: &str) {
        if let Ok(mut revoked) = self.revoked_devices.lock() {
            revoked.remove(identity);
        }
    }

    /// Marks a device identity as currently connected on the local Wi-Fi / LAN.
    pub fn mark_lan_device_connected(&self, identity: &str) {
        if let Ok(mut set) = self.active_lan_devices.lock() {
            set.insert(identity.to_string());
        }
    }

    /// Marks a device identity as disconnected from the local Wi-Fi / LAN.
    pub fn mark_lan_device_disconnected(&self, identity: &str) {
        if let Ok(mut set) = self.active_lan_devices.lock() {
            set.remove(identity);
        }
    }

    /// Checks if a device is active on the local Wi-Fi / LAN network.
    pub fn is_active_lan_device(&self, identity: &str) -> bool {
        self.active_lan_devices
            .lock()
            .map(|set| set.contains(identity))
            .unwrap_or(false)
    }
}
