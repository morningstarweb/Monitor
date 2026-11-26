#![allow(clippy::unwrap_used, clippy::expect_used)]

use monitor_core::{FileChangeCategory, RepoId, Revision};
use monitor_git::{GitExecutor, query_file_diff, query_git_status};
use std::fs;
use std::process::Command;
use tempfile::tempdir;

fn setup_test_git_repo() -> tempfile::TempDir {
    let dir = tempdir().expect("tempdir creation");
    let path = dir.path();

    let init_status = Command::new("git")
        .args(["init", "-b", "main"])
        .current_dir(path)
        .status()
        .expect("git init");
    assert!(init_status.success());

    Command::new("git")
        .args(["config", "user.name", "Test User"])
        .current_dir(path)
        .status()
        .expect("git config user.name");

    Command::new("git")
        .args(["config", "user.email", "test@monitor.local"])
        .current_dir(path)
        .status()
        .expect("git config user.email");

    dir
}

#[tokio::test]
async fn test_real_git_status_and_diff_workflow() {
    let repo_dir = setup_test_git_repo();
    let repo_path = repo_dir.path();
    let repo_id = RepoId::new("r_test_real");
    let executor = GitExecutor::new().expect("GitExecutor init");

    // 1. Initial empty repository status
    let snapshot0 = query_git_status(&executor, repo_path, &repo_id, Revision(0))
        .await
        .expect("status query 0");
    assert_eq!(snapshot0.current_branch, "main");
    assert!(snapshot0.files.is_empty());

    // 2. Add an untracked file
    let file1 = repo_path.join("app.rs");
    fs::write(&file1, "fn main() {\n    println!(\"v1\");\n}\n").expect("write file");

    let snapshot1 = query_git_status(&executor, repo_path, &repo_id, Revision(1))
        .await
        .expect("status query 1");
    assert_eq!(snapshot1.files.len(), 1);
    assert_eq!(snapshot1.files[0].path, "app.rs");
    assert_eq!(snapshot1.files[0].category, FileChangeCategory::Untracked);

    // 3. Stage the file
    Command::new("git")
        .args(["add", "app.rs"])
        .current_dir(repo_path)
        .status()
        .expect("git add");

    let snapshot2 = query_git_status(&executor, repo_path, &repo_id, Revision(2))
        .await
        .expect("status query 2");
    assert_eq!(snapshot2.files.len(), 1);
    assert!(snapshot2.files[0].staged);
    assert_eq!(snapshot2.files[0].category, FileChangeCategory::Added);

    // 4. Commit the file
    Command::new("git")
        .args(["commit", "-m", "initial commit"])
        .current_dir(repo_path)
        .status()
        .expect("git commit");

    let snapshot3 = query_git_status(&executor, repo_path, &repo_id, Revision(3))
        .await
        .expect("status query 3");
    assert!(snapshot3.files.is_empty());

    // 5. Modify the file
    fs::write(&file1, "fn main() {\n    println!(\"v2 modified\");\n}\n").expect("write v2");

    let snapshot4 = query_git_status(&executor, repo_path, &repo_id, Revision(4))
        .await
        .expect("status query 4");
    assert_eq!(snapshot4.files.len(), 1);
    assert!(!snapshot4.files[0].staged);
    assert_eq!(snapshot4.files[0].category, FileChangeCategory::Modified);

    // 6. Query unified diff for app.rs
    let diff = query_file_diff(&executor, repo_path, "app.rs", false, None)
        .await
        .expect("query_file_diff");
    assert_eq!(diff.path, "app.rs");
    assert!(!diff.is_binary);
    assert_eq!(diff.insertions, 1);
    assert_eq!(diff.deletions, 1);
    assert_eq!(diff.hunks.len(), 1);

    // 7. Binary detection test
    let bin_file = repo_path.join("data.bin");
    fs::write(&bin_file, [0x00, 0xFF, 0x12, 0x34]).expect("write bin");
    let bin_diff = query_file_diff(&executor, repo_path, "data.bin", false, None)
        .await
        .expect("bin diff");
    assert!(bin_diff.is_binary);

    // 8. Path traversal attempt rejection
    let traversal_err = query_file_diff(&executor, repo_path, "../outside.txt", false, None).await;
    assert!(traversal_err.is_err());
}

