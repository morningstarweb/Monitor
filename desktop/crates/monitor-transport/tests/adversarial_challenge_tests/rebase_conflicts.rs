#[tokio::test]
async fn test_adversarial_rebase_conflict_clean_state() {
    // Test what happens when method is "rebase" and a conflict occurs!
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

    // Feature branch
    std::process::Command::new("git")
        .args(["checkout", "-b", "feature-branch-rebase"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout -b feature");
    std::fs::write(&conflict_file, "line from rebase branch\n").expect("write file");
    std::process::Command::new("git")
        .args(["commit", "-am", "Rebase feature commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit feature");
    std::process::Command::new("git")
        .args(["push", "origin", "feature-branch-rebase:refs/pull/2/head"])
        .current_dir(repo_path)
        .status()
        .expect("git push pull/2/head");

    // Main conflicting commit
    std::process::Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout main");
    std::fs::write(&conflict_file, "line from main rebase test\n").expect("write file");
    std::process::Command::new("git")
        .args(["commit", "-am", "Main rebase conflict commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit main");

    // Directly call monitor_git::merge_pull_request with Some("rebase")
    let merge_res = monitor_git::merge_pull_request(repo_path, 2, Some("rebase"));
    println!("Rebase conflict merge_res: {:?}", merge_res);

    // Check git status to see if repo was left dirty or in rebase
    let status_out = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    let status_str = String::from_utf8_lossy(&status_out.stdout);
    println!("Status after rebase conflict: '{status_str}'");

    // In a correct conflict abort, git status must be empty and clean!
    assert!(
        status_str.trim().is_empty(),
        "CRITICAL BUG: Repository left dirty after rebase conflict abort! Found: '{status_str}'"
    );
}
