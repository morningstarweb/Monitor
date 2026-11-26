#[tokio::test]
async fn challenge_execute_pending_command_edge_cases() {
    let state = create_challenge_test_state();

    // 1. Missing 'pid' in kill_process payload
    let bad_kill = PendingCommand {
        id: "cmd_bad_kill".to_string(),
        command_type: "kill_process".to_string(),
        payload: serde_json::json!({}),
    };
    let res1 = execute_pending_command(&state, &bad_kill).await;
    assert_eq!(res1.status, "failed");
    assert_eq!(res1.result.unwrap(), "Missing 'pid' in payload");

    // 2. Missing 'repo_id' in merge_pr payload
    let bad_merge_no_repo = PendingCommand {
        id: "cmd_bad_merge_1".to_string(),
        command_type: "merge_pr".to_string(),
        payload: serde_json::json!({ "pr_number": 10 }),
    };
    let res2 = execute_pending_command(&state, &bad_merge_no_repo).await;
    assert_eq!(res2.status, "failed");
    assert_eq!(res2.result.unwrap(), "Missing 'repo_id' in payload");

    // 3. Missing 'pr_number' in merge_pr payload
    let bad_merge_no_pr = PendingCommand {
        id: "cmd_bad_merge_2".to_string(),
        command_type: "merge_pr".to_string(),
        payload: serde_json::json!({ "repo_id": "r_dummy" }),
    };
    let res3 = execute_pending_command(&state, &bad_merge_no_pr).await;
    assert_eq!(res3.status, "failed");
    assert_eq!(res3.result.unwrap(), "Missing 'pr_number' in payload");

    // 4. Non-existent repository in merge_pr payload
    let missing_repo_cmd = PendingCommand {
        id: "cmd_missing_repo".to_string(),
        command_type: "merge_pr".to_string(),
        payload: serde_json::json!({ "repo_id": "r_nonexistent", "pr_number": 1 }),
    };
    let res4 = execute_pending_command(&state, &missing_repo_cmd).await;
    assert_eq!(res4.status, "failed");
    assert!(
        res4.result
            .unwrap()
            .contains("Repository 'r_nonexistent' not found")
    );

    // 5. Completely arbitrary command type
    let arbitrary_cmd = PendingCommand {
        id: "cmd_arbitrary".to_string(),
        command_type: "format_c_drive".to_string(),
        payload: serde_json::json!({ "force": true }),
    };
    let res5 = execute_pending_command(&state, &arbitrary_cmd).await;
    assert_eq!(res5.status, "failed");
    assert!(
        res5.result
            .unwrap()
            .contains("Unsupported command type 'format_c_drive'")
    );
}