#[tokio::test]
async fn test_git_operations_and_coordinator() {
    use monitor_core::{GitOperation, GitOperationRequest, OperationStatus};
    use monitor_git::RepoCoordinator;

    let repo_dir = setup_test_git_repo();
    let repo_path = repo_dir.path().to_path_buf();
    let repo_id = RepoId::new("r_test_ops");
    let executor = GitExecutor::new().expect("GitExecutor init");
    let coordinator = RepoCoordinator::new(executor.clone());

    // Create initial commit
    let file_init = repo_path.join("init.txt");
    fs::write(&file_init, "initial content").expect("write file");

    let req0 = GitOperationRequest {
        request_id: "req_0".to_string(),
        expected_repo_revision: Revision(1),
        operation: GitOperation::StageAll,
    };
    let res0 = coordinator
        .execute_operation(&repo_id, &repo_path, req0)
        .await
        .expect("stage all");
    assert_eq!(res0.status, OperationStatus::Succeeded);
    assert_eq!(res0.new_repo_revision, Revision(2));

    // Commit
    let req1 = GitOperationRequest {
        request_id: "req_1".to_string(),
        expected_repo_revision: Revision(2),
        operation: GitOperation::Commit {
            message_summary: "feat(core): initial setup".to_string(),
            message_body: None,
            stage_all_tracked: false,
            paths: None,
        },
    };
    let res1 = coordinator
        .execute_operation(&repo_id, &repo_path, req1)
        .await
        .expect("commit");
    assert_eq!(res1.status, OperationStatus::Succeeded);
    assert!(res1.commit_oid.is_some());
    assert_eq!(res1.new_repo_revision, Revision(3));

    // Stale revision test: submit with outdated expected_repo_revision = 1
    let req_stale = GitOperationRequest {
        request_id: "req_stale".to_string(),
        expected_repo_revision: Revision(1),
        operation: GitOperation::StageAll,
    };
    let res_stale = coordinator
        .execute_operation(&repo_id, &repo_path, req_stale)
        .await
        .expect("stale execution handled gracefully");
    assert_eq!(res_stale.status, OperationStatus::StaleConflict);
    assert_eq!(res_stale.error_code, Some("STALE_REPO_STATE".to_string()));

    // Create and switch branch
    let req_branch = GitOperationRequest {
        request_id: "req_branch".to_string(),
        expected_repo_revision: Revision(3),
        operation: GitOperation::CreateBranch {
            branch_name: "feat/phase-4".to_string(),
            start_point: None,
        },
    };
    let res_branch = coordinator
        .execute_operation(&repo_id, &repo_path, req_branch)
        .await
        .expect("create branch");
    assert_eq!(res_branch.status, OperationStatus::Succeeded);

    let req_switch = GitOperationRequest {
        request_id: "req_switch".to_string(),
        expected_repo_revision: Revision(4),
        operation: GitOperation::SwitchBranch {
            branch_name: "feat/phase-4".to_string(),
        },
    };
    let res_switch = coordinator
        .execute_operation(&repo_id, &repo_path, req_switch)
        .await
        .expect("switch branch");
    assert_eq!(res_switch.status, OperationStatus::Succeeded);

    // Verify current branch is feat/phase-4
    let branches = monitor_git::list_branches(&executor, &repo_path)
        .await
        .expect("list branches");
    let current = branches
        .iter()
        .find(|b| b.is_current)
        .expect("current branch");
    assert_eq!(current.name, "feat/phase-4");
}

