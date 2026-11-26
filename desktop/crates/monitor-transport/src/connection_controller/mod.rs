//! Connection state machine for path negotiation and recovery (NET-01/NET-02).

#[cfg(test)]
mod tests;
mod types;

pub use types::{
    ConnectionState, PathOutcome, ReconnectPolicy, RecoveryError, Transition, TransportPath,
};

use std::collections::BTreeSet;

#[derive(Debug)]
pub struct ConnectionController {
    state: ConnectionState,
    generation: u64,
    attempts: u32,
    policy: ReconnectPolicy,
    race_paths: BTreeSet<TransportPath>,
    last_path: Option<TransportPath>,
    last_backoff_ms: u64,
    jitter_seed: u64,
}

impl ConnectionController {
    pub fn new(policy: ReconnectPolicy) -> Self {
        Self {
            state: ConnectionState::Idle,
            generation: 0,
            attempts: 0,
            policy,
            race_paths: BTreeSet::new(),
            last_path: None,
            last_backoff_ms: 0,
            jitter_seed: 0x9E37_79B9_7F4A_7C15,
        }
    }

    pub fn state(&self) -> &ConnectionState {
        &self.state
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn attempts(&self) -> u32 {
        self.attempts
    }

    pub fn last_backoff_ms(&self) -> u64 {
        self.last_backoff_ms
    }

    /// Starts a fresh connection attempt with a new generation ID. Reconnect
    /// exhaustion (`Failed`) and `Closed` can be restarted; `Revoked` cannot.
    pub fn begin_discovery(&mut self) -> bool {
        if self.state.is_terminal() {
            return false;
        }
        self.generation = self.generation.saturating_add(1);
        self.attempts = 0;
        self.race_paths.clear();
        self.last_path = None;
        self.last_backoff_ms = 0;
        self.state = ConnectionState::Discover {
            generation: self.generation,
        };
        true
    }

    pub fn begin_authentication(&mut self, generation: u64) -> Transition {
        if self.state.generation() != Some(generation)
            || !matches!(self.state, ConnectionState::Discover { .. })
        {
            return Transition::IgnoredStale;
        }
        self.state = ConnectionState::Authenticating { generation };
        Transition::Accepted
    }

    /// Cancels duplicate candidates at declaration time (BTreeSet dedup).
    pub fn begin_race(&mut self, generation: u64, paths: &[TransportPath]) -> Transition {
        if self.state.generation() != Some(generation)
            || !matches!(self.state, ConnectionState::Authenticating { .. })
        {
            return Transition::IgnoredStale;
        }
        self.race_paths = paths.iter().copied().collect();
        let unique: Vec<TransportPath> = self.race_paths.iter().copied().collect();
        self.state = ConnectionState::Racing {
            generation,
            paths: unique,
        };
        Transition::Accepted
    }

    /// First path to become live wins. Every sibling race is cancelled, and a
    /// late result from a superseded generation cannot replace the live path.
    pub fn path_ready(&mut self, generation: u64, path: TransportPath) -> PathOutcome {
        if self.state.generation() != Some(generation)
            || !matches!(self.state, ConnectionState::Racing { .. })
        {
            return PathOutcome::Ignored;
        }
        if !self.race_paths.remove(&path) {
            return PathOutcome::Ignored;
        }
        self.race_paths.clear();
        self.last_path = Some(path);
        self.state = ConnectionState::Connected { generation, path };
        PathOutcome::Winner { path }
    }

    pub fn degrade(&mut self, generation: u64) -> Transition {
        match self.state {
            ConnectionState::Connected {
                generation: generation_id,
                path,
            } if generation_id == generation => {
                self.state = ConnectionState::Degraded {
                    generation: generation_id,
                    path,
                };
                Transition::Accepted
            }
            _ => Transition::IgnoredStale,
        }
    }

    /// Computes a bounded backoff with deterministic jitter.
    pub fn begin_recovery(&mut self, generation: u64) -> Result<u64, RecoveryError> {
        if self.state.is_terminal() {
            return Err(RecoveryError::Terminal);
        }
        if self.state.generation() != Some(generation)
            || !matches!(
                self.state,
                ConnectionState::Degraded { .. } | ConnectionState::Recovering { .. }
            )
        {
            return Err(RecoveryError::Stale);
        }
        let attempt = self.attempts.saturating_add(1);
        if attempt > self.policy.max_attempts {
            self.state = ConnectionState::Failed {
                generation,
                reason: "reconnect attempts exhausted".to_string(),
            };
            return Err(RecoveryError::Exhausted);
        }
        self.attempts = attempt;
        self.state = ConnectionState::Recovering {
            generation,
            attempt,
        };

        let exponential = self
            .policy
            .base_delay_ms
            .saturating_mul(1u64 << attempt.min(20))
            .min(self.policy.max_delay_ms);
        let jitter = self.next_jitter();
        let delay = exponential
            .saturating_add(jitter)
            .min(self.policy.max_delay_ms);
        self.last_backoff_ms = delay;
        Ok(delay)
    }

    /// Called when reconnection succeeds; restores the last winning path.
    pub fn recover(&mut self, generation: u64) -> Transition {
        if self.state.generation() != Some(generation)
            || !matches!(self.state, ConnectionState::Recovering { .. })
        {
            return Transition::IgnoredStale;
        }
        let path = self.last_path.unwrap_or(TransportPath::CloudRelay);
        self.last_backoff_ms = 0;
        self.state = ConnectionState::Connected { generation, path };
        Transition::Accepted
    }

    pub fn fail(&mut self, generation: u64, reason: &str) -> Transition {
        if self.state.is_terminal() || self.state.generation() != Some(generation) {
            return Transition::IgnoredStale;
        }
        self.state = ConnectionState::Failed {
            generation,
            reason: reason.to_string(),
        };
        Transition::Accepted
    }

    /// Revocation is terminal: no discovery, recovery or close may resume it.
    pub fn revoke(&mut self) {
        self.state = ConnectionState::Revoked;
        self.race_paths.clear();
        self.last_path = None;
    }

    pub fn close(&mut self) {
        if !self.state.is_terminal() {
            self.state = ConnectionState::Closed;
            self.race_paths.clear();
        }
    }

    fn next_jitter(&mut self) -> u64 {
        if self.policy.max_jitter_ms == 0 {
            return 0;
        }
        let mut x = self.jitter_seed;
        x ^= x >> 12;
        x ^= x << 25;
        x ^= x >> 27;
        self.jitter_seed = x;
        x.wrapping_mul(0x2545_F491_4F6C_DD1D) % (self.policy.max_jitter_ms + 1)
    }
}
