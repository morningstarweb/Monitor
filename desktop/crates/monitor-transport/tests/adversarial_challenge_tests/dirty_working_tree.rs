#[tokio::test]
async fn test_adversarial_dirty_working_tree_prior_to_merge() {
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

    std::fs::write(repo_path.join("base.txt"), "base\n").expect("write base");
    std::process::Command::new("git")
        .args(["add", "base.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add");
    std::process::Command::new("git")
        .args(["commit", "-m", "Initial"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git push main");

    // PR branch modifies base.txt
    std::process::Command::new("git")
        .args(["checkout", "-b", "feat-pr"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout feat");
    std::fs::write(repo_path.join("base.txt"), "pr modification\n").expect("write base");
    std::process::Command::new("git")
        .args(["commit", "-am", "PR commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "feat-pr:refs/pull/80/head"])
        .current_dir(repo_path)
        .status()
        .expect("git push PR 80");

    // Checkout main
    std::process::Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout main");

    // Leave uncommitted dirty modification on base.txt prior to merge
    std::fs::write(
        repo_path.join("base.txt"),
        "dirty uncommitted modification\n",
    )
    .expect("write dirty");

    // Also leave an untracked file
    std::fs::write(repo_path.join("untracked_scratch.txt"), "scratch work\n")
        .expect("write scratch");

    // Try merging PR with dirty working tree
    let res = monitor_git::merge_pull_request(repo_path, 80, Some("rebase"));
    println!("Dirty working tree merge_res: {:?}", res);
    assert!(
        res.is_err(),
        "Merge should fail when working tree has conflicting uncommitted changes"
    );

    // After failure, ensure git repo is in a valid, functional state (not stuck in rebase or locked index)
    let status_out = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    let _status_str = String::from_utf8_lossy(&status_out.stdout);

    // Ensure repo is not mid-rebase:
    assert!(
        !repo_path.join(".git").join("rebase-merge").exists(),
        ".git/rebase-merge must not exist"
    );
    assert!(
        !repo_path.join(".git").join("rebase-apply").exists(),
        ".git/rebase-apply must not exist"
    );
    assert!(
        !repo_path.join(".git").join("MERGE_HEAD").exists(),
        ".git/MERGE_HEAD must not exist"
    );

    // Subsequent git operations must work cleanly
    let commit_res = std::process::Command::new("git")
        .args([
            "commit",
            "--allow-empty",
            "-m",
            "subsequent commit after dirty abort",
        ])
        .current_dir(repo_path)
        .status()
        .expect("git commit subsequent");
    assert!(
        commit_res.success(),
        "Git repository must remain functional for future operations"
    );
}
