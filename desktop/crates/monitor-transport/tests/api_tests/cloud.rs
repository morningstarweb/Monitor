#[tokio::test]
async fn test_cloud_parity_and_commands() {
    use monitor_core::{RepoId, TrustProfile};
    use monitor_transport::cloud::{
        PendingCommand, collect_pull_requests, collect_workspaces, execute_pending_command,
    };
    use std::collections::HashMap;

    let state = create_test_state();

    let dir = tempfile::tempdir().expect("tempdir");
    let repo_path = dir.path();
    std::process::Command::new("git")
        .args(["init", "-b", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git init");

    let repo_id = RepoId::new("r_cloud_test");
    state
        .storage
        .register_repo(
            &repo_id,
            &repo_path.to_string_lossy(),
            "Cloud Test Repo",
            TrustProfile::Trusted,
        )
        .expect("register repo");

    // 1. collect_workspaces gathers registered repos
    let workspaces = collect_workspaces(&state).await;
    assert!(workspaces.contains_key("r_cloud_test"));
    assert_eq!(workspaces["r_cloud_test"].current_branch, "main");

    // 2. collect_pull_requests collects with cache
    let mut pr_cache = HashMap::new();
    let prs = collect_pull_requests(&state, &mut pr_cache).await;
    assert!(prs.contains_key("r_cloud_test"));
    assert!(prs["r_cloud_test"].is_empty());
    assert!(pr_cache.contains_key(&repo_id));

    // 3. execute_pending_command for kill_process on protected PID 0 fails
    let kill_cmd = PendingCommand {
        id: "cmd_kill_0".to_string(),
        command_type: "kill_process".to_string(),
        payload: serde_json::json!({ "pid": 0 }),
    };
    let kill_res = execute_pending_command(&state, &kill_cmd).await;
    assert_eq!(kill_res.status, "failed");

    // 4. execute_pending_command for unsupported command fails
    let unknown_cmd = PendingCommand {
        id: "cmd_unk".to_string(),
        command_type: "unsupported_command_xyz".to_string(),
        payload: serde_json::json!({}),
    };
    let unk_res = execute_pending_command(&state, &unknown_cmd).await;
    assert_eq!(unk_res.status, "failed");
}
