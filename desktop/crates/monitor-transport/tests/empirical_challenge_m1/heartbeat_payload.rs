#[tokio::test]
async fn challenge_heartbeat_payload_serialization_stress() {
    let state = create_challenge_test_state();

    let mut temp_dirs = Vec::new();
    // Register 3 repos, keeping TempDir alive
    for i in 1..=3 {
        let dir = tempfile::tempdir().expect("tempdir");
        let repo_path = dir.path();
        std::process::Command::new("git")
            .args(["init", "-b", "main"])
            .current_dir(repo_path)
            .status()
            .expect("git init");

        // Write a test file in repo
        std::fs::write(repo_path.join("file.txt"), format!("content {i}")).unwrap();

        let repo_id = RepoId::new(format!("r_hb_{i}"));
        state
            .storage
            .register_repo(
                &repo_id,
                &repo_path.to_string_lossy(),
                &format!("HB Repo {i}"),
                TrustProfile::Trusted,
            )
            .expect("register repo");

        temp_dirs.push(dir);
    }

    let snapshot = state.telemetry.sample_system_snapshot();
    let projects = monitor_transport::local_handlers::list_project_summaries(&state)
        .await
        .unwrap_or_default();
    let workspaces = collect_workspaces(&state).await;
    let mut pr_cache = HashMap::new();
    let pull_requests = collect_pull_requests(&state, &mut pr_cache).await;
    let command_results: Vec<CommandResult> = vec![CommandResult {
        id: "cmd_test_01".to_string(),
        status: "completed".to_string(),
        result: Some("Merged PR #42".to_string()),
    }];

    let payload = serde_json::json!({
        "lan_ip": "192.168.1.100",
        "lan_port": 9480,
        "snapshot": snapshot,
        "projects": projects,
        "workspaces": workspaces,
        "pull_requests": pull_requests,
        "command_results": command_results,
    });

    let json_str = serde_json::to_string(&payload).expect("payload serialize");
    assert!(json_str.contains("workspaces"));
    assert!(json_str.contains("pull_requests"));
    assert!(json_str.contains("command_results"));
    assert!(json_str.contains("r_hb_1"));
    assert!(json_str.contains("r_hb_2"));
    assert!(json_str.contains("r_hb_3"));

    // Verify workspaces schema
    let parsed: serde_json::Value = serde_json::from_str(&json_str).unwrap();
    let ws_obj = parsed["workspaces"].as_object().unwrap();
    assert_eq!(ws_obj.len(), 3);
    for (_, ws) in ws_obj {
        assert_eq!(ws["current_branch"].as_str().unwrap(), "main");
        let files = ws["files"].as_array().unwrap();
        assert!(
            files
                .iter()
                .any(|f| f["path"].as_str().unwrap() == "file.txt")
        );
    }
}
