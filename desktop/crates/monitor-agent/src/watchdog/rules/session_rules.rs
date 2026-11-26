use super::super::signals::SessionActivityTracker;
use super::{
    ActiveAlertMeta, EvaluationResult, NewAlertSpec, RuleEngineState, build_alert, repo_nickname,
};
use monitor_storage::StoredAlertRule;
use std::collections::HashMap;

pub(super) fn evaluate_session_rules(
    rules_map: &HashMap<&str, &StoredAlertRule>,
    sessions: &HashMap<String, SessionActivityTracker>,
    state: &mut RuleEngineState,
    now_ms: i64,
    out: &mut EvaluationResult,
) {
    for session in sessions.values() {
        let repo_nick = repo_nickname(session.repo_path.as_deref());

        // Rule: agent.stalled
        if let Some(rule) = rules_map.get("agent.stalled")
            && rule.enabled
        {
            let dedup_key = format!("agent.stalled:{}", session.session_id);
            if session.current_state == "Stalled" {
                let elapsed_s = (now_ms - session.last_activity_ms) / 1000;
                let is_critical = elapsed_s >= 1800;
                let target_severity = if is_critical { "critical" } else { "warning" };

                let can_fire = match state.active_alerts.get(&dedup_key) {
                    None => true,
                    Some(meta) => {
                        // Escalate if currently warning and now critical
                        (meta.severity != "critical" && is_critical)
                            || (now_ms - meta.fired_at_ms >= (rule.cooldown_s as i64 * 1000))
                    }
                };

                if can_fire {
                    let title = if is_critical {
                        format!("{} Stalled (Critical)", session.profile_name)
                    } else {
                        format!("{} may be stuck or waiting for input", session.profile_name)
                    };
                    let body = format!("No activity for {}m on {repo_nick}", elapsed_s / 60);
                    let alert = build_alert(NewAlertSpec {
                        rule_kind: "agent.stalled",
                        severity: target_severity,
                        title,
                        body,
                        dedup_key: dedup_key.clone(),
                        session_id: Some(session.session_id.clone()),
                        value: Some(elapsed_s as f64),
                        now_ms,
                    });
                    state.active_alerts.insert(
                        dedup_key,
                        ActiveAlertMeta {
                            severity: target_severity.to_string(),
                            fired_at_ms: now_ms,
                        },
                    );
                    out.new_alerts.push(alert);
                }
            } else if session.current_state == "Active"
                && state.active_alerts.contains_key(&dedup_key)
            {
                state.active_alerts.remove(&dedup_key);
                out.recovered_dedup_keys.push(dedup_key);
            }
        }

        // Rule: agent.ended
        if let Some(rule) = rules_map.get("agent.ended")
            && rule.enabled
        {
            let dedup_key = format!("agent.ended:{}", session.session_id);
            if session.current_state == "Ended" && !state.active_alerts.contains_key(&dedup_key) {
                let code = session.exit_code.unwrap_or(0);
                let (sev, status_str) = if code == 0 {
                    ("info", "Finished")
                } else {
                    ("warning", "Terminated")
                };

                let alert = build_alert(NewAlertSpec {
                    rule_kind: "agent.ended",
                    severity: sev,
                    title: format!("{} {status_str}", session.profile_name),
                    body: format!("Session exited with code {code} on {repo_nick}"),
                    dedup_key: dedup_key.clone(),
                    session_id: Some(session.session_id.clone()),
                    value: Some(code as f64),
                    now_ms,
                });
                state.active_alerts.insert(
                    dedup_key,
                    ActiveAlertMeta {
                        severity: sev.to_string(),
                        fired_at_ms: now_ms,
                    },
                );
                out.new_alerts.push(alert);
            }
        }
    }
}