#[tokio::test]
async fn duplicate_commit_requests_return_the_original_result() {
    use monitor_core::{GitOperation, GitOperationRequest};
    use monitor_git::RepoCoordinator;
    let repo = setup_test_git_repo();
    fs::write(repo.path().join("file.txt"), "one commit only\n").expect("file");
    let coordinator = RepoCoordinator::new(GitExecutor::new().expect("executor"));
    let repo_id = RepoId::new("dedupe");
    coordinator
        .execute_operation(
            &repo_id,
            repo.path(),
            GitOperationRequest {
                request_id: "stage".into(),
                expected_repo_revision: Revision(1),
                operation: GitOperation::StageAll,
            },
        )
        .await
        .expect("stage");
    let request = GitOperationRequest {
        request_id: "retry-after-wifi-loss".into(),
        expected_repo_revision: Revision(2),
        operation: GitOperation::Commit {
            message_summary: "One commit".into(),
            message_body: None,
            stage_all_tracked: false,
            paths: None,
        },
    };
    let (first, retry) = tokio::join!(
        coordinator.execute_operation(&repo_id, repo.path(), request.clone()),
        coordinator.execute_operation(&repo_id, repo.path(), request.clone()),
    );
    assert_eq!(first.expect("first commit"), retry.expect("cached retry"));
    let changed = GitOperationRequest {
        operation: GitOperation::StageAll,
        ..request
    };
    assert!(
        coordinator
            .execute_operation(&repo_id, repo.path(), changed)
            .await
            .is_err()
    );
    let count = Command::new("git")
        .args(["rev-list", "--count", "HEAD"])
        .current_dir(repo.path())
        .output()
        .expect("count commits");
    assert_eq!(String::from_utf8_lossy(&count.stdout).trim(), "1");
}

#[tokio::test]
async fn monitor_disables_hooks_and_diff_helpers_and_keeps_pathspecs_literal() {
    use monitor_git::{commit, stage_all, stage_files};
    let repo = setup_test_git_repo();
    let executor = GitExecutor::new().expect("executor");
    fs::write(repo.path().join("file.txt"), "before\n").expect("file");
    fs::write(repo.path().join(".gitattributes"), "file.txt diff=unsafe\n").expect("attributes");
    let hook = repo.path().join(".git/hooks/pre-commit");
    fs::write(&hook, "#!/bin/sh\nexit 99\n").expect("hook");
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&hook, fs::Permissions::from_mode(0o755)).expect("executable hook");
    }
    stage_all(&executor, repo.path()).await.expect("stage");
    commit(&executor, repo.path(), "Hooks cannot run", None, false)
        .await
        .expect("hook disabled");
    let clean = query_file_diff(&executor, repo.path(), "file.txt", false, None)
        .await
        .expect("clean diff");
    assert!(
        clean.hunks.is_empty(),
        "clean tracked files must not be reported as additions"
    );
    for key in ["diff.external", "diff.unsafe.textconv"] {
        assert!(
            Command::new("git")
                .args(["config", key, "monitor-test-nonexistent-helper"])
                .current_dir(repo.path())
                .status()
                .expect("config diff helper")
                .success()
        );
    }
    fs::write(repo.path().join("file.txt"), "after\n").expect("change");
    let diff = query_file_diff(&executor, repo.path(), "file.txt", false, None)
        .await
        .expect("helpers disabled");
    assert_eq!(diff.insertions, 1);
    assert!(
        stage_files(&executor, repo.path(), &["*.txt".into()])
            .await
            .is_err()
    );
    let status = query_git_status(&executor, repo.path(), &RepoId::new("literal"), Revision(1))
        .await
        .expect("status");
    assert!(status.files.iter().all(|file| !file.staged));
}

#[tokio::test]
async fn generic_discard_confirmation_never_deletes_a_file() {
    use monitor_git::discard_file;
    let repo = setup_test_git_repo();
    let executor = GitExecutor::new().expect("executor");
    let file = repo.path().join("important.txt");
    fs::write(&file, "preserve").expect("file");
    assert!(
        discard_file(
            &executor,
            repo.path(),
            "important.txt",
            "CONFIRM_DISCARD",
            Revision(1)
        )
        .await
        .is_err()
    );
    assert!(file.exists());
}
