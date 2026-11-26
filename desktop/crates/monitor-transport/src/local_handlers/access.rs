pub async fn toggle_git_access_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let current = state
        .storage
        .get_setting("allow_git_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let new_val = !current;
    state
        .storage
        .set_setting(
            "allow_git_operations",
            if new_val { "true" } else { "false" },
        )
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(serde_json::json!({
        "git_access_allowed": new_val
    })))
}

pub async fn toggle_delete_access_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let current = state
        .storage
        .get_setting("allow_delete_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let new_val = !current;
    state
        .storage
        .set_setting(
            "allow_delete_operations",
            if new_val { "true" } else { "false" },
        )
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(serde_json::json!({
        "delete_access_allowed": new_val
    })))
}

pub async fn toggle_cloud_access_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let current = state
        .storage
        .get_setting("allow_cloud_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let new_val = !current;
    state
        .storage
        .set_setting(
            "allow_cloud_operations",
            if new_val { "true" } else { "false" },
        )
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(serde_json::json!({
        "cloud_access_allowed": new_val
    })))
}

pub async fn toggle_local_access_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let current = state
        .storage
        .get_setting("allow_local_network")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let new_val = !current;
    state
        .storage
        .set_setting(
            "allow_local_network",
            if new_val { "true" } else { "false" },
        )
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(serde_json::json!({
        "local_access_allowed": new_val
    })))
}

pub async fn toggle_terminal_access_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let current = state
        .storage
        .get_setting("allow_remote_terminal")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let new_val = !current;
    state
        .storage
        .set_setting(
            "allow_remote_terminal",
            if new_val { "true" } else { "false" },
        )
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(serde_json::json!({
        "terminal_access_allowed": new_val
    })))
}

pub async fn get_permissions_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
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
    Ok(Json(serde_json::json!({
        "git_access_allowed": git_access_allowed,
        "delete_access_allowed": delete_access_allowed,
        "cloud_access_allowed": cloud_access_allowed,
        "local_access_allowed": local_access_allowed,
        "terminal_access_allowed": terminal_access_allowed,
        "checkpoint_access_allowed": checkpoint_access_allowed
    })))
}

pub async fn clear_alerts_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let now = chrono::Utc::now().timestamp_millis();
    let _ = state.storage.clear_alerts();
    let _ = state.storage.end_all_active_sessions(Some(0), now);
    Ok(Json(serde_json::json!({
        "cleared": true
    })))
}

pub async fn toggle_checkpoints_access_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    let current = state
        .storage
        .get_setting("allow_checkpoint_rollback")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let new_val = !current;
    state
        .storage
        .set_setting(
            "allow_checkpoint_rollback",
            if new_val { "true" } else { "false" },
        )
        .map_err(|e| failure(StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(serde_json::json!({
        "checkpoint_access_allowed": new_val
    })))
}
