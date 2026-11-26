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
    state.unrevoke_device(&id);
    info!("Approved device {id} to Admin (T3) tier");
    Ok(StatusCode::OK)
}
