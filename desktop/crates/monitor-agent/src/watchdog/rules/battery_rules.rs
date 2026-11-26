use super::super::signals::SessionActivityTracker;
use super::{ActiveAlertMeta, EvaluationResult, NewAlertSpec, RuleEngineState, build_alert};
use monitor_core::{AcPowerStatus, SystemSnapshot};
use monitor_storage::StoredAlertRule;
use std::collections::HashMap;

pub(super) fn evaluate_battery_rules(
    rules_map: &HashMap<&str, &StoredAlertRule>,
    sessions: &HashMap<String, SessionActivityTracker>,
    telemetry: &SystemSnapshot,
    state: &mut RuleEngineState,
    now_ms: i64,
    out: &mut EvaluationResult,
) {
    let battery = &telemetry.battery;
    let is_on_battery = battery.ac_status == AcPowerStatus::Disconnected;
    let has_active_agent = sessions.values().any(|s| s.current_state == "Active");

    // Rule: battery.low
    if let Some(rule) = rules_map.get("battery.low")
        && rule.enabled
    {
        let dedup_key = "battery.low:host".to_string();
        if is_on_battery {
            let pct = battery.percentage.unwrap_or(100.0);
            let is_critical = pct <= 10.0;
            let is_warning = pct <= 20.0;

            if is_warning || is_critical {
                let target_sev = if is_critical { "critical" } else { "warning" };
                let can_fire = match state.active_alerts.get(&dedup_key) {
                    None => true,
                    Some(meta) => {
                        (meta.severity != "critical" && is_critical)
                            || (now_ms - meta.fired_at_ms >= (rule.cooldown_s as i64 * 1000))
                    }
                };
                if can_fire {
                    let alert = build_alert(NewAlertSpec {
                        rule_kind: "battery.low",
                        severity: target_sev,
                        title: if is_critical {
                            "Battery Critical"
                        } else {
                            "Battery Low"
                        }
                        .to_string(),
                        body: format!("{pct:.0}% remaining on battery"),
                        dedup_key: dedup_key.clone(),
                        session_id: None,
                        value: Some(pct as f64),
                        now_ms,
                    });
                    state.active_alerts.insert(
                        dedup_key,
                        ActiveAlertMeta {
                            severity: target_sev.to_string(),
                            fired_at_ms: now_ms,
                        },
                    );
                    out.new_alerts.push(alert);
                }
            } else if pct >= 25.0 && state.active_alerts.contains_key(&dedup_key) {
                state.active_alerts.remove(&dedup_key);
                out.recovered_dedup_keys.push(dedup_key);
            }
        } else if state.active_alerts.contains_key(&dedup_key) {
            state.active_alerts.remove(&dedup_key);
            out.recovered_dedup_keys.push(dedup_key);
        }
    }

    // Rule: battery.unplugged (debounce 60s if agent is active)
    if let Some(rule) = rules_map.get("battery.unplugged")
        && rule.enabled
    {
        let dedup_key = "battery.unplugged:host".to_string();
        if is_on_battery && has_active_agent {
            let start = state.unplugged_since_ms.get_or_insert(now_ms);
            if (now_ms - *start) >= 60_000 && !state.active_alerts.contains_key(&dedup_key) {
                let alert = build_alert(NewAlertSpec {
                    rule_kind: "battery.unplugged",
                    severity: "info",
                    title: "AC Power Disconnected".to_string(),
                    body: "Machine is running on battery while an agent is active".to_string(),
                    dedup_key: dedup_key.clone(),
                    session_id: None,
                    value: None,
                    now_ms,
                });
                state.active_alerts.insert(
                    dedup_key,
                    ActiveAlertMeta {
                        severity: "info".to_string(),
                        fired_at_ms: now_ms,
                    },
                );
                out.new_alerts.push(alert);
            }
        } else {
            state.unplugged_since_ms = None;
            if state.active_alerts.contains_key(&dedup_key) {
                state.active_alerts.remove(&dedup_key);
                out.recovered_dedup_keys.push(dedup_key);
            }
        }
    }
}
