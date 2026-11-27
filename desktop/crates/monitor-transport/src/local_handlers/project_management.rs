pub async fn remove_project(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<StatusCode, ApiFailure> {
    let removed = state
        .storage
        .remove_repository(&RepoId::new(id))
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    *state.project_cache.lock().await = None;
    let _ = state.event_tx.send(ServerEvent::ResyncRequired);
    Ok(if removed {
        StatusCode::NO_CONTENT
    } else {
        StatusCode::NOT_FOUND
    })
}

fn is_device_dismissed(storage: &monitor_storage::Storage, id: &str) -> bool {
    storage
        .get_setting(&format!("dismissed_device:{id}"))
        .ok()
        .flatten()
        .is_some_and(|v| v == "1")
}

fn mark_device_dismissed(state: &AppState, id: &str) {
    let _ = state.storage.set_setting(&format!("dismissed_device:{id}"), "1");
    if let Ok(Some(linked_pk)) = state.storage.get_setting(&format!("cloud_device_link:{id}")) {
        let _ = state
            .storage
            .set_setting(&format!("dismissed_device:{linked_pk}"), "1");
    }
    if let Ok(Some(linked_cid)) = state.storage.get_setting(&format!("device_cloud_id:{id}")) {
        let _ = state
            .storage
            .set_setting(&format!("dismissed_device:{linked_cid}"), "1");
    }
}

pub(crate) fn clear_device_dismissed(storage: &monitor_storage::Storage, id: &str) {
    let _ = storage.delete_setting(&format!("dismissed_device:{id}"));
    if let Ok(Some(linked_pk)) = storage.get_setting(&format!("cloud_device_link:{id}")) {
        let _ = storage.delete_setting(&format!("dismissed_device:{linked_pk}"));
    }
    if let Ok(Some(linked_cid)) = storage.get_setting(&format!("device_cloud_id:{id}")) {
        let _ = storage.delete_setting(&format!("dismissed_device:{linked_cid}"));
    }
}

pub async fn revoke_device(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<StatusCode, ApiFailure> {
    let device_id = DeviceId::new(&id);
    // Single revocation path (AUD-004/AUD-007): always permit immediate revocation
    // including currently active LAN peers, then notify live HostRoom & LAN v2 tasks
    // so only the revoked peer's socket is closed cleanly.
    let mut changed = state
        .revoke_identity(device_id.as_str())
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e))?;
    if let Ok(Some(linked_pk)) = state.storage.get_setting(&format!("cloud_device_link:{id}"))
        && let Ok(c) = state.revoke_identity(&linked_pk)
    {
        changed |= c;
    }
    if let Ok(Some(linked_cid)) = state.storage.get_setting(&format!("device_cloud_id:{id}"))
        && let Ok(c) = state.revoke_identity(&linked_cid)
    {
        changed |= c;
    }
    {
        let mut otps = state
            .device_otps
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        otps.remove(&id);
    }
    state
        .ws_tickets
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .retain(|_, ticket| ticket.device_id != device_id);
    Ok(if changed {
        StatusCode::NO_CONTENT
    } else {
        StatusCode::NOT_FOUND
    })
}

#[derive(Debug, Default, Deserialize)]
pub struct GenerateOtpPayload {
    #[serde(default)]
    pub device_name: Option<String>,
}

pub async fn generate_device_otp_handler(
    State(state): State<AppState>,
    Path(id): Path<String>,
    body: Option<Json<GenerateOtpPayload>>,
) -> Result<Json<crate::state::DeviceOtpEntry>, ApiFailure> {
    let requested_name = body
        .and_then(|Json(b)| b.device_name)
        .filter(|n| !n.trim().is_empty());
    let fallback_name = state
        .storage
        .get_viewer_trust(&id)
        .ok()
        .flatten()
        .map(|v| v.user_id)
        .filter(|n| !n.trim().is_empty())
        .or_else(|| {
            state
                .cloud_devices_cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .1
                .iter()
                .find(|d| d.id == id)
                .map(|d| d.name.clone())
        })
        .unwrap_or_else(|| "Android Device".to_string());
    let final_name = requested_name.unwrap_or(fallback_name);
    let entry = state.generate_device_otp(&id, &final_name);
    info!("Generated per-device verification OTP for {} ({})", final_name, id);
    Ok(Json(entry))
}

#[derive(Debug, Deserialize)]
pub struct SetDeviceTierPayload {
    pub tier: String,
}

pub async fn set_device_tier_handler(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(payload): Json<SetDeviceTierPayload>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let target_tier = payload.tier.trim().to_ascii_uppercase();
    if !matches!(target_tier.as_str(), "T2" | "T3") {
        return Err(failure(
            StatusCode::BAD_REQUEST,
            "Trust tier must be 'T2' (Operator) or 'T3' (Admin)",
        ));
    }

    let mut targets = vec![id.clone()];
    if let Ok(Some(linked_pk)) = state.storage.get_setting(&format!("cloud_device_link:{id}"))
        && !targets.contains(&linked_pk)
    {
        targets.push(linked_pk);
    }
    if let Ok(Some(linked_cid)) = state.storage.get_setting(&format!("device_cloud_id:{id}"))
        && !targets.contains(&linked_cid)
    {
        targets.push(linked_cid);
    }

    let mut updated_any = false;
    for tid in &targets {
        let _ = state
            .storage
            .reauthorize_device(&DeviceId::new(tid), DeviceRole::Operator);
        if state
            .storage
            .approve_viewer_tier(tid, &target_tier)
            .unwrap_or(false)
        {
            clear_device_dismissed(&state.storage, tid);
            updated_any = true;
        }
    }

    if !updated_any {
        return Err(failure(
            StatusCode::NOT_FOUND,
            format!("Verified device '{id}' not found. Verify via OTP or QR first."),
        ));
    }
    info!("Updated trust tier for device {id} to {target_tier}");
    Ok(Json(serde_json::json!({
        "device_id": id,
        "trust_tier": target_tier,
    })))
}

