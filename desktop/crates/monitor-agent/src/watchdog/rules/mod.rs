mod battery_rules;
mod resource_rules;
mod session_rules;

use super::signals::SessionActivityTracker;
use monitor_core::SystemSnapshot;
use monitor_storage::{StoredAlert, StoredAlertRule};
use std::collections::HashMap;
use std::path::Path;

#[derive(Debug, Default)]
pub struct RuleEngineState {
    pub active_alerts: HashMap<String, ActiveAlertMeta>,
    pub unplugged_since_ms: Option<i64>,
    pub mem_critical_since_ms: Option<i64>,
    pub cpu_pinned_since_ms: Option<i64>,
}

#[derive(Debug, Clone)]
pub struct ActiveAlertMeta {
    pub severity: String,
    pub fired_at_ms: i64,
}

#[derive(Debug)]
pub struct EvaluationResult {
    pub new_alerts: Vec<StoredAlert>,
    pub recovered_dedup_keys: Vec<String>,
}

pub fn evaluate_rules(
    rules: &[StoredAlertRule],
    sessions: &HashMap<String, SessionActivityTracker>,
    telemetry: &SystemSnapshot,
    state: &mut RuleEngineState,
    now_ms: i64,
) -> EvaluationResult {
    let mut result = EvaluationResult {
        new_alerts: Vec::new(),
        recovered_dedup_keys: Vec::new(),
    };
    let rules_map: HashMap<&str, &StoredAlertRule> =
        rules.iter().map(|r| (r.kind.as_str(), r)).collect();

    session_rules::evaluate_session_rules(&rules_map, sessions, state, now_ms, &mut result);
    battery_rules::evaluate_battery_rules(
        &rules_map,
        sessions,
        telemetry,
        state,
        now_ms,
        &mut result,
    );
    resource_rules::evaluate_resource_rules(&rules_map, telemetry, state, now_ms, &mut result);

    result
}

pub(super) struct NewAlertSpec<'a> {
    pub rule_kind: &'a str,
    pub severity: &'a str,
    pub title: String,
    pub body: String,
    pub dedup_key: String,
    pub session_id: Option<String>,
    pub value: Option<f64>,
    pub now_ms: i64,
}

pub(super) fn build_alert(spec: NewAlertSpec<'_>) -> StoredAlert {
    StoredAlert {
        alert_id: format!("alert_{}_{}", spec.now_ms, rand_id()),
        rule_kind: spec.rule_kind.to_string(),
        severity: spec.severity.to_string(),
        title: spec.title,
        body: spec.body,
        dedup_key: spec.dedup_key,
        session_id: spec.session_id,
        value: spec.value,
        created_at_utc_ms: spec.now_ms,
        delivered_at_utc_ms: None,
        acked_at_utc_ms: None,
        snoozed_until_utc_ms: None,
        recovered_at_utc_ms: None,
    }
}

pub(super) fn repo_nickname(path: Option<&str>) -> String {
    let Some(p) = path else {
        return "workspace".to_string();
    };
    Path::new(p)
        .file_name()
        .and_then(|f| f.to_str())
        .unwrap_or(p)
        .to_string()
}

fn rand_id() -> u32 {
    use std::time::SystemTime;
    let nanos = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|d| d.subsec_nanos())
        .unwrap_or(12345);
    nanos % 1_000_000
}
