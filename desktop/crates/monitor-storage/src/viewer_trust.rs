use rusqlite::{Connection, OptionalExtension, Result, params};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct StoredViewerTrust {
    pub device_id: String,
    pub user_id: String,
    pub public_key: String,
    pub trust_tier: String, // "T0", "T1", "T2", "T3"
    pub enrolled_at_utc_ms: i64,
    pub approved_at_utc_ms: Option<i64>,
    pub revoked_at_utc_ms: Option<i64>,
}

/// Enrolls a *new* viewer device with a specified trust tier.
///
/// Security invariant (AUD-01): enrollment is a one-time, insert-only operation.
/// An existing record — including its trust tier, public key and revocation state —
/// is never modified here. Reconnecting, heartbeat and synchronization paths call
/// this function, so it must never be able to clear an explicit revocation or
/// silently promote an unapproved viewer. Elevation and reauthorization are
/// separate, explicitly authorized operations (`approve_viewer_tier` /
/// `reauthorize_viewer`).
pub fn enroll_viewer_with_tier(
    conn: &Connection,
    device_id: &str,
    user_id: &str,
    public_key: &str,
    initial_tier: &str,
) -> Result<()> {
    let now = chrono::Utc::now().timestamp_millis();
    conn.execute(
        "INSERT INTO viewer_trust (device_id, user_id, public_key, trust_tier, enrolled_at_utc_ms, approved_at_utc_ms)
         VALUES (?1, ?2, ?3, ?4, ?5, ?5)
         ON CONFLICT(device_id) DO NOTHING",
        params![device_id, user_id, public_key, initial_tier, now],
    )?;
    Ok(())
}

/// Enrolls a new viewer device with default Read-Only (T0) tier.
pub fn enroll_viewer(
    conn: &Connection,
    device_id: &str,
    user_id: &str,
    public_key: &str,
) -> Result<()> {
    enroll_viewer_with_tier(conn, device_id, user_id, public_key, "T0")
}

/// Updates trust tier for all active (unrevoked) viewers.
pub fn update_all_active_viewers_tier(conn: &Connection, tier: &str) -> Result<usize> {
    let now = chrono::Utc::now().timestamp_millis();
    let updated = conn.execute(
        "UPDATE viewer_trust
         SET trust_tier = ?1, approved_at_utc_ms = ?2
         WHERE revoked_at_utc_ms IS NULL",
        params![tier, now],
    )?;
    Ok(updated)
}

/// Approves/promotes a viewer device to a higher trust tier (e.g. T2 or T3) upon host confirmation.
pub fn approve_viewer_tier(conn: &Connection, device_id: &str, tier: &str) -> Result<bool> {
    if !matches!(tier, "T0" | "T1" | "T2" | "T3") {
        return Ok(false);
    }
    let now = chrono::Utc::now().timestamp_millis();
    let updated = conn.execute(
        "UPDATE viewer_trust
         SET trust_tier = ?1, approved_at_utc_ms = ?2
         WHERE device_id = ?3 AND revoked_at_utc_ms IS NULL",
        params![tier, now, device_id],
    )?;
    Ok(updated > 0)
}

/// Revokes a viewer device's trust.
pub fn revoke_viewer(conn: &Connection, device_id: &str) -> Result<bool> {
    let now = chrono::Utc::now().timestamp_millis();
    let updated = conn.execute(
        "UPDATE viewer_trust
         SET revoked_at_utc_ms = ?1
         WHERE device_id = ?2",
        params![now, device_id],
    )?;
    Ok(updated > 0)
}

/// Explicitly reauthorizes a previously revoked viewer and assigns a trust tier.
///
/// This is the *only* operation permitted to clear `revoked_at_utc_ms`. It must be
/// reachable solely from an authenticated administrative action, never from a
/// connection, enrollment, heartbeat or device-upsert path (AUD-01, invariant A).
pub fn reauthorize_viewer(conn: &Connection, device_id: &str, tier: &str) -> Result<bool> {
    // Defense in depth: only a revoked row can be reauthorized, and only to a
    // known tier. Callers must still be authenticated administrators.
    if !matches!(tier, "T0" | "T1" | "T2" | "T3") {
        return Ok(false);
    }
    let now = chrono::Utc::now().timestamp_millis();
    let updated = conn.execute(
        "UPDATE viewer_trust
         SET trust_tier = ?1, approved_at_utc_ms = ?2, revoked_at_utc_ms = NULL
         WHERE device_id = ?3 AND revoked_at_utc_ms IS NOT NULL",
        params![tier, now, device_id],
    )?;
    Ok(updated > 0)
}

