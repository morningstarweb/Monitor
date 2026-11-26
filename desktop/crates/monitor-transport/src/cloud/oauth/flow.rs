/// Performs the complete Google OAuth flow with loopback listener on port 9481.
pub async fn run_google_login_flow(state: AppState) -> Result<CloudAccount, String> {
    let listener = TcpListener::bind("127.0.0.1:9481")
        .await
        .map_err(|e| format!("Cannot bind OAuth listener on port 9481: {e}"))?;

    let (code_verifier, code_challenge) = generate_pkce_verifier_and_challenge();
    let state_token = format!("{:x}", rand::random::<u128>());
    let redirect_uri_enc = urlencoding_encode(OAUTH_REDIRECT_URI);
    let scope_enc = urlencoding_encode("openid email profile");
    let challenge_enc = urlencoding_encode(&code_challenge);
    let auth_url = format!(
        "https://accounts.google.com/o/oauth2/v2/auth?client_id={GOOGLE_CLIENT_ID}&redirect_uri={redirect_uri_enc}&response_type=code&scope={scope_enc}&state={state_token}&code_challenge={challenge_enc}&code_challenge_method=S256&prompt=select_account"
    );

    info!("Generating OAuth launcher trampoline HTML for clean browser navigation...");
    let launcher_html = format_oauth_launcher_html(&auth_url);
    let launcher_path = std::env::temp_dir().join("monitor_oauth_launcher.html");
    let _ = std::fs::write(&launcher_path, launcher_html);
    launch_browser_safely(&launcher_path);

    let (auth_code, stream) = receive_google_authorization(&listener, &state_token).await?;
    let http_client = reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .build()
        .map_err(|e| format!("Failed to build HTTP client: {e}"))?;
    let (id_token, auth_stream) =
        exchange_google_auth_code(&http_client, &auth_code, &code_verifier, Some(stream)).await?;
    let (account, auth_stream) =
        register_with_cloud_broker(&state, &http_client, &id_token, auth_stream).await?;
    send_oauth_success_response(auth_stream, &account.email).await;

    info!(
        "Desktop successfully authenticated with Monitor Cloud: {}",
        account.email
    );
    Ok(account)
}
