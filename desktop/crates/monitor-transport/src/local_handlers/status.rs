#[derive(Debug, Serialize)]
pub struct LocalStatus {
    pub connected_clients: usize,
    pub snapshot: SystemSnapshot,
    pub projects: Vec<RepoSummary>,
    pub devices: Vec<StoredDevice>,
    pub addresses: Vec<String>,
    pub pairing_code: Option<crate::state::ActivePairingCode>,
    pub cloud_account: Option<monitor_core::CloudAccountSummary>,
    pub git_access_allowed: bool,
    pub delete_access_allowed: bool,
    pub cloud_access_allowed: bool,
    pub local_access_allowed: bool,
    pub terminal_access_allowed: bool,
    pub checkpoint_access_allowed: bool,
    pub active_agent_sessions: Vec<monitor_storage::StoredAgentSession>,
    pub alerts: Vec<monitor_storage::StoredAlert>,
    pub listen_port: u16,
    #[serde(default)]
    pub host_cloud_device_id: Option<String>,
}

fn spawn_cloud_devices_refresh_if_stale(state: &AppState) {
    let maybe_token = state
        .cloud_account
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
        .map(|a| a.token.clone());
    let Some(token) = maybe_token else {
        return;
    };
    let is_stale = state
        .cloud_devices_cache
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .0
        .map(|at| at.elapsed() > std::time::Duration::from_secs(8))
        .unwrap_or(true);
    if !is_stale {
        return;
    }
    if state
        .cloud_devices_refreshing
        .compare_exchange(
            false,
            true,
            std::sync::atomic::Ordering::AcqRel,
            std::sync::atomic::Ordering::Acquire,
        )
        .is_err()
    {
        return;
    }
    let state_clone = state.clone();
    tokio::spawn(async move {
        let _guard = scopeguard_reset(&state_clone.cloud_devices_refreshing);
        if let Ok(client) = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(6))
            .build()
        {
            let url = format!("{}/api/v1/devices", crate::cloud::broker_base_url());
            if let Ok(resp) = client.get(&url).bearer_auth(&token).send().await
                && resp.status().is_success()
                && let Ok(val) = resp.json::<serde_json::Value>().await
                && let Some(arr) = val.get("devices")
                && let Ok(parsed) = serde_json::from_value::<Vec<crate::state::CloudDeviceInfo>>(arr.clone())
            {
                let mut cache = state_clone
                    .cloud_devices_cache
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                *cache = (Some(std::time::Instant::now()), parsed);
            }
        }
    });
}

fn scopeguard_reset(flag: &std::sync::Arc<std::sync::atomic::AtomicBool>) -> RefreshFlagGuard {
    RefreshFlagGuard(std::sync::Arc::clone(flag))
}

struct RefreshFlagGuard(std::sync::Arc<std::sync::atomic::AtomicBool>);

impl Drop for RefreshFlagGuard {
    fn drop(&mut self) {
        self.0.store(false, std::sync::atomic::Ordering::Release);
    }
}

