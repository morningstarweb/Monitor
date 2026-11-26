impl Storage {
    pub fn list_devices(&self) -> Result<Vec<StoredDevice>> {
        let conn = self
            .read_conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut stmt = conn.prepare("SELECT device_id, display_name, role, last_seen_utc_ms, revoked_at_utc_ms FROM devices ORDER BY created_at_utc_ms DESC")?;
        stmt.query_map([], |row| {
            let revoked_at: Option<i64> = row.get(4)?;
            let role_str: String = row.get(2)?;
            let is_revoked = revoked_at.is_some();
            let is_pending = role_str == "Viewer" && !is_revoked;
            let trust_tier = match role_str.as_str() {
                "Admin" => "T3",
                "Operator" => "T2",
                "Member" => "T1",
                _ => "T0",
            }
            .to_string();
            Ok(StoredDevice {
                device_id: DeviceId::new(row.get::<_, String>(0)?),
                display_name: row.get(1)?,
                role: role_str,
                last_seen_utc_ms: row.get(3)?,
                is_revoked,
                is_local: false,
                is_pending,
                trust_tier,
                active_otp: None,
                otp_expires_at_ms: None,
                platform: "android".to_string(),
            })
        })?
        .collect()
    }

    pub fn reauthorize_device(&self, device_id: &DeviceId, role: DeviceRole) -> Result<bool> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let now = chrono::Utc::now().timestamp_millis();
        let role_str = role.to_string();
        let updated = conn.execute(
            "UPDATE devices SET revoked_at_utc_ms = NULL, role = ?1, last_seen_utc_ms = ?2 WHERE device_id = ?3",
            params![role_str, now, device_id.as_str()],
        )?;
        Ok(updated > 0)
    }

    pub fn revoke_device(&self, device_id: &DeviceId) -> Result<bool> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        Ok(conn.execute("UPDATE devices SET revoked_at_utc_ms = ?1 WHERE device_id = ?2 AND revoked_at_utc_ms IS NULL", params![chrono::Utc::now().timestamp_millis(), device_id.as_str()])? > 0)
    }

    pub fn touch_device(&self, device_id: &DeviceId) -> Result<()> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.execute(
            "UPDATE devices SET last_seen_utc_ms = ?1 WHERE device_id = ?2",
            params![chrono::Utc::now().timestamp_millis(), device_id.as_str()],
        )?;
        Ok(())
    }

    pub fn is_device_active(&self, device_id: &DeviceId) -> Result<bool> {
        let conn = self
            .read_conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        conn.query_row("SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = ?1 AND revoked_at_utc_ms IS NULL)", [device_id.as_str()], |row| row.get(0))
    }
    pub fn insert_device(
        &self,
        device_id: &DeviceId,
        display_name: &str,
        token_hash: &DeviceTokenHash,
        role: DeviceRole,
    ) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let now = chrono::Utc::now().timestamp_millis();
        let role_str = role.to_string();

        conn.execute(
            "INSERT INTO devices (device_id, display_name, token_hash, role, created_at_utc_ms, last_seen_utc_ms)
             VALUES (?1, ?2, ?3, ?4, ?5, ?5)",
            params![device_id.as_str(), display_name, token_hash.as_str(), role_str, now],
        )?;
        Ok(())
    }

    pub fn set_verified_device_credential(
        &self,
        device_id: &DeviceId,
        display_name: &str,
        token_hash: &DeviceTokenHash,
        role: DeviceRole,
    ) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let now = chrono::Utc::now().timestamp_millis();
        let role_str = role.to_string();
        conn.execute(
            "INSERT INTO devices (device_id, display_name, token_hash, role, created_at_utc_ms, last_seen_utc_ms, revoked_at_utc_ms)
             VALUES (?1, ?2, ?3, ?4, ?5, ?5, NULL)
             ON CONFLICT(device_id) DO UPDATE SET
                display_name = CASE WHEN excluded.display_name != '' THEN excluded.display_name ELSE devices.display_name END,
                token_hash = excluded.token_hash,
                role = excluded.role,
                last_seen_utc_ms = excluded.last_seen_utc_ms,
                revoked_at_utc_ms = NULL",
            params![device_id.as_str(), display_name, token_hash.as_str(), role_str, now],
        )?;
        Ok(())
    }

    pub fn upsert_device(
        &self,
        device_id: &DeviceId,
        display_name: &str,
        token_hash: &DeviceTokenHash,
        role: DeviceRole,
    ) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let now = chrono::Utc::now().timestamp_millis();
        let role_str = role.to_string();

        // Security invariant (AUD-01): an upsert is a routine connection/sync
        // operation. It must never clear an explicit revocation, overwrite the
        // token hash, or elevate the role. Only `revoke_device` and an explicit
        // administrative reauthorization may change those fields.
        conn.execute(
            "INSERT INTO devices (device_id, display_name, token_hash, role, created_at_utc_ms, last_seen_utc_ms, revoked_at_utc_ms)
             VALUES (?1, ?2, ?3, ?4, ?5, ?5, NULL)
             ON CONFLICT(device_id) DO UPDATE SET
                display_name = CASE WHEN excluded.display_name != '' AND excluded.display_name != 'local_lan_viewer' THEN excluded.display_name ELSE devices.display_name END,
                last_seen_utc_ms = excluded.last_seen_utc_ms",
            params![device_id.as_str(), display_name, token_hash.as_str(), role_str, now],
        )?;
        Ok(())
    }

    pub fn get_device_by_token_hash(
        &self,
        token_hash: &DeviceTokenHash,
    ) -> Result<Option<(DeviceId, DeviceRole, bool)>> {
        let conn = match self.read_conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let mut stmt = conn.prepare(
            "SELECT device_id, role, revoked_at_utc_ms FROM devices WHERE token_hash = ?1",
        )?;
        let mut rows = stmt.query(params![token_hash.as_str()])?;
        if let Some(row) = rows.next()? {
            let id: String = row.get(0)?;
            let role_str: String = row.get(1)?;
            let revoked_at: Option<i64> = row.get(2)?;

            let role = match role_str.as_str() {
                "Operator" => DeviceRole::Operator,
                "Admin" => DeviceRole::Admin,
                "LocalUi" => DeviceRole::LocalUi,
                _ => DeviceRole::Viewer,
            };

            let is_revoked = revoked_at.is_some();
            Ok(Some((DeviceId::new(id), role, is_revoked)))
        } else {
            Ok(None)
        }
    }
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
mod device_revocation_tests {
    use super::*;

