#[tokio::test]
async fn test_adversarial_multi_commit_rebase_conflict_abort() {
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

    let file_a = repo_path.join("file_a.txt");
    let file_b = repo_path.join("file_b.txt");
    std::fs::write(&file_a, "init a\n").expect("write a");
    std::fs::write(&file_b, "init b\n").expect("write b");
    std::process::Command::new("git")
        .args(["add", "."])
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

    // Feature branch with 3 commits:
    // C1: edits file_a
    // C2: edits file_b
    // C3: edits file_a again
    std::process::Command::new("git")
        .args(["checkout", "-b", "feat-multi"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout feat");

    std::fs::write(&file_a, "feat a 1\n").expect("write a");
    std::process::Command::new("git")
        .args(["commit", "-am", "C1: feat a 1"])
        .current_dir(repo_path)
        .status()
        .expect("commit c1");

    std::fs::write(&file_b, "feat b 2\n").expect("write b");
    std::process::Command::new("git")
        .args(["commit", "-am", "C2: feat b 2"])
        .current_dir(repo_path)
        .status()
        .expect("commit c2");

    std::fs::write(&file_a, "feat a 3\n").expect("write a");
    std::process::Command::new("git")
        .args(["commit", "-am", "C3: feat a 3"])
        .current_dir(repo_path)
        .status()
        .expect("commit c3");

    std::process::Command::new("git")
        .args(["push", "origin", "feat-multi:refs/pull/90/head"])
        .current_dir(repo_path)
        .status()
        .expect("push PR 90");

    // Main has commit that conflicts with C2 (edits file_b)
    std::process::Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout main");
    std::fs::write(&file_b, "main conflicting b\n").expect("write b");
    std::process::Command::new("git")
        .args(["commit", "-am", "Main edits b"])
        .current_dir(repo_path)
        .status()
        .expect("commit main");

    let res = monitor_git::merge_pull_request(repo_path, 90, Some("rebase"));
    assert!(res.is_err(), "Multi-commit rebase conflict must return Err");

    let status_out = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    let status_str = String::from_utf8_lossy(&status_out.stdout);
    assert!(
        status_str.trim().is_empty(),
        "Repository must be completely clean after multi-commit rebase conflict abort! Found: '{status_str}'"
    );

    // Verify .git/rebase-merge is removed
    assert!(!repo_path.join(".git").join("rebase-merge").exists());
    assert!(!repo_path.join(".git").join("rebase-apply").exists());

    // Verify file_a and file_b match main HEAD
    assert_eq!(
        std::fs::read_to_string(&file_a)
            .expect("read a")
            .replace("\r\n", "\n"),
        "init a\n"
    );
    assert_eq!(
        std::fs::read_to_string(&file_b)
            .expect("read b")
            .replace("\r\n", "\n"),
        "main conflicting b\n"
    );
}