pub async fn get_local_status(
    State(state): State<AppState>,
) -> Result<Json<LocalStatus>, ApiFailure> {
    spawn_cloud_devices_refresh_if_stale(&state);
    let projects = {
        let cache = state.project_cache.lock().await;
        cache.as_ref().map(|(_, p)| p.clone())
    };
    let projects = match projects {
        Some(p) => {
            let state_clone = state.clone();
            tokio::spawn(async move {
                let is_stale = {
                    let cache = state_clone.project_cache.lock().await;
                    cache.as_ref().map(|(at, _)| at.elapsed() > std::time::Duration::from_secs(4)).unwrap_or(true)
                };
                if is_stale {
                    let _ = refresh_and_broadcast_repositories(&state_clone).await;
                }
            });
            p
        }
        None => {
            match tokio::time::timeout(std::time::Duration::from_secs(2), list_project_summaries(&state)).await {
                Ok(Ok(p)) => p,
                _ => Vec::new(),
            }
        }
    };
    let snapshot = state.telemetry.sample_system_snapshot();
    let mut devices = state
        .storage
        .list_devices()
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    if let Ok(viewers) = state.storage.list_all_viewers() {
        for viewer in viewers {
            let dev_id = DeviceId::new(&viewer.device_id);
            let linked_cid = state
                .storage
                .get_setting(&format!("device_cloud_id:{}", viewer.device_id))
                .ok()
                .flatten();
            let is_revoked = viewer.revoked_at_utc_ms.is_some()
                || state.is_revoked_locally(&viewer.device_id)
                || linked_cid
                    .as_deref()
                    .is_some_and(|cid| state.is_revoked_locally(cid));
            let is_pending =
                !is_revoked && (viewer.trust_tier == "T0" || viewer.trust_tier.is_empty());
            let role = match viewer.trust_tier.as_str() {
                "T3" | "Admin" => "Admin",
                "T2" | "Operator" => "Operator",
                "T1" => "Member",
                _ => "Unverified",
            }
            .to_string();
            let tier_norm = if viewer.trust_tier.is_empty() {
                "T0".to_string()
            } else {
                viewer.trust_tier.clone()
            };
            let otp_entry = state
                .get_active_device_otp(&viewer.device_id)
                .or_else(|| linked_cid.as_deref().and_then(|cid| state.get_active_device_otp(cid)));

            if let Some(existing) = devices.iter_mut().find(|d| d.device_id == dev_id) {
                if is_revoked {
                    existing.is_revoked = true;
                    existing.is_pending = false;
                } else {
                    existing.role = role.clone();
                    existing.is_pending = is_pending;
                }
                existing.trust_tier = tier_norm;
                existing.active_otp = otp_entry.as_ref().map(|e| e.code.clone());
                existing.otp_expires_at_ms = otp_entry.as_ref().map(|e| e.expires_at_ms);
                if (existing.display_name == "local_lan_viewer" || existing.display_name == "Cloud Android Phone")
                    && !viewer.user_id.is_empty()
                    && viewer.user_id != "local_lan_viewer"
                {
                    existing.display_name = viewer.user_id.clone();
                }
            } else {
                let display_name = if !viewer.user_id.is_empty() {
                    viewer.user_id
                } else {
                    format!(
                        "Mobile ({})",
                        viewer.device_id.chars().take(6).collect::<String>()
                    )
                };
                devices.push(StoredDevice {
                    device_id: dev_id,
                    display_name,
                    role,
                    last_seen_utc_ms: viewer
                        .approved_at_utc_ms
                        .or(Some(viewer.enrolled_at_utc_ms)),
                    is_revoked,
                    is_local: false,
                    is_pending,
                    trust_tier: tier_norm,
                    active_otp: otp_entry.as_ref().map(|e| e.code.clone()),
                    otp_expires_at_ms: otp_entry.as_ref().map(|e| e.expires_at_ms),
                    platform: "android".to_string(),
                });
            }
        }
    }

    // Merge cloud-registered client devices under the same Google account (deduplicated by cloud link or id)
    let cached_cloud_devices = state
        .cloud_devices_cache
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .1
        .clone();
    for cd in cached_cloud_devices {
        if cd.role == "desktop" {
            continue;
        }
        let linked_pk = state
            .storage
            .get_setting(&format!("cloud_device_link:{}", cd.id))
            .ok()
            .flatten();
        let matched = devices.iter_mut().find(|d| {
            d.device_id.as_str() == cd.id
                || linked_pk.as_deref() == Some(d.device_id.as_str())
        });
        let cloud_otp = state.get_active_device_otp(&cd.id);
        if let Some(existing) = matched {
            if !cd.name.trim().is_empty()
                && (existing.display_name == "Android Phone"
                    || existing.display_name == "Cloud Android Phone"
                    || existing.display_name == "local_lan_viewer")
            {
                existing.display_name = cd.name.clone();
            }
            if !cd.platform.is_empty() {
                existing.platform = cd.platform.clone();
            }
            if existing.active_otp.is_none()
                && let Some(ref otp) = cloud_otp
            {
                existing.active_otp = Some(otp.code.clone());
                existing.otp_expires_at_ms = Some(otp.expires_at_ms);
            }
        } else {
            let trust_opt = state.storage.get_viewer_trust(&cd.id).ok().flatten();
            let is_revoked = state.is_revoked_locally(&cd.id)
                || trust_opt.as_ref().is_some_and(|t| t.revoked_at_utc_ms.is_some());
            let tier = trust_opt
                .map(|t| t.trust_tier)
                .filter(|t| !t.is_empty())
                .unwrap_or_else(|| "T0".to_string());
            let is_pending = !is_revoked && tier == "T0";
            let role = match tier.as_str() {
                "T3" => "Admin",
                "T2" => "Operator",
                "T1" => "Member",
                _ => "Unverified",
            }
            .to_string();
            devices.push(StoredDevice {
                device_id: DeviceId::new(&cd.id),
                display_name: if cd.name.trim().is_empty() {
                    "Android Device".to_string()
                } else {
                    cd.name
                },
                role,
                last_seen_utc_ms: if cd.last_heartbeat > 0 {
                    Some(cd.last_heartbeat * 1000)
                } else {
                    None
                },
                is_revoked,
                is_local: false,
                is_pending,
                trust_tier: tier,
                active_otp: cloud_otp.as_ref().map(|e| e.code.clone()),
                otp_expires_at_ms: cloud_otp.as_ref().map(|e| e.expires_at_ms),
                platform: if cd.platform.is_empty() {
                    "android".to_string()
                } else {
                    cd.platform
                },
            });
        }
    }

    for d in &mut devices {
        if state.is_active_lan_device(d.device_id.as_str()) {
            d.is_local = true;
        }
        if d.active_otp.is_none()
            && let Some(otp) = state.get_active_device_otp(d.device_id.as_str())
        {
            d.active_otp = Some(otp.code);
            d.otp_expires_at_ms = Some(otp.expires_at_ms);
        }
        if state.is_revoked_locally(d.device_id.as_str()) {
            d.is_revoked = true;
            d.is_pending = false;
        }
    }
    let pairing_code = state
        .pairing_code
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .clone()
        .filter(|code| code.expires_at_ms > chrono::Utc::now().timestamp_millis());
    let addresses = state
        .addresses
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .clone();
    let (cloud_account, host_cloud_device_id) = {
        let guard = state
            .cloud_account
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        (
            guard.as_ref().map(|account| monitor_core::CloudAccountSummary {
                email: account.email.clone(),
            }),
            guard.as_ref().map(|account| account.device_id.clone()),
        )
    };
    // AUD-04: all permission reads fail closed so the UI can never display a
    // more permissive state than the backend enforces.
    let git_access_allowed = state
        .storage
        .get_setting("allow_git_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let delete_access_allowed = state
        .storage
        .get_setting("allow_delete_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let cloud_access_allowed = state
        .storage
        .get_setting("allow_cloud_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let local_access_allowed = state
        .storage
        .get_setting("allow_local_network")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let terminal_access_allowed = state
        .storage
        .get_setting("allow_remote_terminal")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let checkpoint_access_allowed = state
        .storage
        .get_setting("allow_checkpoint_rollback")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let mut active_agent_sessions = state
        .storage
        .get_active_agent_sessions()
        .unwrap_or_default();
    let now_ms = chrono::Utc::now().timestamp_millis();
    active_agent_sessions.retain(|s| {
        if s.pid > 0 && !state.telemetry.is_pid_alive(s.pid) {
            let _ = state.storage.end_agent_session(&s.session_id, Some(0), now_ms);
            false
        } else {
            true
        }
    });
    let alerts = state
        .storage
        .list_alerts(15)
        .unwrap_or_default();
    let live_clients = state.active_client_count.load(std::sync::atomic::Ordering::Relaxed);
    Ok(Json(LocalStatus {
        connected_clients: live_clients,
        snapshot,
        projects,
        devices,
        addresses,
        pairing_code,
        cloud_account,
        git_access_allowed,
        delete_access_allowed,
        cloud_access_allowed,
        local_access_allowed,
        terminal_access_allowed,
        checkpoint_access_allowed,
        active_agent_sessions,
        alerts,
        listen_port: state.listen_port(),
        host_cloud_device_id,
    }))
}
