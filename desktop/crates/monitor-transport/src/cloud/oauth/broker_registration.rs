async fn register_with_cloud_broker(
    state: &AppState,
    http_client: &reqwest::Client,
    id_token: &str,
    auth_stream: Option<tokio::net::TcpStream>,
) -> Result<(CloudAccount, Option<tokio::net::TcpStream>), String> {
    // Register with Cloudflare broker
    let device_id = get_or_create_device_id(&state.storage);
    let hostname =
        std::env::var("COMPUTERNAME").unwrap_or_else(|_| "Windows Workstation".to_string());
    let lan_ip = crate::network::discover_local_ipv4().map(|ip| ip.to_string());

    let broker_verify_url = format!("{}/api/v1/auth/google-verify", broker_base_url());
    let broker_payload = serde_json::json!({
        "id_token": id_token,
        "device_id": device_id,
        "device_name": hostname,
        "platform": "windows",
        "role": "desktop",
        "lan_ip": lan_ip,
        "lan_port": state.listen_port()
    });

    let broker_resp = match http_client
        .post(&broker_verify_url)
        .json(&broker_payload)
        .send()
        .await
    {
        Ok(resp) => resp,
        Err(e) => {
            let err_msg = format!("Failed contacting Cloudflare broker: {e}");
            if let Some(mut stream) = auth_stream {
                let html = format_oauth_error_html(&err_msg);
                let _ = stream.write_all(format!("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
                let _ = stream.flush().await;
            }
            return Err(err_msg);
        }
    };

    if !broker_resp.status().is_success() {
        let err_text = broker_resp.text().await.unwrap_or_default();
        let err_msg = format!("Cloudflare broker verification rejected: {err_text}");
        if let Some(mut stream) = auth_stream {
            let html = format_oauth_error_html(&err_msg);
            let _ = stream.write_all(format!("HTTP/1.1 403 Forbidden\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
            let _ = stream.flush().await;
        }
        return Err(err_msg);
    }

    let broker_json: serde_json::Value = match broker_resp.json().await {
        Ok(j) => j,
        Err(e) => {
            let err_msg = format!("Invalid JSON from Cloudflare broker: {e}");
            if let Some(mut stream) = auth_stream {
                let html = format_oauth_error_html(&err_msg);
                let _ = stream.write_all(format!("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
                let _ = stream.flush().await;
            }
            return Err(err_msg);
        }
    };

    let Some(session_token) = broker_json.get("token").and_then(|v| v.as_str()) else {
        let err_msg = "Broker response missing session token".to_string();
        if let Some(mut stream) = auth_stream {
            let html = format_oauth_error_html(&err_msg);
            let _ = stream.write_all(format!("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
            let _ = stream.flush().await;
        }
        return Err(err_msg);
    };
    let session_token = session_token.to_string();

    let email = broker_json
        .get("user")
        .and_then(|u| u.get("email"))
        .and_then(|v| v.as_str())
        .unwrap_or("google-user")
        .to_string();

    let name = broker_json
        .get("user")
        .and_then(|u| u.get("name"))
        .and_then(|v| v.as_str())
        .unwrap_or(&email)
        .to_string();

    let account = CloudAccount {
        email,
        name,
        token: session_token,
        device_id,
        logged_in_at_utc_ms: chrono::Utc::now().timestamp_millis(),
    };

    // Save locally
    if let Ok(serialized) = serde_json::to_string(&account) {
        let _ = state.storage.set_setting("cloud_account", &serialized);
    }

    // Update in-memory state
    {
        let mut guard = state
            .cloud_account
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        *guard = Some(account.clone());
    }

    Ok((account, auth_stream))
}
