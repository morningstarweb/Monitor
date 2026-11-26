#[tokio::test]
async fn management_routes_require_native_loopback_peer() {
    use axum::extract::ConnectInfo;
    use monitor_transport::build_local_router;
    use std::net::SocketAddr;

    let state = create_test_state();
    let app = build_local_router(state.clone());
    for path in ["/api/v1/local/pairing-code", "/api/v1/auth/request-code"] {
        for peer in [None, Some("192.168.1.20:43000"), Some("100.64.0.2:43000")] {
            let mut request = Request::builder().method("POST").uri(path);
            if let Some(peer) = peer {
                request = request.extension(ConnectInfo(peer.parse::<SocketAddr>().unwrap()));
            }
            let response = app
                .clone()
                .oneshot(request.body(Body::empty()).unwrap())
                .await
                .unwrap();
            assert_eq!(response.status(), StatusCode::FORBIDDEN);
            assert!(state.pairing_code.lock().unwrap().is_none());
        }
        for origin_header in ["origin", "sec-fetch-site"] {
            let response = app
                .clone()
                .oneshot(
                    Request::builder()
                        .method("POST")
                        .uri(path)
                        .extension(ConnectInfo(
                            "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
                        ))
                        .header(origin_header, "https://untrusted.example")
                        .body(Body::empty())
                        .unwrap(),
                )
                .await
                .unwrap();
            assert_eq!(response.status(), StatusCode::FORBIDDEN);
        }
        let response = app
            .clone()
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri(path)
                    .extension(ConnectInfo(
                        "127.0.0.1:43000".parse::<SocketAddr>().unwrap(),
                    ))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        *state.pairing_code.lock().unwrap() = None;
    }
}
