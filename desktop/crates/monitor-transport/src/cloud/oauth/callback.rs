async fn receive_google_authorization(
    listener: &TcpListener,
    state_token: &str,
) -> Result<(String, tokio::net::TcpStream), String> {
    let deadline = tokio::time::Instant::now() + Duration::from_secs(120);
    while tokio::time::Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        let (mut stream, _) = match tokio::time::timeout(remaining, listener.accept()).await {
            Ok(Ok(pair)) => pair,
            Ok(Err(_)) => continue,
            Err(_) => break,
        };

        let mut buf = [0u8; 4096];
        let Ok(bytes_read) = stream.read(&mut buf).await else {
            continue;
        };

        let request_str = String::from_utf8_lossy(&buf[..bytes_read]);
        if let Some(code) = parse_code_from_request(&request_str, state_token) {
            return Ok((code, stream));
        } else if let Some(error) = parse_error_from_request(&request_str, state_token) {
            let error_html = format_oauth_error_html(&error);
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                error_html.len(),
                error_html
            );
            let _ = stream.write_all(response.as_bytes()).await;
            let _ = stream.flush().await;
            return Err(format!("Google Sign-In canceled or rejected: {error}"));
        } else {
            // Return 404 for unrelated browser probes (e.g. /favicon.ico) and keep waiting for OAuth redirect
            let _ = stream
                .write_all(
                    b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                )
                .await;
        }
    }

    Err("Google Sign-In timed out waiting for browser authorization".to_string())
}
