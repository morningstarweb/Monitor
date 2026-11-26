use super::{ActivePairingCode, AppState, DeviceOtpEntry, OtpVerifyError, WsTicketEntry};
use monitor_core::{DeviceId, DeviceRole};

impl AppState {
    /// Generates a new 6-digit pairing code valid for 180 seconds.
    pub fn generate_pairing_code(&self) -> String {
        use rand::Rng;
        let mut rng = rand::rng();
        let num: u32 = rng.random_range(100_000..=999_999);
        let code = num.to_string();

        let expires_at_ms = chrono::Utc::now().timestamp_millis() + (180 * 1000);
        let mut guard = match self.pairing_code.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        *guard = Some(ActivePairingCode {
            code: code.clone(),
            expires_at_ms,
            attempts: 0,
        });

        code
    }

    /// Generates a unique 6-digit OTP specifically scoped to one mobile device (valid for 5 minutes).
    pub fn generate_device_otp(&self, device_id: &str, device_name: &str) -> DeviceOtpEntry {
        use rand::Rng;
        let now = chrono::Utc::now().timestamp_millis();
        let expires_at_ms = now + (300 * 1000);

        let mut otps = match self.device_otps.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        otps.retain(|_, entry| entry.expires_at_ms > now);

        let mut rng = rand::rng();
        let mut code = rng.random_range(100_000..=999_999).to_string();
        for _ in 0..32 {
            if !otps
                .values()
                .any(|existing| existing.device_id != device_id && existing.code == code)
            {
                break;
            }
            code = rng.random_range(100_000..=999_999).to_string();
        }

        let entry = DeviceOtpEntry {
            device_id: device_id.to_string(),
            device_name: if device_name.trim().is_empty() {
                "Android Device".to_string()
            } else {
                device_name.trim().to_string()
            },
            code,
            expires_at_ms,
            attempts: 0,
        };
        otps.insert(device_id.to_string(), entry.clone());
        entry
    }

    /// Retrieves active (unexpired) per-device OTP if one was generated.
    pub fn get_active_device_otp(&self, device_id: &str) -> Option<DeviceOtpEntry> {
        let now = chrono::Utc::now().timestamp_millis();
        let mut otps = match self.device_otps.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        otps.retain(|_, entry| entry.expires_at_ms > now);
        otps.get(device_id).cloned()
    }

    /// Verifies a 6-digit OTP against per-device OTP sessions (preferred) or the global pairing code.
    /// Returns `Ok(Some((matched_device_id, matched_device_name)))` for a per-device OTP match,
    /// `Ok(None)` for the global pairing code match, or a typed `OtpVerifyError`.
    pub fn verify_otp_submission(
        &self,
        submitted_code: &str,
        candidate_device_ids: &[&str],
    ) -> Result<Option<(String, String)>, OtpVerifyError> {
        use subtle::ConstantTimeEq;

        let now = chrono::Utc::now().timestamp_millis();
        let trimmed = submitted_code.trim();
        let submitted_bytes = trimmed.as_bytes();

        let mut device_attempt_err: Option<OtpVerifyError> = None;
        {
            let mut otps = match self.device_otps.lock() {
                Ok(g) => g,
                Err(p) => p.into_inner(),
            };
            otps.retain(|_, entry| entry.expires_at_ms >= now);

            let matched_candidate_key = candidate_device_ids
                .iter()
                .find(|id| !id.is_empty() && otps.contains_key(**id))
                .map(|id| (*id).to_string());

            if let Some(ref key) = matched_candidate_key {
                if let Some(entry) = otps.get_mut(key) {
                    if entry.attempts >= 5 {
                        otps.remove(key);
                        device_attempt_err = Some(OtpVerifyError::TooManyAttempts);
                    } else {
                        entry.attempts = entry.attempts.saturating_add(1);
                        let expected_bytes = entry.code.as_bytes();
                        if submitted_bytes.len() == expected_bytes.len()
                            && bool::from(submitted_bytes.ct_eq(expected_bytes))
                        {
                            let matched = (entry.device_id.clone(), entry.device_name.clone());
                            otps.remove(key);
                            return Ok(Some(matched));
                        }
                        if entry.attempts >= 5 {
                            otps.remove(key);
                            device_attempt_err = Some(OtpVerifyError::TooManyAttempts);
                        } else {
                            device_attempt_err = Some(OtpVerifyError::InvalidCode);
                        }
                    }
                }
            } else if !otps.is_empty() {
                // Allow matching any unique active per-device OTP when the client only presents its code.
                let mut matched_key: Option<String> = None;
                for (key, entry) in otps.iter() {
                    let expected_bytes = entry.code.as_bytes();
                    if submitted_bytes.len() == expected_bytes.len()
                        && bool::from(submitted_bytes.ct_eq(expected_bytes))
                    {
                        matched_key = Some(key.clone());
                        break;
                    }
                }
                if let Some(key) = matched_key
                    && let Some(entry) = otps.remove(&key)
                {
                    return Ok(Some((entry.device_id, entry.device_name)));
                }
            }
        }

        // Fall back to global pairing session if one is active (QR Quick Pair & test parity).
        let mut guard = match self.pairing_code.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        let Some(active) = guard.as_mut() else {
            return Err(device_attempt_err.unwrap_or(OtpVerifyError::NoActiveSession));
        };
        if active.expires_at_ms < now {
            *guard = None;
            return Err(device_attempt_err.unwrap_or(OtpVerifyError::Expired));
        }
        if active.attempts >= 3 {
            *guard = None;
            return Err(OtpVerifyError::TooManyAttempts);
        }
        active.attempts = active.attempts.saturating_add(1);

        let expected_bytes = active.code.as_bytes();
        if submitted_bytes.len() != expected_bytes.len()
            || !bool::from(submitted_bytes.ct_eq(expected_bytes))
        {
            return Err(OtpVerifyError::InvalidCode);
        }
        *guard = None;
        Ok(None)
    }

    /// Issues a single-use 30-second ticket for WebSocket authentication.
    pub fn issue_ws_ticket(&self, device_id: DeviceId, role: DeviceRole) -> String {
        let ticket = format!("wst_{}", uuid::Uuid::new_v4().simple());
        let expires_at_ms = chrono::Utc::now().timestamp_millis() + (30 * 1000);

        let mut guard = match self.ws_tickets.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        // Clean up expired tickets
        let now = chrono::Utc::now().timestamp_millis();
        guard.retain(|_, entry| entry.expires_at_ms > now);

        guard.insert(
            ticket.clone(),
            WsTicketEntry {
                device_id,
                role,
                expires_at_ms,
            },
        );

        ticket
    }

    /// Consumes and validates a single-use WebSocket ticket.
    pub fn consume_ws_ticket(&self, ticket: &str) -> Option<(DeviceId, DeviceRole)> {
        let mut guard = match self.ws_tickets.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        let now = chrono::Utc::now().timestamp_millis();

        if let Some(entry) = guard.remove(ticket)
            && entry.expires_at_ms >= now
        {
            return Some((entry.device_id, entry.role));
        }
        None
    }
}
