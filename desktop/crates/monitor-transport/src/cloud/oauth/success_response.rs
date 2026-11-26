async fn send_oauth_success_response(auth_stream: Option<tokio::net::TcpStream>, email: &str) {
    if let Some(mut stream) = auth_stream {
        let success_html = format_oauth_success_html(email);
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            success_html.len(),
            success_html
        );
        let _ = stream.write_all(response.as_bytes()).await;
        let _ = stream.flush().await;
    }
}
