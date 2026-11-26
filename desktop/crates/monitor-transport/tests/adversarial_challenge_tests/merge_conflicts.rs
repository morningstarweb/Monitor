#[tokio::test]
async fn test_adversarial_merge_conflict_handling_and_clean_state() {
    // Test that when a merge conflict occurs during PR merge:
    // 1. CoreError::Conflict is returned and mapped to HTTP 409 CONFLICT
    // 2. git merge --abort is called
    // 3. The working tree is clean afterwards
    let state = create_test_state();
    let app = build_router(state.clone());

    // Setup an authentic local origin and clone
    let origin_dir = tempfile::tempdir().expect("origin dir");
    let origin_path = origin_dir.path();
    std::process::Command::new("git")
        .args(["init", "--bare", "-b", "main"])
        .current_dir(origin_path)
        .status()
        .expect("git init bare");

    let repo_dir = tempfile::tempdir().expect("repo dir");
    let repo_path = repo_dir.path();
    std::process::Command::new("git")
        .args(["clone", &origin_path.to_string_lossy(), "."])
        .current_dir(repo_path)
        .status()
        .expect("git clone");

    std::process::Command::new("git")
        .args(["config", "user.name", "Test Committer"])
        .current_dir(repo_path)
        .status()
        .expect("git config user.name");
    std::process::Command::new("git")
        .args(["config", "user.email", "test@example.com"])
        .current_dir(repo_path)
        .status()
        .expect("git config user.email");

    // Create initial commit on main
    let conflict_file = repo_path.join("conflict.txt");
    std::fs::write(&conflict_file, "initial line\n").expect("write file");
    std::process::Command::new("git")
        .args(["add", "conflict.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add");
    std::process::Command::new("git")
        .args(["commit", "-m", "Initial commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git push main");

    // Create a feature branch that edits conflict.txt
    std::process::Command::new("git")
        .args(["checkout", "-b", "feature-branch"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout -b feature");
    std::fs::write(&conflict_file, "line modified by PR feature branch\n").expect("write file");
    std::process::Command::new("git")
        .args(["commit", "-am", "Feature commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit feature");
    // Push ref to origin as pull/1/head
    std::process::Command::new("git")
        .args(["push", "origin", "feature-branch:refs/pull/1/head"])
        .current_dir(repo_path)
        .status()
        .expect("git push pull/1/head");

    // Now switch back to main and make a conflicting change
    std::process::Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout main");
    std::fs::write(
        &conflict_file,
        "line modified by main branch independently\n",
    )
    .expect("write file");
    std::process::Command::new("git")
        .args(["commit", "-am", "Main conflicting commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit main");

    // Register repo in app state
    let repo_id = RepoId::new("r_conflict");
    state
        .storage
        .register_repo(
            &repo_id,
            &repo_path.to_string_lossy(),
            "Conflict Repo",
            TrustProfile::Trusted,
        )
        .expect("register repo");

    let op_token = "tok_op_conflict_secret_1234567890";
    let op_hash = TokenAuthenticator::hash_token(op_token);
    state
        .storage
        .insert_device(
            &DeviceId::new("dev_op_conflict"),
            "Op Conflict",
            &op_hash,
            DeviceRole::Operator,
        )
        .expect("insert op device");

    state
        .storage
        .set_setting("allow_delete_operations", "1")
        .expect("grant allow_delete_operations");

    // Perform merge of PR #1
    let payload = MergePrRequest {
        method: Some("merge".to_string()),
    };
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/projects/r_conflict/pulls/1/merge")
        .header("Authorization", format!("Bearer {op_token}"))
        .header("Content-Type", "application/json")
        .body(Body::from(serde_json::to_string(&payload).unwrap()))
        .unwrap();

    let res = app.clone().oneshot(req).await.unwrap();
    // Verify HTTP 409 Conflict is returned
    assert_eq!(
        res.status(),
        StatusCode::CONFLICT,
        "Merge conflict must return HTTP 409 Conflict"
    );

    // Verify git working tree in repo_path is completely clean (abort was executed)
    let status_out = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    let status_str = String::from_utf8_lossy(&status_out.stdout);
    assert!(
        status_str.trim().is_empty(),
        "Working tree must be clean after conflict abort, but found: {status_str}"
    );

    // Verify repository is not locked or broken: a subsequent git commit works
    std::fs::write(repo_path.join("clean.txt"), "clean").expect("write clean");
    let add_res = std::process::Command::new("git")
        .args(["add", "clean.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add clean");
    assert!(add_res.success(), "Git repository must remain usable");
}
