static OAUTH_RUNNING: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

fn launch_browser_safely(path: &std::path::Path) {
    if std::process::Command::new("rundll32.exe")
        .args(["url.dll,FileProtocolHandler", &path.to_string_lossy()])
        .spawn()
        .is_err()
    {
        let _ = std::process::Command::new("explorer.exe").arg(path).spawn();
    }
}

/// Triggers Google OAuth in the background. Spawns an async task and returns immediately.
pub fn trigger_google_login(state: AppState) -> Result<(), String> {
    let launcher_path = std::env::temp_dir().join("monitor_oauth_launcher.html");

    if OAUTH_RUNNING.load(std::sync::atomic::Ordering::SeqCst) {
        info!("OAuth listener already waiting on port 9481, re-opening browser...");
        launch_browser_safely(&launcher_path);
        return Ok(());
    }

    tokio::spawn(async move {
        OAUTH_RUNNING.store(true, std::sync::atomic::Ordering::SeqCst);
        let res = run_google_login_flow(state).await;
        OAUTH_RUNNING.store(false, std::sync::atomic::Ordering::SeqCst);
        if let Err(e) = res {
            warn!("Google OAuth flow terminated: {e}");
        }
    });
    Ok(())
}
