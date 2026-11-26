use super::{ActiveAlertMeta, EvaluationResult, NewAlertSpec, RuleEngineState, build_alert};
use monitor_core::SystemSnapshot;
use monitor_storage::StoredAlertRule;
use std::collections::HashMap;

pub(super) fn evaluate_resource_rules(
    rules_map: &HashMap<&str, &StoredAlertRule>,
    telemetry: &SystemSnapshot,
    state: &mut RuleEngineState,
    now_ms: i64,
    out: &mut EvaluationResult,
) {
    // Rule: memory.high
    if let Some(rule) = rules_map.get("memory.high")
        && rule.enabled
    {
        let dedup_key = "memory.high:host".to_string();
        let ram_pct = telemetry.ram.usage_percent;
        let is_over_95 = ram_pct >= 95.0;
        let is_over_90 = ram_pct >= 90.0;

        if is_over_95 {
            let start = state.mem_critical_since_ms.get_or_insert(now_ms);
            let is_critical_sustained = (now_ms - *start) >= 60_000;
            let target_sev = if is_critical_sustained {
                "critical"
            } else {
                "warning"
            };

            let can_fire = match state.active_alerts.get(&dedup_key) {
                None => true,
                Some(meta) => meta.severity != "critical" && is_critical_sustained,
            };

            if can_fire {
                let alert = build_alert(NewAlertSpec {
                    rule_kind: "memory.high",
                    severity: target_sev,
                    title: format!(
                        "Memory {}",
                        if target_sev == "critical" {
                            "Critical"
                        } else {
                            "High"
                        }
                    ),
                    body: format!("RAM usage at {ram_pct:.1}%"),
                    dedup_key: dedup_key.clone(),
                    session_id: None,
                    value: Some(ram_pct as f64),
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
        } else if is_over_90 {
            state.mem_critical_since_ms = None;
            if let std::collections::hash_map::Entry::Vacant(e) =
                state.active_alerts.entry(dedup_key.clone())
            {
                let alert = build_alert(NewAlertSpec {
                    rule_kind: "memory.high",
                    severity: "warning",
                    title: "Memory High".to_string(),
                    body: format!("RAM usage at {ram_pct:.1}%"),
                    dedup_key,
                    session_id: None,
                    value: Some(ram_pct as f64),
                    now_ms,
                });
                e.insert(ActiveAlertMeta {
                    severity: "warning".to_string(),
                    fired_at_ms: now_ms,
                });
                out.new_alerts.push(alert);
            }
        } else if ram_pct < 85.0 {
            state.mem_critical_since_ms = None;
            if state.active_alerts.remove(&dedup_key).is_some() {
                out.recovered_dedup_keys.push(dedup_key);
            }
        }
    }

    // Rule: disk.low
    if let Some(rule) = rules_map.get("disk.low")
        && rule.enabled
        && !telemetry.disks.is_empty()
    {
        let dedup_key = "disk.low:host".to_string();
        let min_avail_gb = telemetry
            .disks
            .iter()
            .map(|d| d.available_bytes as f64 / 1_073_741_824.0)
            .fold(f64::INFINITY, f64::min);

        let is_critical = min_avail_gb < 2.0;
        let is_warning = min_avail_gb < 5.0;

        if is_critical || is_warning {
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
                    rule_kind: "disk.low",
                    severity: target_sev,
                    title: format!(
                        "Disk Space {}",
                        if is_critical { "Critical" } else { "Low" }
                    ),
                    body: format!("{min_avail_gb:.1} GB remaining on disk"),
                    dedup_key: dedup_key.clone(),
                    session_id: None,
                    value: Some(min_avail_gb),
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
        } else if min_avail_gb >= 6.0 && state.active_alerts.contains_key(&dedup_key) {
            state.active_alerts.remove(&dedup_key);
            out.recovered_dedup_keys.push(dedup_key);
        }
    }
}
