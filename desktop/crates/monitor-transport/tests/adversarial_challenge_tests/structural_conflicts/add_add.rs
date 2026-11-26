fn verify_add_add_conflict(method: &str) {
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

    std::fs::write(repo_path.join("root.txt"), "root\n").expect("write root");
    std::process::Command::new("git")
        .args(["add", "root.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add");
    std::process::Command::new("git")
        .args(["commit", "-m", "Root commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git push main");

    // PR adds new_file.txt with PR content
    std::process::Command::new("git")
        .args(["checkout", "-b", "feat-add-add"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout feat");
    std::fs::write(repo_path.join("new_file.txt"), "from PR\n").expect("write new_file");
    std::process::Command::new("git")
        .args(["add", "new_file.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add");
    std::process::Command::new("git")
        .args(["commit", "-m", "PR adds new_file"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");
    std::process::Command::new("git")
        .args(["push", "origin", "feat-add-add:refs/pull/60/head"])
        .current_dir(repo_path)
        .status()
        .expect("git push PR 60");

    // Main adds new_file.txt with main content
    std::process::Command::new("git")
        .args(["checkout", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git checkout main");
    std::fs::write(repo_path.join("new_file.txt"), "from main\n").expect("write new_file");
    std::process::Command::new("git")
        .args(["add", "new_file.txt"])
        .current_dir(repo_path)
        .status()
        .expect("git add");
    std::process::Command::new("git")
        .args(["commit", "-m", "Main adds new_file"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");

    let res = monitor_git::merge_pull_request(repo_path, 60, Some(method));
    assert!(
        res.is_err(),
        "Add/Add conflict must fail for method {method}"
    );

    let status_out = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(repo_path)
        .output()
        .expect("git status");
    let status_str = String::from_utf8_lossy(&status_out.stdout);
    assert!(
        status_str.trim().is_empty(),
        "Repository must be clean after Add/Add conflict abort for method {method}! Found: '{status_str}'"
    );
    let content = std::fs::read_to_string(repo_path.join("new_file.txt")).expect("read file");
    assert_eq!(
        content.replace("\r\n", "\n"),
        "from main\n",
        "Content must match main commit"
    );
}
