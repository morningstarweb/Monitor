async fn exchange_google_auth_code(
    http_client: &reqwest::Client,
    auth_code: &str,
    code_verifier: &str,
    auth_stream: Option<tokio::net::TcpStream>,
) -> Result<(String, Option<tokio::net::TcpStream>), String> {
    // 1. Primary Enterprise Path: Exchange via Cloudflare Edge Broker (BFF Pattern, Zero-Secret Client)
    let broker_url = format!("{}/api/v1/auth/google-exchange", broker_base_url());
    let exchange_payload = serde_json::json!({
        "code": auth_code,
        "code_verifier": code_verifier,
        "redirect_uri": OAUTH_REDIRECT_URI,
    });

    info!("Exchanging Google auth code via secure Cloudflare Edge Broker...");
    let broker_resp = http_client
        .post(&broker_url)
        .json(&exchange_payload)
        .send()
        .await;

    match broker_resp {
        Ok(resp) if resp.status().is_success() => {
            if let Ok(json) = resp.json::<serde_json::Value>().await
                && let Some(id_tok) = json.get("id_token").and_then(|v| v.as_str()) {
                    info!("Successfully exchanged auth code via Cloudflare Edge Broker");
                    return Ok((id_tok.to_string(), auth_stream));
                }
        }
        Ok(resp) => {
            let status = resp.status();
            let err_text = resp.text().await.unwrap_or_default();
            warn!("Cloudflare broker exchange returned {status}: {err_text}, attempting direct fallback...");
        }
        Err(e) => {
            warn!("Failed to reach Cloudflare broker ({e}), attempting direct fallback...");
        }
    }

    // 2. Resilient Fallback Path: Direct Google OAuth token exchange (Public Client PKCE Flow, RFC 7636)
    let client_secret = std::env::var("GOOGLE_CLIENT_SECRET").ok();

    let mut token_params: Vec<(&str, &str)> = vec![
        ("code", auth_code),
        ("client_id", GOOGLE_CLIENT_ID),
        ("code_verifier", code_verifier),
        ("redirect_uri", OAUTH_REDIRECT_URI),
        ("grant_type", "authorization_code"),
    ];

    if let Some(ref secret) = client_secret {
        let trimmed = secret.trim();
        if !trimmed.is_empty() {
            token_params.push(("client_secret", trimmed));
        }
    }

    let token_resp = match http_client
        .post("https://oauth2.googleapis.com/token")
        .form(&token_params)
        .send()
        .await
    {
        Ok(resp) => resp,
        Err(e) => {
            let err_msg = format!("Failed token exchange request with Google: {e}");
            if let Some(mut stream) = auth_stream {
                let html = format_oauth_error_html(&err_msg);
                let _ = stream.write_all(format!("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
                let _ = stream.flush().await;
            }
            return Err(err_msg);
        }
    };

    if !token_resp.status().is_success() {
        let err_text = token_resp.text().await.unwrap_or_default();
        let err_msg = format!("Google token exchange rejected: {err_text}");
        if let Some(mut stream) = auth_stream {
            let html = format_oauth_error_html(&err_msg);
            let _ = stream.write_all(format!("HTTP/1.1 400 Bad Request\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
            let _ = stream.flush().await;
        }
        return Err(err_msg);
    }

    let token_json: serde_json::Value = match token_resp.json().await {
        Ok(j) => j,
        Err(e) => {
            let err_msg = format!("Invalid JSON response from Google token endpoint: {e}");
            if let Some(mut stream) = auth_stream {
                let html = format_oauth_error_html(&err_msg);
                let _ = stream.write_all(format!("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
                let _ = stream.flush().await;
            }
            return Err(err_msg);
        }
    };

    let Some(id_token) = token_json.get("id_token").and_then(|v| v.as_str()) else {
        let err_msg = "Google response did not contain id_token".to_string();
        if let Some(mut stream) = auth_stream {
            let html = format_oauth_error_html(&err_msg);
            let _ = stream.write_all(format!("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", html.len(), html).as_bytes()).await;
            let _ = stream.flush().await;
        }
        return Err(err_msg);
    };

    Ok((id_token.to_string(), auth_stream))
}
