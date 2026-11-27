async fn post_broker_verify(
    http_client: &reqwest::Client,
    broker_verify_url: &str,
    id_token: &str,
    device_id: &str,
    hostname: &str,
    lan_ip: Option<&str>,
    listen_port: u16,
) -> Result<reqwest::Response, reqwest::Error> {
    let broker_payload = serde_json::json!({
        "id_token": id_token,
        "device_id": device_id,
        "device_name": hostname,
        "platform": "windows",
        "role": "desktop",
        "lan_ip": lan_ip,
        "lan_port": listen_port
    });
    http_client
        .post(broker_verify_url)
        .json(&broker_payload)
        .send()
        .await
}

async fn write_oauth_error_and_fail(
    auth_stream: Option<tokio::net::TcpStream>,
    status_line: &str,
    err_msg: String,
) -> Result<(CloudAccount, Option<tokio::net::TcpStream>), String> {
    if let Some(mut stream) = auth_stream {
        let html = format_oauth_error_html(&err_msg);
        let response = format!(
            "HTTP/1.1 {status_line}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            html.len(),
            html
        );
        let _ = stream.write_all(response.as_bytes()).await;
        let _ = stream.flush().await;
    }
    Err(err_msg)
}

async fn register_with_cloud_broker(
    state: &AppState,
    http_client: &reqwest::Client,
    id_token: &str,
    auth_stream: Option<tokio::net::TcpStream>,
) -> Result<(CloudAccount, Option<tokio::net::TcpStream>), String> {
    register_with_cloud_broker_at(
        &state.storage,
        &state.cloud_account,
        state.listen_port(),
        &broker_base_url(),
        http_client,
        id_token,
        auth_stream,
    )
    .await
}

async fn register_with_cloud_broker_at(
    storage: &Storage,
    cloud_account_slot: &std::sync::Mutex<Option<CloudAccount>>,
    listen_port: u16,
    base_url: &str,
    http_client: &reqwest::Client,
    id_token: &str,
    auth_stream: Option<tokio::net::TcpStream>,
) -> Result<(CloudAccount, Option<tokio::net::TcpStream>), String> {
    let mut device_id = get_or_create_device_id(storage);
    let hostname =
        std::env::var("COMPUTERNAME").unwrap_or_else(|_| "Windows Workstation".to_string());
    let lan_ip = crate::network::discover_local_ipv4().map(|ip| ip.to_string());
    let broker_verify_url = format!("{base_url}/api/v1/auth/google-verify");

    let mut broker_resp = match post_broker_verify(
        http_client,
        &broker_verify_url,
        id_token,
        &device_id,
        &hostname,
        lan_ip.as_deref(),
        listen_port,
    )
    .await
    {
        Ok(resp) => resp,
        Err(e) => {
            return write_oauth_error_and_fail(
                auth_stream,
                "502 Bad Gateway",
                format!("Failed contacting Cloudflare broker: {e}"),
            )
            .await;
        }
    };

    // If this machine's cached device_id is bound to a previous user's account on the broker,
    // rotate to a fresh workstation ID and retry once so account switches succeed cleanly.
    if broker_resp.status() == reqwest::StatusCode::CONFLICT {
        warn!("Workstation device_id belongs to another account; rotating device_id and retrying");
        device_id = rotate_device_id(storage);
        broker_resp = match post_broker_verify(
            http_client,
            &broker_verify_url,
            id_token,
            &device_id,
            &hostname,
            lan_ip.as_deref(),
            listen_port,
        )
        .await
        {
            Ok(resp) => resp,
            Err(e) => {
                return write_oauth_error_and_fail(
                    auth_stream,
                    "502 Bad Gateway",
                    format!("Failed contacting Cloudflare broker: {e}"),
                )
                .await;
            }
        };
    }

    if !broker_resp.status().is_success() {
        let err_text = broker_resp.text().await.unwrap_or_default();
        return write_oauth_error_and_fail(
            auth_stream,
            "403 Forbidden",
            format!("Cloudflare broker verification rejected: {err_text}"),
        )
        .await;
    }

    let broker_json: serde_json::Value = match broker_resp.json().await {
        Ok(j) => j,
        Err(e) => {
            return write_oauth_error_and_fail(
                auth_stream,
                "502 Bad Gateway",
                format!("Invalid JSON from Cloudflare broker: {e}"),
            )
            .await;
        }
    };

    let Some(session_token) = broker_json.get("token").and_then(|v| v.as_str()) else {
        return write_oauth_error_and_fail(
            auth_stream,
            "500 Internal Server Error",
            "Broker response missing session token".to_string(),
        )
        .await;
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

    if let Ok(serialized) = serde_json::to_string(&account) {
        let _ = storage.set_setting("cloud_account", &serialized);
    }

    {
        let mut guard = cloud_account_slot
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        *guard = Some(account.clone());
    }

    Ok((account, auth_stream))
}