    fn storage() -> Storage {
        let mut conn = Connection::open_in_memory().expect("in-memory db");
        run_migrations(&mut conn).expect("migrations");
        let shared = Arc::new(Mutex::new(conn));
        Storage {
            conn: Arc::clone(&shared),
            read_conn: shared,
        }
    }

    /// AUD-01 regression: device upsert must preserve an explicit revocation and
    /// must not elevate a previously assigned role on reconnect.
    #[test]
    fn upsert_cannot_unrevoke_or_elevate_role() {
        let storage = storage();
        let device = DeviceId::new("revoked-device");
        let token = DeviceTokenHash::new("mon_dev_original");
        storage
            .upsert_device(&device, "Phone", &token, DeviceRole::Viewer)
            .expect("initial insert");
        assert!(storage.revoke_device(&device).expect("revoke"));
        assert!(!storage.is_device_active(&device).expect("active check"));

        // Repeated reconnects with a fresh token and a privileged role request.
        for i in 0..3 {
            let new_token = DeviceTokenHash::new(format!("mon_dev_rotated_{i}"));
            storage
                .upsert_device(&device, "Phone", &new_token, DeviceRole::Operator)
                .expect("reconnect upsert");
            assert!(
                !storage.is_device_active(&device).expect("active check"),
                "revocation erased by upsert #{i}"
            );
            // The rotated token must NOT become a credential: token hashes are
            // immutable on upsert, so lookup by the new token finds nothing.
            assert!(
                storage
                    .get_device_by_token_hash(&new_token)
                    .expect("lookup by rotated token")
                    .is_none(),
                "upsert silently rotated the device credential"
            );
            let (device_id_after, role, revoked) = storage
                .get_device_by_token_hash(&token)
                .expect("lookup by original token")
                .expect("original credential must still resolve");
            assert_eq!(device_id_after, device);
            assert!(revoked, "revocation flag lost");
            assert_eq!(role, DeviceRole::Viewer, "role silently escalated");
        }
    }

    #[test]
    fn reauthorize_device_restores_active_state_and_role() {
        let storage = storage();
        let device = DeviceId::new("phone-to-reauth");
        let token = DeviceTokenHash::new("mon_dev_tok123");
        storage
            .upsert_device(&device, "OnePlus Phone", &token, DeviceRole::Viewer)
            .expect("insert");
        assert!(storage.revoke_device(&device).expect("revoke"));
        assert!(!storage.is_device_active(&device).expect("active check"));

        // Reauthorize explicitly
        assert!(storage.reauthorize_device(&device, DeviceRole::Operator).expect("reauthorize"));
        assert!(storage.is_device_active(&device).expect("active check after reauth"));
        let (device_id_after, role, revoked) = storage
            .get_device_by_token_hash(&token)
            .expect("lookup")
            .expect("found");
        assert_eq!(device_id_after, device);
        assert!(!revoked);
        assert_eq!(role, DeviceRole::Operator);
    }
}
