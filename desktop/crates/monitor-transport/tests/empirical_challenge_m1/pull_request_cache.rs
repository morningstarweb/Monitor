#[tokio::test]
async fn challenge_pr_cache_30s_ttl_and_invalidation() {
    let state = create_challenge_test_state();

    let dir = tempfile::tempdir().expect("tempdir");
    let repo_path = dir.path();
    std::process::Command::new("git")
        .args(["init", "-b", "main"])
        .current_dir(repo_path)
        .status()
        .expect("git init");

    let repo_id = RepoId::new("r_ttl_challenge");
    state
        .storage
        .register_repo(
            &repo_id,
            &repo_path.to_string_lossy(),
            "TTL Challenge Repo",
            TrustProfile::Trusted,
        )
        .expect("register repo");

    let mut pr_cache: HashMap<RepoId, PrCacheEntry> = HashMap::new();

    // 1. First collection fetches and populates cache
    let first_prs = collect_pull_requests(&state, &mut pr_cache).await;
    assert!(first_prs.contains_key("r_ttl_challenge"));
    assert!(pr_cache.contains_key(&repo_id));

    let entry = pr_cache.get(&repo_id).unwrap();
    let initial_fetched_at = entry.fetched_at;

    // 2. Immediate second call (< 30s) MUST use cache and not change fetched_at
    tokio::time::sleep(Duration::from_millis(50)).await;
    let second_prs = collect_pull_requests(&state, &mut pr_cache).await;
    assert_eq!(first_prs, second_prs);
    assert_eq!(
        pr_cache.get(&repo_id).unwrap().fetched_at,
        initial_fetched_at,
        "Immediate call must reuse cache entry without re-querying"
    );

    // 3. Rapid 50 successive calls under stress
    for _ in 0..50 {
        let prs = collect_pull_requests(&state, &mut pr_cache).await;
        assert_eq!(prs, first_prs);
    }
    assert_eq!(
        pr_cache.get(&repo_id).unwrap().fetched_at,
        initial_fetched_at,
        "50 rapid calls must all hit cache"
    );

    // 4. Invalidation upon cache clear
    pr_cache.clear();
    assert!(pr_cache.is_empty());
    let _third_prs = collect_pull_requests(&state, &mut pr_cache).await;
    assert!(pr_cache.contains_key(&repo_id));
    assert!(pr_cache.get(&repo_id).unwrap().fetched_at > initial_fetched_at);
}
