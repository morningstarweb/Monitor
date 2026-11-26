#[tokio::test]
async fn test_cloud_pending_command_tray_policy_enforcement() {
    let storage = Storage::in_memory().expect("in-memory storage");
    // Cloud transport is explicitly authorized here so this test exercises the
    // *delete* tray-policy gate rather than the outer cloud gate (AUD-04).
    storage
        .set_setting("allow_cloud_operations", "1")
        .expect("grant cloud operations");
    let telemetry = TelemetrySampler::new();
    let git_executor = monitor_git::GitExecutor::new().expect("git executor");
    let state = AppState::new(storage, telemetry, git_executor);

    // 1. By default, allow_delete_operations is false -> kill_process is blocked by policy
    let kill_cmd = PendingCommand {
        id: "cmd_kill_blocked".to_string(),
        command_type: "kill_process".to_string(),
        payload: serde_json::json!({ "pid": 1234 }),
    };
    let res = execute_pending_command(&state, &kill_cmd).await;
    assert_eq!(res.status, "failed");
    assert_eq!(
        res.result.as_deref(),
        Some("Operation disabled by desktop security policy")
    );

    // 2. Enable allow_delete_operations -> kill_process passes security policy gate
    state
        .storage
        .set_setting("allow_delete_operations", "1")
        .expect("set allow_delete_operations");
    let res = execute_pending_command(&state, &kill_cmd).await;
    assert_eq!(res.status, "failed");
    // Not blocked by policy anymore; fails with process not found
    assert_ne!(
        res.result.as_deref(),
        Some("Operation disabled by desktop security policy")
    );

    // 3. Disable allow_git_operations -> merge_pr is blocked by policy
    state
        .storage
        .set_setting("allow_git_operations", "0")
        .expect("set allow_git_operations");
    let merge_cmd = PendingCommand {
        id: "cmd_merge_blocked".to_string(),
        command_type: "merge_pr".to_string(),
        payload: serde_json::json!({ "repo_id": "r_test", "pr_number": 1 }),
    };
    let res = execute_pending_command(&state, &merge_cmd).await;
    assert_eq!(res.status, "failed");
    assert_eq!(
        res.result.as_deref(),
        Some("Operation disabled by desktop security policy")
    );

    // 4. Enable allow_git_operations -> merge_pr passes security policy gate
    state
        .storage
        .set_setting("allow_git_operations", "1")
        .expect("set allow_git_operations");
    let res = execute_pending_command(&state, &merge_cmd).await;
    assert_eq!(res.status, "failed");
    assert_eq!(res.result.as_deref(), Some("Repository 'r_test' not found"));
}
