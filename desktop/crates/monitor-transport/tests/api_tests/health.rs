#[tokio::test]
async fn test_health_endpoint() {
    let state = create_test_state();
    let app = build_router(state);

    let req = Request::builder()
        .uri("/api/v1/health")
        .body(Body::empty())
        .unwrap();

    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);

    let body = res.into_body().collect().await.unwrap().to_bytes();
    let health: HealthResponse = serde_json::from_slice(&body).unwrap();
    assert_eq!(health.app_name, "Monitor");
    assert!(health.auth_required);
}
