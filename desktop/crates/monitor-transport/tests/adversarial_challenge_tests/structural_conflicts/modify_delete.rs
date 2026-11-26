fn verify_modify_delete_conflict(method: &str) {
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

    let target_file = repo_path.join("target.txt");
    std::fs::write(&target_file, "initial\n").expect("write target");
    std::process::Command::new("git")
        .args(["add", "target.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add");
    std::process::Command::new("git")
        .args(["commit", "-m", "Initial target"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git push main");

    // PR deletes target.txt
    std::process::Command::new("git")
        .args(["checkout", "-b", "feat-delete"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout feat");
    std::process::Command::new("git")
        .args(["rm", "target.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git rm");
    std::process::Command::new("git")
        .args(["commit", "-m", "PR deletes target"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "feat-delete:refs/pull/70/head"])
        .current_dir(repo_path)
        .status()
        .expect("git push PR 70");

    // Main modifies target.txt
    std::process::Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout main");
    std::fs::write(&target_file, "modified on main\n").expect("write target");
    std::process::Command::new("git")
        .args(["commit", "-am", "Main modifies target"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");

    let res = monitor_git::merge_pull_request(repo_path, 70, Some(method));
    assert!(
        res.is_err(),
        "Modify/Delete conflict must fail for method {method}"
    );

    let status_out = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    let status_str = String::from_utf8_lossy(&status_out.stdout);
    assert!(
        status_str.trim().is_empty(),
        "Repository must be clean after Modify/Delete conflict abort for method {method}! Found: '{status_str}'"
    );
    assert!(
        target_file.exists(),
        "target.txt must exist on main after abort"
    );
}