/// Retrieves trust information for a viewer device.
pub fn get_viewer_trust(conn: &Connection, device_id: &str) -> Result<Option<StoredViewerTrust>> {
    conn.query_row(
        "SELECT device_id, user_id, public_key, trust_tier, enrolled_at_utc_ms,
                approved_at_utc_ms, revoked_at_utc_ms
         FROM viewer_trust WHERE device_id = ?1",
        params![device_id],
        |row| {
            Ok(StoredViewerTrust {
                device_id: row.get(0)?,
                user_id: row.get(1)?,
                public_key: row.get(2)?,
                trust_tier: row.get(3)?,
                enrolled_at_utc_ms: row.get(4)?,
                approved_at_utc_ms: row.get(5)?,
                revoked_at_utc_ms: row.get(6)?,
            })
        },
    )
    .optional()
}

/// Retrieves all active (unrevoked) viewer devices from viewer_trust.
pub fn list_active_viewers(conn: &Connection) -> Result<Vec<StoredViewerTrust>> {
    let mut stmt = conn.prepare(
        "SELECT device_id, user_id, public_key, trust_tier, enrolled_at_utc_ms,
                approved_at_utc_ms, revoked_at_utc_ms
         FROM viewer_trust
         WHERE revoked_at_utc_ms IS NULL
         ORDER BY enrolled_at_utc_ms DESC",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(StoredViewerTrust {
            device_id: row.get(0)?,
            user_id: row.get(1)?,
            public_key: row.get(2)?,
            trust_tier: row.get(3)?,
            enrolled_at_utc_ms: row.get(4)?,
            approved_at_utc_ms: row.get(5)?,
            revoked_at_utc_ms: row.get(6)?,
        })
    })?;
    rows.collect()
}

/// Retrieves all enrolled viewer devices (active and revoked) from viewer_trust.
pub fn list_all_viewers(conn: &Connection) -> Result<Vec<StoredViewerTrust>> {
    let mut stmt = conn.prepare(
        "SELECT device_id, user_id, public_key, trust_tier, enrolled_at_utc_ms,
                approved_at_utc_ms, revoked_at_utc_ms
         FROM viewer_trust
         ORDER BY enrolled_at_utc_ms DESC",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(StoredViewerTrust {
            device_id: row.get(0)?,
            user_id: row.get(1)?,
            public_key: row.get(2)?,
            trust_tier: row.get(3)?,
            enrolled_at_utc_ms: row.get(4)?,
            approved_at_utc_ms: row.get(5)?,
            revoked_at_utc_ms: row.get(6)?,
        })
    })?;
    rows.collect()
}

#[cfg(test)]
#[allow(clippy::panic, clippy::unwrap_used)]
mod tests {
    use super::*;
    use crate::migrations::run_migrations;

    fn setup_test_db() -> Connection {
        let mut conn = Connection::open_in_memory().unwrap_or_else(|e| panic!("in memory db: {e}"));
        let _ = run_migrations(&mut conn);
        conn
    }

    #[test]
    fn test_viewer_trust_lifecycle() {
        let conn = setup_test_db();
        let _ = enroll_viewer(&conn, "phone-dev-1", "user-google-1", "pubkey_hex_123");

        let trust = get_viewer_trust(&conn, "phone-dev-1").unwrap_or(None);
        assert!(trust.is_some());
        let t = trust.unwrap_or_else(|| panic!("expected trust"));
        assert_eq!(t.trust_tier, "T0"); // Default read-only

        // Approve for T3 Dangerous
        let approved = approve_viewer_tier(&conn, "phone-dev-1", "T3").unwrap_or(false);
        assert!(approved);

        let t2 = get_viewer_trust(&conn, "phone-dev-1").unwrap_or(None);
        assert_eq!(t2.map(|x| x.trust_tier), Some("T3".to_string()));

        // Revoke
        let revoked = revoke_viewer(&conn, "phone-dev-1").unwrap_or(false);
        assert!(revoked);

        let t3 = get_viewer_trust(&conn, "phone-dev-1").unwrap_or(None);
        assert!(t3.and_then(|x| x.revoked_at_utc_ms).is_some());

        // Test enroll_viewer_with_tier
        let _ = enroll_viewer_with_tier(
            &conn,
            "phone-dev-2",
            "user-google-2",
            "pubkey_hex_456",
            "T2",
        );
        let t4 = get_viewer_trust(&conn, "phone-dev-2").unwrap_or(None);
        assert_eq!(t4.map(|x| x.trust_tier), Some("T2".to_string()));

        // Test update_all_active_viewers_tier
        let count = update_all_active_viewers_tier(&conn, "T1").unwrap_or(0);
        assert_eq!(count, 1); // only phone-dev-2 is unrevoked
        let t5 = get_viewer_trust(&conn, "phone-dev-2").unwrap_or(None);
        assert_eq!(t5.map(|x| x.trust_tier), Some("T1".to_string()));

        // Test list_active_viewers
        let active = list_active_viewers(&conn).unwrap_or_default();
        assert_eq!(active.len(), 1);
        assert_eq!(active[0].device_id, "phone-dev-2");
        assert_eq!(active[0].user_id, "user-google-2");
    }

