#[test]
fn test_merge_pull_request_squash_conflict_cleans_repository() {
    let origin = tempfile::tempdir().unwrap();
    Command::new("git")
        .args(["init", "--bare", "-b", "main"])
        .current_dir(origin.path())
        .status()
        .unwrap();

    let repo = tempfile::tempdir().unwrap();
    let repo_path = repo.path();
    Command::new("git")
        .args(["clone", &origin.path().to_string_lossy(), "."])
        .current_dir(repo_path)
        .status()
        .unwrap();
    Command::new("git")
        .args(["config", "user.name", "Test Committer"])
        .current_dir(repo_path)
        .status()
        .unwrap();
    Command::new("git")
        .args(["config", "user.email", "test@example.com"])
        .current_dir(repo_path)
        .status()
        .unwrap();

    let file = repo_path.join("file.txt");
    std::fs::write(&file, "initial\n").unwrap();
    Command::new("git")
        .args(["add", "file.txt"])
        .current_dir(repo_path)
        .status()
        .unwrap();
    Command::new("git")
        .args(["commit", "-m", "initial"])
        .current_dir(repo_path)
        .status()
        .unwrap();
    Command::new("git")
        .args(["push", "origin", "main"])
        .current_dir(repo_path)
        .status()
        .unwrap();

    // Feature branch
    Command::new("git")
        .args(["checkout", "-b", "feat-squash"])
        .current_dir(repo_path)
        .status()
        .unwrap();
    std::fs::write(&file, "squash line\n").unwrap();
    Command::new("git")
        .args(["commit", "-am", "squash commit"])
        .current_dir(repo_path)
        .status()
        .unwrap();
    Command::new("git")
        .args(["push", "origin", "feat-squash:refs/pull/20/head"])
        .current_dir(repo_path)
        .status()
        .unwrap();

    // Conflicting commit on main
    Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .unwrap();
    std::fs::write(&file, "main line\n").unwrap();
    Command::new("git")
        .args(["commit", "-am", "main commit"])
        .current_dir(repo_path)
        .status()
        .unwrap();

    let res = merge_pull_request(repo_path, 20, Some("squash"));
    assert!(matches!(res, Err(CoreError::Conflict(_))));

    let status = Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .unwrap();
    assert!(
        status.stdout.is_empty(),
        "Working tree must be clean after squash conflict abort!"
    );
}
