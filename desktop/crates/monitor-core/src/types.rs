use schemars::JsonSchema;
use serde::{Deserialize, Serialize};
use std::fmt;

/// Opaque repository identifier (e.g. `r_9f8e7d6c`).
#[derive(
    Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize, JsonSchema,
)]
#[serde(transparent)]
pub struct RepoId(pub String);

impl RepoId {
    pub fn new(id: impl Into<String>) -> Self {
        Self(id.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for RepoId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

/// Opaque device identifier for paired Android devices.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize, JsonSchema)]
#[serde(transparent)]
pub struct DeviceId(pub String);

impl DeviceId {
    pub fn new(id: impl Into<String>) -> Self {
        Self(id.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for DeviceId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

/// Unique operation identifier for tracking background Git tasks.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize, JsonSchema)]
#[serde(transparent)]
pub struct OperationId(pub String);

impl OperationId {
    pub fn new(id: impl Into<String>) -> Self {
        Self(id.into())
    }

    pub fn generate() -> Self {
        let u = uuid::Uuid::new_v4();
        let short = &u.simple().to_string()[..12];
        Self(format!("op_{short}"))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for OperationId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

/// Monotonic repository revision counter for optimistic concurrency.
#[derive(
    Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize, JsonSchema,
)]
#[serde(transparent)]
pub struct Revision(pub u64);

impl Revision {
    pub fn next(self) -> Self {
        Self(self.0.saturating_add(1))
    }

    pub fn value(self) -> u64 {
        self.0
    }
}

impl fmt::Display for Revision {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

/// 6-digit numeric pairing code.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
#[serde(transparent)]
pub struct PairingCode(pub String);

impl PairingCode {
    pub fn new(code: impl Into<String>) -> Result<Self, &'static str> {
        let s = code.into();
        let cleaned: String = s.chars().filter(char::is_ascii_digit).collect();
        if cleaned.len() == 6 {
            Ok(Self(cleaned))
        } else {
            Err("Pairing code must consist of exactly 6 digits")
        }
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// SHA-256 hash of a device authorization bearer token.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize, JsonSchema)]
#[serde(transparent)]
pub struct DeviceTokenHash(pub String);

impl DeviceTokenHash {
    pub fn new(hash: impl Into<String>) -> Self {
        Self(hash.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// Scoped permission roles for paired devices.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum DeviceRole {
    Viewer,
    Operator,
    Admin,
    LocalUi,
}

impl fmt::Display for DeviceRole {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Viewer => write!(f, "Viewer"),
            Self::Operator => write!(f, "Operator"),
            Self::Admin => write!(f, "Admin"),
            Self::LocalUi => write!(f, "LocalUi"),
        }
    }
}

/// Information about a connected Google Cloud session.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct CloudAccount {
    pub email: String,
    pub name: String,
    pub token: String,
    pub device_id: String,
    pub logged_in_at_utc_ms: i64,
}

/// Non-secret cloud account details safe to expose to the local tray client.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct CloudAccountSummary {
    pub email: String,
}
