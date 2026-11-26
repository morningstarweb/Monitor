pub async fn disconnect_cloud(state: AppState) -> Result<(), String> {
    let maybe_account = {
        let mut guard = state
            .cloud_account
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        guard.take()
    };

    let _ = state.storage.delete_setting("cloud_account");

    if let Some(account) = maybe_account {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .map_err(|e| e.to_string())?;

        let offline_url = format!("{}/api/v1/devices/offline", broker_base_url());
        let _ = client
            .post(&offline_url)
            .header("Authorization", format!("Bearer {}", account.token))
            .send()
            .await;

        let logout_url = format!("{}/api/v1/auth/logout", broker_base_url());
        match client
            .post(&logout_url)
            .header("Authorization", format!("Bearer {}", account.token))
            .send()
            .await
        {
            Ok(response) if response.status().is_success() => {}
            Ok(response) => warn!("Cloud broker logout returned HTTP {}", response.status()),
            Err(error) => warn!("Cloud broker logout could not be reached: {error}"),
        }
    }

    info!("Desktop disconnected from Monitor Cloud.");
    Ok(())
}