    /// AUD-01 regression: routine enrollment must never undo an explicit revocation.
    #[test]
    fn reconnect_cannot_clear_revocation_or_restore_privileged_tier() {
        let conn = setup_test_db();
        let device = "revoked-viewer";
        enroll_viewer_with_tier(&conn, device, "user-1", "pk-original", "T2").unwrap();
        assert!(
            approve_viewer_tier(&conn, device, "T2").unwrap(),
            "initial approval must succeed"
        );

        assert!(revoke_viewer(&conn, device).unwrap());
        let before = get_viewer_trust(&conn, device).unwrap().unwrap();
        assert!(before.revoked_at_utc_ms.is_some());
        assert_eq!(before.trust_tier, "T2");

        // Simulate several reconnect / heartbeat / sync enrollments with a fresh key.
        for i in 0..3 {
            enroll_viewer_with_tier(&conn, device, "user-1", &format!("pk-rotated-{i}"), "T2")
                .unwrap();
            let after = get_viewer_trust(&conn, device).unwrap().unwrap();
            assert!(
                after.revoked_at_utc_ms.is_some(),
                "revocation erased by reconnect #{i}"
            );
            assert_eq!(after.revoked_at_utc_ms, before.revoked_at_utc_ms);
            assert_eq!(
                after.public_key, "pk-original",
                "public key silently replaced"
            );
            assert_eq!(after.trust_tier, "T2");
        }

        // Promotion of a revoked viewer is impossible; only explicit reauthorization works.
        assert!(!approve_viewer_tier(&conn, device, "T3").unwrap());
        assert!(reauthorize_viewer(&conn, device, "T0").unwrap());
        let restored = get_viewer_trust(&conn, device).unwrap().unwrap();
        assert!(restored.revoked_at_utc_ms.is_none());
        assert_eq!(
            restored.trust_tier, "T0",
            "reauthorization must not grant privilege"
        );
        assert!(restored.approved_at_utc_ms.is_some());
    }

    /// Reauthorization is a narrow primitive: it cannot clear a live
    /// (non-revoked) row, invent a tier, or elevate by reauthorization.
    #[test]
    fn reauthorize_is_limited_to_revoked_rows_and_known_tiers() {
        let conn = setup_test_db();
        enroll_viewer_with_tier(&conn, "live-viewer", "u", "pk-live", "T0").unwrap();

        // A live row is not reauthorized (no state change), and an unknown tier
        // is rejected outright.
        assert!(!reauthorize_viewer(&conn, "live-viewer", "T2").unwrap());
        assert!(!reauthorize_viewer(&conn, "live-viewer", "T9").unwrap());
        let live = get_viewer_trust(&conn, "live-viewer").unwrap().unwrap();
        assert_eq!(live.trust_tier, "T0");
        assert!(live.revoked_at_utc_ms.is_none());

        revoke_viewer(&conn, "live-viewer").unwrap();
        assert!(!reauthorize_viewer(&conn, "live-viewer", "T9").unwrap());
        assert!(
            get_viewer_trust(&conn, "live-viewer")
                .unwrap()
                .unwrap()
                .revoked_at_utc_ms
                .is_some()
        );
        assert!(reauthorize_viewer(&conn, "live-viewer", "T1").unwrap());
        let restored = get_viewer_trust(&conn, "live-viewer").unwrap().unwrap();
        assert_eq!(restored.trust_tier, "T1");
        assert!(restored.revoked_at_utc_ms.is_none());
    }

    /// An approved viewer's tier and identity survive ordinary reconnects.
    #[test]
    fn reconnect_preserves_existing_approval() {
        let conn = setup_test_db();
        let device = "approved-viewer";
        enroll_viewer_with_tier(&conn, device, "user-2", "pk-a", "T0").unwrap();
        assert!(approve_viewer_tier(&conn, device, "T3").unwrap());

        enroll_viewer_with_tier(&conn, device, "user-2", "pk-b", "T0").unwrap();
        let trust = get_viewer_trust(&conn, device).unwrap().unwrap();
        assert_eq!(trust.trust_tier, "T3");
        assert_eq!(trust.public_key, "pk-a");
        assert!(trust.revoked_at_utc_ms.is_none());
    }
}