pub async fn approve_device(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<StatusCode, ApiFailure> {
    let device_id = DeviceId::new(&id);
    let dev_updated = state
        .storage
        .reauthorize_device(&device_id, DeviceRole::Operator)
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    let viewer_reauth = state
        .storage
        .reauthorize_viewer(&id, "T3")
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    let viewer_promoted = state
        .storage
        .approve_viewer_tier(&id, "T3")
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    if !dev_updated && !viewer_reauth && !viewer_promoted {
        return Err(failure(
            StatusCode::NOT_FOUND,
            format!("Device '{id}' not found"),
        ));
    }
    clear_device_dismissed(&state.storage, &id);
    state.unrevoke_device(&id);
    info!("Approved device {id} to Admin (T3) tier");
    Ok(StatusCode::OK)
}

/// Dismisses and hides a single unverified (`T0`), cloud-synced, or previously revoked device
/// while preserving `AUD-01` local revocation tombstones for any previously issued tokens.
pub async fn dismiss_device_handler(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<StatusCode, ApiFailure> {
    let device_id = DeviceId::new(&id);
    let _ = state.revoke_identity(device_id.as_str());
    if let Ok(Some(linked_pk)) = state.storage.get_setting(&format!("cloud_device_link:{id}")) {
        let _ = state.revoke_identity(&linked_pk);
    }
    if let Ok(Some(linked_cid)) = state.storage.get_setting(&format!("device_cloud_id:{id}")) {
        let _ = state.revoke_identity(&linked_cid);
    }
    mark_device_dismissed(&state, &id);
    {
        let mut otps = state
            .device_otps
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        otps.remove(&id);
    }
    state
        .ws_tickets
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .retain(|_, ticket| ticket.device_id != device_id);
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Debug, Default, Deserialize)]
pub struct ClearStaleDevicesPayload {
    #[serde(default)]
    pub scope: Option<String>,
}

/// Bulk-clears stale unverified (`T0`/cloud) and/or revoked device entries from the tray list
/// without affecting verified active devices or weakening `AUD-01` token revocation tombstones.
pub async fn clear_stale_devices_handler(
    State(state): State<AppState>,
    body: Option<Json<ClearStaleDevicesPayload>>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let scope = body
        .and_then(|Json(b)| b.scope)
        .unwrap_or_else(|| "unverified".to_string());
    let clear_unverified = matches!(scope.as_str(), "unverified" | "all_stale");
    let clear_revoked = matches!(scope.as_str(), "revoked" | "all_stale");
    if !clear_unverified && !clear_revoked {
        return Err(failure(
            StatusCode::BAD_REQUEST,
            "Invalid scope: expected 'unverified', 'revoked', or 'all_stale'",
        ));
    }

    let mut targets: Vec<(String, bool)> = Vec::new();
    if let Ok(devices) = state.storage.list_devices() {
        for d in devices {
            if (clear_unverified && d.is_pending && !d.is_revoked)
                || (clear_revoked && d.is_revoked)
            {
                targets.push((d.device_id.as_str().to_string(), d.is_revoked));
            }
        }
    }
    if let Ok(viewers) = state.storage.list_all_viewers() {
        for v in viewers {
            let is_rev = v.revoked_at_utc_ms.is_some() || state.is_revoked_locally(&v.device_id);
            let is_pend = !is_rev && (v.trust_tier == "T0" || v.trust_tier.is_empty());
            if (clear_unverified && is_pend) || (clear_revoked && is_rev) {
                targets.push((v.device_id, is_rev));
            }
        }
    }
    let cached_cloud = state
        .cloud_devices_cache
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .1
        .clone();
    for cd in cached_cloud {
        if cd.role == "desktop" {
            continue;
        }
        let linked_pk = state
            .storage
            .get_setting(&format!("cloud_device_link:{}", cd.id))
            .ok()
            .flatten();
        let trust_opt = state
            .storage
            .get_viewer_trust(&cd.id)
            .ok()
            .flatten()
            .or_else(|| {
                linked_pk
                    .as_deref()
                    .and_then(|pk| state.storage.get_viewer_trust(pk).ok().flatten())
            });
        let is_rev = state.is_revoked_locally(&cd.id)
            || linked_pk
                .as_deref()
                .is_some_and(|pk| state.is_revoked_locally(pk))
            || trust_opt
                .as_ref()
                .is_some_and(|t| t.revoked_at_utc_ms.is_some());
        let tier = trust_opt
            .map(|t| t.trust_tier)
            .filter(|t| !t.is_empty())
            .unwrap_or_else(|| "T0".to_string());
        let is_pend = !is_rev && tier == "T0";
        if (clear_unverified && is_pend) || (clear_revoked && is_rev) {
            targets.push((cd.id, is_rev));
        }
    }

    targets.sort();
    targets.dedup_by(|a, b| a.0 == b.0);

    let cleared = targets.len();
    for (id, was_revoked) in &targets {
        if !*was_revoked {
            let _ = state.revoke_identity(id);
        }
        mark_device_dismissed(&state, id);
        if let Ok(mut otps) = state.device_otps.lock() {
            otps.remove(id);
        }
    }

    info!("Cleared {cleared} stale device entries (scope={scope})");
    Ok(Json(serde_json::json!({ "cleared": cleared })))
}
