/// Verifies a client token with Cloudflare Broker when an unrecognised Bearer token arrives.
/// If verified as belonging to the same Google account, automatically pairs the device locally.
pub async fn verify_remote_client_token(
    state: &AppState,
    client_token: &str,
) -> Option<(monitor_core::DeviceId, monitor_core::DeviceRole)> {
    let maybe_account = {
        let guard = state
            .cloud_account
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        guard.clone()
    };
    let account = maybe_account?;

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(5))
        .build()
        .ok()?;

    let url = format!("{}/api/v1/auth/verify-client", broker_base_url());
    let resp = client
        .post(&url)
        .header("Authorization", format!("Bearer {}", account.token))
        .json(&serde_json::json!({ "client_token": client_token }))
        .send()
        .await
        .ok()?;

    if !resp.status().is_success() {
        return None;
    }

    let body: serde_json::Value = resp.json().await.ok()?;
    if body.get("valid").and_then(serde_json::Value::as_bool) == Some(true) {
        let dev_id_str = body
            .get("device_id")
            .and_then(|v| v.as_str())
            .unwrap_or("android_cloud");
        if state.is_revoked_locally(dev_id_str) {
            return None;
        }
        // Only treat a cloud client as a verified local Operator if it already completed
        // per-device OTP / QR verification (`T2` or `T3`) via either its cloud ID or linked Noise key.
        let linked_pk = state
            .storage
            .get_setting(&format!("cloud_device_link:{dev_id_str}"))
            .ok()
            .flatten();
        let trusted = [Some(dev_id_str), linked_pk.as_deref()]
            .into_iter()
            .flatten()
            .filter_map(|id| state.storage.get_viewer_trust(id).ok().flatten())
            .any(|t| {
                t.revoked_at_utc_ms.is_none()
                    && matches!(t.trust_tier.as_str(), "T2" | "T3")
            });
        if !trusted {
            warn!("Rejected LAN REST upgrade for unverified cloud device {dev_id_str}; OTP/QR verification required");
            return None;
        }

        let dev_id = monitor_core::DeviceId::new(dev_id_str);
        let token_hash = crate::auth::TokenAuthenticator::hash_token(client_token);
        if let Err(error) = state.storage.set_verified_device_credential(
            &dev_id,
            "Cloud Android Phone",
            &token_hash,
            monitor_core::DeviceRole::Operator,
        ) {
            warn!("Cloud-verified client could not be paired locally: {error}");
            return None;
        }
        info!("Successfully validated verified cloud client device {dev_id_str}");
        Some((dev_id, monitor_core::DeviceRole::Operator))
    } else {
        None
    }
}
