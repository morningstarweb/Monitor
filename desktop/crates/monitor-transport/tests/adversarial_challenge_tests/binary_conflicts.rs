#[tokio::test]
async fn test_adversarial_binary_conflict_handling_all_methods() {
    for method in ["merge", "squash", "rebase"] {
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

        let bin_file = repo_path.join("data.bin");
        let base_bytes: Vec<u8> = (0..255).collect();
        std::fs::write(&bin_file, &base_bytes).expect("write binary");
        std::process::Command::new("git")
            .args(["add", "data.bin"])
            .current_dir(repo_path)
            .status()
            .expect("git add");
        std::process::Command::new("git")
            .args(["commit", "-m", "Initial binary"])
            .current_dir(repo_path)
            .status()
            .expect("git commit");
        std::process::Command::new("git")
            .args(["push", "origin", "main"])
            .current_dir(repo_path)
            .status()
            .expect("git push main");

        // Feature branch with binary change
        std::process::Command::new("git")
            .args(["checkout", "-b", "feat-bin"])
            .current_dir(repo_path)
            .status()
            .expect("git checkout feat-bin");
        let feat_bytes: Vec<u8> = (0..255).rev().collect();
        std::fs::write(&bin_file, &feat_bytes).expect("write feat binary");
        std::process::Command::new("git")
            .args(["commit", "-am", "Feat binary"])
            .current_dir(repo_path)
            .status()
            .expect("git commit feat");
        std::process::Command::new("git")
            .args(["push", "origin", "feat-bin:refs/pull/50/head"])
            .current_dir(repo_path)
            .status()
            .expect("git push pull 50");

        // Main branch with conflicting binary change
        std::process::Command::new("git")
            .args(["checkout", "main"])
            .current_dir(repo_path)
            .status()
            .expect("git checkout main");
        let mut main_bytes = base_bytes.clone();
        main_bytes[0] = 0xAA;
        main_bytes[100] = 0xBB;
        std::fs::write(&bin_file, &main_bytes).expect("write main binary");
        std::process::Command::new("git")
            .args(["commit", "-am", "Main binary"])
            .current_dir(repo_path)
            .status()
            .expect("git commit main");

        let res = monitor_git::merge_pull_request(repo_path, 50, Some(method));
        assert!(
            res.is_err(),
            "Binary conflict must return Err for method {method}"
        );

        let status_out = std::process::Command::new("git")
            .args(["status", "--porcelain"])
            .current_dir(repo_path)
            .output()
            .expect("git status");
        let status_str = String::from_utf8_lossy(&status_out.stdout);
        assert!(
            status_str.trim().is_empty(),
            "Repository must be clean after binary conflict abort for method {method}! Found: '{status_str}'"
        );

        // Assert file content matches main_bytes exactly
        let current_bytes = std::fs::read(&bin_file).expect("read bin");
        assert_eq!(
            current_bytes, main_bytes,
            "Binary file content must be restored to main HEAD for method {method}"
        );
    }
}
