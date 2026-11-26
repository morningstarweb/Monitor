impl Storage {
    pub fn record_incoming_command(
        &self,
        entry: &crate::command_ledger::CommandLedgerEntry,
    ) -> Result<crate::command_ledger::LedgerAdmission> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::command_ledger::record_incoming_command(&conn, entry)
    }

    /// Atomically records a command under `(viewer_device_id, request_id)`.
    /// Duplicates return the stored result instead of re-executing the command.
    pub fn record_idempotent_command(
        &self,
        viewer_device_id: &str,
        request_id: &str,
        entry: &crate::command_ledger::CommandLedgerEntry,
    ) -> Result<crate::command_ledger::LedgerAdmission> {
        let mut conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::command_ledger::record_idempotent_command(
            &mut conn,
            viewer_device_id,
            request_id,
            entry,
        )
    }

    pub fn finalize_command(
        &self,
        command_id: &str,
        final_state: crate::command_ledger::CommandLedgerState,
        result_code: Option<&str>,
        result_digest: Option<&str>,
        result_summary: Option<&str>,
    ) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::command_ledger::finalize_command(
            &conn,
            command_id,
            final_state,
            result_code,
            result_digest,
            result_summary,
        )
    }

    pub fn get_command(
        &self,
        command_id: &str,
    ) -> Result<Option<crate::command_ledger::CommandLedgerEntry>> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::command_ledger::get_command(&conn, command_id)
    }

    pub fn reconcile_crash_states(&self) -> Result<usize> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::command_ledger::reconcile_crash_states(&conn)
    }

    pub fn enroll_viewer(
        &self,
        viewer_device_id: &str,
        user_id: &str,
        public_key_fingerprint: &str,
    ) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::enroll_viewer(&conn, viewer_device_id, user_id, public_key_fingerprint)
    }

    pub fn enroll_viewer_with_tier(
        &self,
        viewer_device_id: &str,
        user_id: &str,
        public_key_fingerprint: &str,
        initial_tier: &str,
    ) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::enroll_viewer_with_tier(
            &conn,
            viewer_device_id,
            user_id,
            public_key_fingerprint,
            initial_tier,
        )
    }

    pub fn update_all_active_viewers_tier(&self, tier: &str) -> Result<usize> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::update_all_active_viewers_tier(&conn, tier)
    }

    pub fn approve_viewer_tier(&self, viewer_device_id: &str, target_tier: &str) -> Result<bool> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::approve_viewer_tier(&conn, viewer_device_id, target_tier)
    }

    pub fn revoke_viewer(&self, viewer_device_id: &str) -> Result<bool> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::revoke_viewer(&conn, viewer_device_id)
    }

    /// Explicit administrative reauthorization of a revoked viewer.
    /// This is the only supported way to clear a revocation.
    pub fn reauthorize_viewer(&self, viewer_device_id: &str, tier: &str) -> Result<bool> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::reauthorize_viewer(&conn, viewer_device_id, tier)
    }

    pub fn get_viewer_trust(
        &self,
        viewer_device_id: &str,
    ) -> Result<Option<crate::viewer_trust::StoredViewerTrust>> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::get_viewer_trust(&conn, viewer_device_id)
    }

    pub fn list_active_viewers(&self) -> Result<Vec<crate::viewer_trust::StoredViewerTrust>> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::list_active_viewers(&conn)
    }

    pub fn list_all_viewers(&self) -> Result<Vec<crate::viewer_trust::StoredViewerTrust>> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        crate::viewer_trust::list_all_viewers(&conn)
    }
}
