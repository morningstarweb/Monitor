#[tokio::test]
async fn test_adversarial_nonexistent_branch_and_cleanliness() {
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

    std::fs::write(repo_path.join("file.txt"), "hello\n").expect("write file");
    std::process::Command::new("git")
        .args(["add", "file.txt"])
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

    for method in ["merge", "squash", "rebase"] {
        let res = monitor_git::merge_pull_request(repo_path, 99999, Some(method));
        assert!(
            res.is_err(),
            "Non-existent PR must fail for method {method}"
        );

        let status_out = std::process::Command::new("git")
            .args(["status", "--porcelain"])
            .current_dir(repo_path)
            .output()
            .expect("git status");
        let status_str = String::from_utf8_lossy(&status_out.stdout);
        assert!(
            status_str.trim().is_empty(),
            "Repository must remain clean after non-existent PR fetch failure for method {method}, got: '{status_str}'"
        );
    }
}
