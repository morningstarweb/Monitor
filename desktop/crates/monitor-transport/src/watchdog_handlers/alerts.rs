#[derive(Debug, Deserialize)]
pub struct ListAlertsQuery {
    pub limit: Option<u32>,
    pub active_only: Option<bool>,
}

pub async fn list_alerts_handler(
    State(state): State<AppState>,
    Query(query): Query<ListAlertsQuery>,
) -> Result<Json<Vec<AlertItem>>, StatusCode> {
    let limit = query.limit.unwrap_or(50);
    let active_only = query.active_only.unwrap_or(false);
    let now_ms = chrono::Utc::now().timestamp_millis();

    let alerts = state
        .storage
        .list_alerts(limit)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let filtered: Vec<AlertItem> = alerts
        .into_iter()
        .filter(|a| {
            if !active_only {
                return true;
            }
            // Active: not recovered, not snoozed in the future
            if a.recovered_at_utc_ms.is_some() {
                return false;
            }
            if let Some(snoozed_until) = a.snoozed_until_utc_ms
                && snoozed_until > now_ms
            {
                return false;
            }
            true
        })
        .map(|a| AlertItem {
            alert_id: a.alert_id,
            rule_kind: a.rule_kind,
            severity: a.severity,
            title: a.title,
            body: a.body,
            dedup_key: a.dedup_key,
            session_id: a.session_id,
            value: a.value,
            created_at_utc_ms: a.created_at_utc_ms,
            delivered_at_utc_ms: a.delivered_at_utc_ms,
            acked_at_utc_ms: a.acked_at_utc_ms,
            snoozed_until_utc_ms: a.snoozed_until_utc_ms,
            recovered_at_utc_ms: a.recovered_at_utc_ms,
        })
        .collect();

    Ok(Json(filtered))
}

pub(crate) async fn ack_alert_impl(state: &AppState, alert_id: &str) -> Result<Value, StatusCode> {
    let now_ms = chrono::Utc::now().timestamp_millis();
    let updated = state
        .storage
        .ack_alert(alert_id, now_ms)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    if !updated {
        return Err(StatusCode::NOT_FOUND);
    }

    Ok(json!({
        "success": true,
        "alert_id": alert_id,
        "acked_at_utc_ms": now_ms,
    }))
}

pub async fn ack_alert_handler(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(alert_id): AxumPath<String>,
) -> Result<Json<Value>, StatusCode> {
    if device.role == DeviceRole::Viewer {
        return Err(StatusCode::FORBIDDEN);
    }
    Ok(Json(ack_alert_impl(&state, &alert_id).await?))
}

pub(crate) async fn snooze_alert_impl(
    state: &AppState,
    alert_id: &str,
    duration_s: u32,
) -> Result<Value, StatusCode> {
    let now_ms = chrono::Utc::now().timestamp_millis();
    let snoozed_until = now_ms + (duration_s as i64 * 1000);

    let updated = state
        .storage
        .snooze_alert(alert_id, snoozed_until)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    if !updated {
        return Err(StatusCode::NOT_FOUND);
    }

    Ok(json!({
        "success": true,
        "alert_id": alert_id,
        "snoozed_until_utc_ms": snoozed_until,
    }))
}

pub async fn snooze_alert_handler(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(alert_id): AxumPath<String>,
    Json(payload): Json<SnoozeAlertRequest>,
) -> Result<Json<Value>, StatusCode> {
    if device.role == DeviceRole::Viewer {
        return Err(StatusCode::FORBIDDEN);
    }
    Ok(Json(
        snooze_alert_impl(&state, &alert_id, payload.duration_s).await?,
    ))
}

pub async fn list_agent_sessions_handler(
    State(state): State<AppState>,
) -> Result<Json<Vec<AgentSessionSummary>>, StatusCode> {
    let sessions = state
        .storage
        .get_active_agent_sessions()
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let summaries = sessions
        .into_iter()
        .map(|s| AgentSessionSummary {
            session_id: s.session_id,
            profile_name: s.profile_name,
            pid: s.pid,
            repo_path: s.repo_path,
            state: s.state,
            started_at_utc_ms: s.started_at_utc_ms,
            last_activity_utc_ms: s.last_activity_utc_ms,
            ended_at_utc_ms: s.ended_at_utc_ms,
            exit_code: s.exit_code,
        })
        .collect();

    Ok(Json(summaries))
}

pub async fn list_alert_rules_handler(
    State(state): State<AppState>,
) -> Result<Json<Vec<AlertRuleConfig>>, StatusCode> {
    let rules = state
        .storage
        .list_alert_rules()
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let configs = rules
        .into_iter()
        .map(|r| AlertRuleConfig {
            kind: r.kind,
            enabled: r.enabled,
            severity: r.severity,
            threshold: r.threshold,
            for_duration_s: r.for_duration_s,
            cooldown_s: r.cooldown_s,
        })
        .collect();

    Ok(Json(configs))
}

pub async fn update_alert_rule_handler(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(kind): AxumPath<String>,
    Json(rule): Json<AlertRuleConfig>,
) -> Result<Json<Value>, StatusCode> {
    if device.role == DeviceRole::Viewer {
        return Err(StatusCode::FORBIDDEN);
    }
    let stored = StoredAlertRule {
        kind: kind.clone(),
        enabled: rule.enabled,
        severity: rule.severity,
        threshold: rule.threshold,
        for_duration_s: rule.for_duration_s,
        cooldown_s: rule.cooldown_s,
    };

    state
        .storage
        .update_alert_rule(&stored)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    Ok(Json(json!({
        "success": true,
        "kind": kind,
    })))
}

pub async fn list_agent_profiles_handler(
    State(state): State<AppState>,
) -> Result<Json<Vec<AgentProfileConfig>>, StatusCode> {
    let profiles = state
        .storage
        .list_agent_profiles()
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let configs = profiles
        .into_iter()
        .map(|p| AgentProfileConfig {
            name: p.name,
            matchers: p.matchers,
            stall_threshold_s: p.stall_threshold_s,
            restart_command: p.restart_command,
            keep_running: p.keep_running,
            max_retries: p.max_retries,
        })
        .collect();

    Ok(Json(configs))
}
