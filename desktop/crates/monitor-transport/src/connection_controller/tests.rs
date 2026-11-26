#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use super::*;

fn controller() -> ConnectionController {
    ConnectionController::new(ReconnectPolicy {
        max_attempts: 3,
        base_delay_ms: 100,
        max_delay_ms: 1_000,
        max_jitter_ms: 200,
    })
}

#[test]
fn happy_path_tracks_generation() {
    let mut c = controller();
    assert_eq!(c.state(), &ConnectionState::Idle);
    assert!(c.begin_discovery());
    let generation_id = c.generation();
    assert_eq!(generation_id, 1);
    assert_eq!(
        c.state(),
        &ConnectionState::Discover {
            generation: generation_id
        }
    );
    assert_eq!(c.begin_authentication(generation_id), Transition::Accepted);
    assert_eq!(
        c.state(),
        &ConnectionState::Authenticating {
            generation: generation_id
        }
    );
    assert_eq!(
        c.begin_race(generation_id, &[TransportPath::Lan, TransportPath::WebRtc]),
        Transition::Accepted
    );
    assert_eq!(
        c.path_ready(generation_id, TransportPath::WebRtc),
        PathOutcome::Winner {
            path: TransportPath::WebRtc
        }
    );
    assert_eq!(
        c.state(),
        &ConnectionState::Connected {
            generation: generation_id,
            path: TransportPath::WebRtc
        }
    );
}

#[test]
fn duplicate_race_paths_are_cancelled_and_only_first_wins() {
    let mut c = controller();
    assert!(c.begin_discovery());
    let generation_id = c.generation();
    let _ = c.begin_authentication(generation_id);
    assert_eq!(
        c.begin_race(
            generation_id,
            &[
                TransportPath::Lan,
                TransportPath::Lan,
                TransportPath::WebRtc,
                TransportPath::CloudRelay,
            ],
        ),
        Transition::Accepted
    );
    match c.state() {
        ConnectionState::Racing { paths, .. } => assert_eq!(paths.len(), 3),
        other => panic!("unexpected state: {other:?}"),
    }
    assert_eq!(
        c.path_ready(generation_id, TransportPath::Lan),
        PathOutcome::Winner {
            path: TransportPath::Lan
        }
    );
    assert_eq!(
        c.path_ready(generation_id, TransportPath::WebRtc),
        PathOutcome::Ignored
    );
    assert_eq!(
        c.path_ready(generation_id, TransportPath::CloudRelay),
        PathOutcome::Ignored
    );
    assert_eq!(
        c.state(),
        &ConnectionState::Connected {
            generation: generation_id,
            path: TransportPath::Lan
        }
    );
}

#[test]
fn stale_generation_events_are_ignored() {
    let mut c = controller();
    assert!(c.begin_discovery());
    let old_generation_id = c.generation();
    let _ = c.begin_authentication(old_generation_id);
    assert_eq!(
        c.begin_authentication(old_generation_id.wrapping_sub(1)),
        Transition::IgnoredStale
    );
    assert_eq!(
        c.begin_race(old_generation_id.wrapping_add(1), &[TransportPath::Lan]),
        Transition::IgnoredStale
    );
    assert_eq!(
        c.path_ready(old_generation_id, TransportPath::Lan),
        PathOutcome::Ignored
    );

    assert!(c.begin_discovery());
    let new_generation_id = c.generation();
    assert_ne!(new_generation_id, old_generation_id);
    assert_eq!(
        c.begin_authentication(old_generation_id),
        Transition::IgnoredStale
    );
    assert_eq!(
        c.fail(old_generation_id, "stale failure"),
        Transition::IgnoredStale
    );
}

#[test]
fn reconnect_is_bounded_and_jitter_stays_within_policy() {
    let mut c = controller();
    assert!(c.begin_discovery());
    let generation_id = c.generation();
    let _ = c.begin_authentication(generation_id);
    let _ = c.begin_race(generation_id, &[TransportPath::Lan]);
    let _ = c.path_ready(generation_id, TransportPath::Lan);
    assert_eq!(c.degrade(generation_id), Transition::Accepted);

    let mut delays = Vec::new();
    for expected_attempt in 1..=3 {
        let delay = c.begin_recovery(generation_id).expect("recovery scheduled");
        assert!(delay <= c.policy.max_delay_ms);
        assert_eq!(c.attempts(), expected_attempt);
        assert_eq!(c.state().generation(), Some(generation_id));
        delays.push(delay);
        assert_eq!(c.recover(generation_id), Transition::Accepted);
        assert_eq!(c.degrade(generation_id), Transition::Accepted);
    }
    assert!(delays.iter().all(|d| *d >= c.policy.base_delay_ms));
    assert_eq!(
        c.begin_recovery(generation_id),
        Err(RecoveryError::Exhausted)
    );
    assert!(matches!(c.state(), ConnectionState::Failed { .. }));
    assert_eq!(c.begin_recovery(generation_id), Err(RecoveryError::Stale));
}

#[test]
fn revocation_is_terminal_and_close_is_not_resurrectable() {
    let mut c = controller();
    assert!(c.begin_discovery());
    let generation_id = c.generation();
    c.revoke();
    assert_eq!(c.state(), &ConnectionState::Revoked);
    assert!(!c.begin_discovery());
    assert_eq!(
        c.begin_authentication(generation_id),
        Transition::IgnoredStale
    );
    assert_eq!(
        c.begin_recovery(generation_id),
        Err(RecoveryError::Terminal)
    );
    assert_eq!(c.fail(generation_id, "ignored"), Transition::IgnoredStale);

    let mut c2 = controller();
    assert!(c2.begin_discovery());
    c2.close();
    assert_eq!(c2.state(), &ConnectionState::Closed);
    assert!(!c2.begin_discovery());
    assert_eq!(
        c2.begin_recovery(c2.generation()),
        Err(RecoveryError::Terminal)
    );
}
