impl Storage {
    pub fn get_setting(&self, key: &str) -> Result<Option<String>> {
        let conn = match self.read_conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let mut stmt = conn.prepare("SELECT value FROM settings WHERE key = ?1")?;
        let mut rows = stmt.query(params![key])?;
        if let Some(row) = rows.next()? {
            Ok(Some(row.get(0)?))
        } else {
            Ok(None)
        }
    }

    pub fn set_setting(&self, key: &str, value: &str) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let now = chrono::Utc::now().timestamp_millis();
        conn.execute(
            "INSERT INTO settings (key, value, updated_at_utc_ms) VALUES (?1, ?2, ?3)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at_utc_ms = excluded.updated_at_utc_ms",
            params![key, value, now],
        )?;
        Ok(())
    }

    pub fn delete_setting(&self, key: &str) -> Result<bool> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        Ok(conn.execute("DELETE FROM settings WHERE key = ?1", params![key])? > 0)
    }

    // ==========================================
    // Command Ledger & Viewer Trust (v3.2)
    // ==========================================
}
