use monitor_core::{DeviceId, DeviceRole, DeviceTokenHash};
use monitor_storage::Storage;
use sha2::{Digest, Sha256};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TokenVerification {
    Authenticated(DeviceId, DeviceRole),
    Revoked,
    Unknown,
}

#[derive(Debug, Clone)]
pub struct TokenAuthenticator {
    storage: Storage,
}

impl TokenAuthenticator {
    pub fn new(storage: Storage) -> Self {
        Self { storage }
    }

    pub fn hash_token(raw_token: &str) -> DeviceTokenHash {
        use std::fmt::Write;
        let mut hasher = Sha256::new();
        hasher.update(raw_token.as_bytes());
        let result = hasher.finalize();
        let mut hex = String::with_capacity(64);
        for byte in result {
            let _ = write!(hex, "{byte:02x}");
        }
        DeviceTokenHash::new(hex)
    }

    pub fn verify_token(&self, raw_token: &str) -> anyhow::Result<TokenVerification> {
        let candidate_hash = Self::hash_token(raw_token);
        let Some(device_record) = self.storage.get_device_by_token_hash(&candidate_hash)? else {
            return Ok(TokenVerification::Unknown);
        };

        let (device_id, role, is_revoked) = device_record;

        if is_revoked {
            return Ok(TokenVerification::Revoked);
        }

        // SQLite looked up the SHA-256 digest; never compare a candidate to itself.
        let _ = self.storage.touch_device(&device_id);
        Ok(TokenVerification::Authenticated(device_id, role))
    }
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used)]
mod tests {
    use super::{TokenAuthenticator, TokenVerification};
    use monitor_core::{DeviceId, DeviceRole};
    use monitor_storage::Storage;

    #[test]
    fn revoked_local_token_is_not_reported_as_unknown() {
        let storage = Storage::in_memory().expect("in-memory storage");
        let device_id = DeviceId::new("android-test-device");
        let raw_token = "test-cloud-session-token";
        storage
            .insert_device(
                &device_id,
                "Test phone",
                &TokenAuthenticator::hash_token(raw_token),
                DeviceRole::Operator,
            )
            .expect("insert device");
        let authenticator = TokenAuthenticator::new(storage.clone());

        assert_eq!(
            authenticator
                .verify_token(raw_token)
                .expect("verify active"),
            TokenVerification::Authenticated(device_id.clone(), DeviceRole::Operator)
        );
        assert!(storage.revoke_device(&device_id).expect("revoke device"));
        assert_eq!(
            authenticator
                .verify_token(raw_token)
                .expect("verify revoked"),
            TokenVerification::Revoked
        );
    }

    #[test]
    fn raw_token_hash_cannot_authenticate_as_bearer_token() {
        let storage = Storage::in_memory().expect("in-memory storage");
        let device_id = DeviceId::new("viewer_abc123");
        let surrogate_hash = TokenAuthenticator::hash_token("v2_peer_only:viewer_abc123");
        storage
            .insert_device(
                &device_id,
                "LAN Viewer",
                &surrogate_hash,
                DeviceRole::Viewer,
            )
            .expect("insert device");
        let authenticator = TokenAuthenticator::new(storage);

        assert_eq!(
            authenticator
                .verify_token(surrogate_hash.as_str())
                .expect("reject raw hash lookup"),
            TokenVerification::Unknown
        );
        assert_eq!(
            authenticator
                .verify_token("noise_ik_viewer_abc123")
                .expect("reject synthetic noise_ik string"),
            TokenVerification::Unknown
        );
    }
}
