/// A transport candidate raced during path negotiation.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum TransportPath {
    Lan,
    WebRtc,
    CloudRelay,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ConnectionState {
    Idle,
    Discover {
        generation: u64,
    },
    Authenticating {
        generation: u64,
    },
    Racing {
        generation: u64,
        paths: Vec<TransportPath>,
    },
    Connected {
        generation: u64,
        path: TransportPath,
    },
    Degraded {
        generation: u64,
        path: TransportPath,
    },
    Recovering {
        generation: u64,
        attempt: u32,
    },
    Failed {
        generation: u64,
        reason: String,
    },
    Closed,
    Revoked,
}

impl ConnectionState {
    pub fn generation(&self) -> Option<u64> {
        match self {
            Self::Idle | Self::Closed | Self::Revoked => None,
            Self::Discover { generation }
            | Self::Authenticating { generation }
            | Self::Racing { generation, .. }
            | Self::Connected { generation, .. }
            | Self::Degraded { generation, .. }
            | Self::Recovering { generation, .. }
            | Self::Failed { generation, .. } => Some(*generation),
        }
    }

    pub fn is_terminal(&self) -> bool {
        matches!(self, Self::Closed | Self::Revoked)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Transition {
    Accepted,
    IgnoredStale,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PathOutcome {
    /// The first live path won; all other races were cancelled.
    Winner { path: TransportPath },
    /// A duplicate/late path result that must not replace the live path.
    Ignored,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RecoveryError {
    Exhausted,
    Stale,
    Terminal,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ReconnectPolicy {
    pub max_attempts: u32,
    pub base_delay_ms: u64,
    pub max_delay_ms: u64,
    pub max_jitter_ms: u64,
}

impl Default for ReconnectPolicy {
    fn default() -> Self {
        Self {
            max_attempts: 5,
            base_delay_ms: 500,
            max_delay_ms: 15_000,
            max_jitter_ms: 1_000,
        }
    }
}
