#![allow(clippy::unwrap_used)]
use super::*;
use crate::executor::GitExecutor;
use std::path::Path;

#[test]
fn test_branch_name_validation() {
    assert!(validate_branch_name("main").is_ok());
    assert!(validate_branch_name("feat/auth-login").is_ok());
    assert!(validate_branch_name("bugfix/123").is_ok());

    assert!(validate_branch_name("").is_err());
    assert!(validate_branch_name("-main").is_err());
    assert!(validate_branch_name("feat/..").is_err());
    assert!(validate_branch_name("feat/branch~1").is_err());
    assert!(validate_branch_name("feat/with space").is_err());
    assert!(validate_branch_name("feat.lock").is_err());
}

#[test]
fn test_branch_name_sanitization() {
    assert_eq!(sanitize_branch_name("Morning Cloud"), "Morning-Cloud");
    assert_eq!(sanitize_branch_name("telegram   cloud"), "telegram-cloud");
    assert_eq!(
        sanitize_branch_name("feature / auth flow"),
        "feature/auth-flow"
    );
    assert_eq!(sanitize_branch_name("feat~1^2:test*"), "feat-1-2-test");
    assert_eq!(sanitize_branch_name("feat..flow"), "feat.flow");
    assert_eq!(sanitize_branch_name("feat.lock"), "feat");
    assert_eq!(sanitize_branch_name("-main-"), "main");
    assert_eq!(sanitize_branch_name("   "), "new-branch");
    // All sanitized names must pass validation
    assert!(validate_branch_name(&sanitize_branch_name("Morning Cloud")).is_ok());
    assert!(validate_branch_name(&sanitize_branch_name("telegram   cloud")).is_ok());
    assert!(validate_branch_name(&sanitize_branch_name("feature / auth flow")).is_ok());
}

#[tokio::test]
async fn test_main_branch_deletion_protection() {
    let executor = GitExecutor::new().unwrap();
    let path = Path::new(".");
    assert!(delete_branch(&executor, path, "main", true).await.is_err());
    assert!(
        delete_branch(&executor, path, "master", true)
            .await
            .is_err()
    );
    assert!(
        delete_branch(&executor, path, "origin/main", true)
            .await
            .is_err()
    );
}
