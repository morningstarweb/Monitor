#[tokio::test]
async fn test_adversarial_sequential_conflict_recovery_then_success() {
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

    let shared = repo_path.join("shared.txt");
    std::fs::write(&shared, "shared line 0\n").expect("write shared");
    std::process::Command::new("git")
        .args(["add", "shared.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add");
    std::process::Command::new("git")
        .args(["commit", "-m", "Initial shared"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git push main");

    // PR 1: conflicting change on shared.txt
    std::process::Command::new("git")
        .args(["checkout", "-b", "pr-1-branch"])
        .current_dir(repo_path)
        .status()
        .expect("checkout pr-1");
    std::fs::write(&shared, "pr 1 conflicting line\n").expect("write shared");
    std::process::Command::new("git")
        .args(["commit", "-am", "PR 1 commit"])
        .current_dir(repo_path)
        .status()
        .expect("commit pr 1");
    std::process::Command::new("git")
        .args(["push", "origin", "pr-1-branch:refs/pull/101/head"])
        .current_dir(repo_path)
        .status()
        .expect("push PR 101");

    // PR 2: also conflicting change on shared.txt
    std::process::Command::new("git")
        .args(["checkout", "-b", "pr-2-branch", "main"])
        .current_dir(repo_path)
        .status()
        .expect("checkout pr-2");
    std::fs::write(&shared, "pr 2 conflicting line\n").expect("write shared");
    std::process::Command::new("git")
        .args(["commit", "-am", "PR 2 commit"])
        .current_dir(repo_path)
        .status()
        .expect("commit pr 2");
    std::process::Command::new("git")
        .args(["push", "origin", "pr-2-branch:refs/pull/102/head"])
        .current_dir(repo_path)
        .status()
        .expect("push PR 102");

    // PR 3: clean non-conflicting change adding clean_feature.txt
    std::process::Command::new("git")
        .args(["checkout", "-b", "pr-3-branch", "main"])
        .current_dir(repo_path)
        .status()
        .expect("checkout pr-3");
    std::fs::write(repo_path.join("clean_feature.txt"), "clean feature line\n")
        .expect("write clean feature");
    std::process::Command::new("git")
        .args(["add", "clean_feature.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add clean");
    std::process::Command::new("git")
        .args(["commit", "-m", "PR 3 clean commit"])
        .current_dir(repo_path)
        .status()
        .expect("commit pr 3");
    std::process::Command::new("git")
        .args(["push", "origin", "pr-3-branch:refs/pull/103/head"])
        .current_dir(repo_path)
        .status()
        .expect("push PR 103");

    // Switch back to main and make a commit on shared.txt
    std::process::Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .expect("checkout main");
    std::fs::write(&shared, "main divergence on shared\n").expect("write shared");
    std::process::Command::new("git")
        .args(["commit", "-am", "Main divergence commit"])
        .current_dir(repo_path)
        .status()
        .expect("commit main divergence");

    // Step 1: Attempt PR 101 with rebase -> should fail and abort
    let res1 = monitor_git::merge_pull_request(repo_path, 101, Some("rebase"));
    assert!(res1.is_err(), "PR 101 must conflict");
    let status_1 = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    assert!(
        status_1.stdout.is_empty(),
        "Status after PR 101 rebase conflict must be clean"
    );

    // Step 2: Attempt PR 102 with squash -> should fail and abort
    let res2 = monitor_git::merge_pull_request(repo_path, 102, Some("squash"));
    assert!(res2.is_err(), "PR 102 must conflict");
    let status_2 = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    assert!(
        status_2.stdout.is_empty(),
        "Status after PR 102 squash conflict must be clean"
    );

    // Step 3: Attempt PR 103 with squash -> should SUCCEED cleanly!
    let res3 = monitor_git::merge_pull_request(repo_path, 103, Some("squash"));
    assert!(
        res3.is_ok(),
        "PR 103 clean squash must succeed: {:?}",
        res3.err()
    );

    let status_3 = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    assert!(
        status_3.stdout.is_empty(),
        "Status after successful squash must be clean, got: '{}'",
        String::from_utf8_lossy(&status_3.stdout)
    );
    assert!(
        repo_path.join("clean_feature.txt").exists(),
        "clean_feature.txt must exist on disk"
    );
}
