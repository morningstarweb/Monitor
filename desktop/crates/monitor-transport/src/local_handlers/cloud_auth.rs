pub async fn start_cloud_login_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    match crate::cloud::trigger_google_login(state) {
        Ok(()) => Ok(Json(serde_json::json!({
            "status": "started",
            "message": "Browser opened for Google Sign-In"
        }))),
        Err(err) => Err(failure(StatusCode::BAD_REQUEST, err)),
    }
}

pub async fn disconnect_cloud_handler(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, ApiFailure> {
    match crate::cloud::disconnect_cloud(state).await {
        Ok(()) => Ok(Json(serde_json::json!({ "status": "ok" }))),
        Err(err) => Err(failure(StatusCode::INTERNAL_SERVER_ERROR, err)),
    }
}
