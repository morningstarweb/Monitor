#[test]
fn test_parse_github_url_https() {
    let (owner, repo) = parse_github_url("https://github.com/rust-lang/rust.git").unwrap();
    assert_eq!(owner, "rust-lang");
    assert_eq!(repo, "rust");

    let (owner2, repo2) = parse_github_url("https://github.com/facebook/react").unwrap();
    assert_eq!(owner2, "facebook");
    assert_eq!(repo2, "react");
}

#[test]
fn test_parse_github_url_ssh() {
    let (owner, repo) = parse_github_url("git@github.com:torvalds/linux.git").unwrap();
    assert_eq!(owner, "torvalds");
    assert_eq!(repo, "linux");

    let (owner2, repo2) = parse_github_url("ssh://git@github.com/owner/repo.git").unwrap();
    assert_eq!(owner2, "owner");
    assert_eq!(repo2, "repo");
}

#[test]
fn test_parse_github_url_invalid() {
    assert!(parse_github_url("https://gitlab.com/owner/repo.git").is_none());
    assert!(parse_github_url("not a url").is_none());
    assert!(parse_github_url("").is_none());
}

#[test]
fn test_parse_github_remote_nonexistent_repo() {
    let temp = tempfile::tempdir().unwrap();
    assert!(parse_github_remote(temp.path()).is_none());
}
