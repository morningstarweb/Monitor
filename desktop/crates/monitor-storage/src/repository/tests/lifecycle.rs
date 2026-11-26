#[test]
fn test_storage_lifecycle() -> Result<()> {
    let storage = Storage::in_memory()?;

    let repo_id = RepoId::new("r_test1");
    storage.register_repo(
        &repo_id,
        "D:\\Test\\Repo",
        "Test Repo",
        TrustProfile::Restricted,
    )?;

    let rev = storage.get_repo_revision(&repo_id)?;
    assert_eq!(rev, Some(Revision(0)));

    let dev_id = DeviceId::new("dev_pixel9");
    let hash = DeviceTokenHash::new("hash1234");
    storage.insert_device(&dev_id, "Pixel 9", &hash, DeviceRole::Operator)?;

    let fetched = storage.get_device_by_token_hash(&hash)?;
    assert!(matches!(fetched, Some((_, DeviceRole::Operator, false))));

    Ok(())
}

#[test]
#[allow(clippy::expect_used)]
fn wal_read_conn_does_not_block_behind_writer_lock() -> Result<()> {
    let dir = tempfile::tempdir().expect("temp dir");
    let db_path = dir.path().join("wal_concurrent.db");
    let storage = Storage::open(&db_path)?;

    let repo_id = RepoId::new("r_wal");
    storage.register_repo(&repo_id, "D:\\Test\\Wal", "WAL Repo", TrustProfile::Trusted)?;
    storage.set_setting("wal_probe", "ready")?;

    // Hold the writer connection mutex and an uncommitted write lock while reading via `read_conn`.
    let writer = match storage.conn.lock() {
        Ok(c) => c,
        Err(p) => p.into_inner(),
    };
    writer.execute_batch("BEGIN IMMEDIATE;")?;

    assert_eq!(storage.get_setting("wal_probe")?.as_deref(), Some("ready"));
    assert_eq!(storage.get_repo_revision(&repo_id)?, Some(Revision(0)));
    assert!(storage.get_repository(&repo_id)?.is_some());
    assert!(!storage.list_repositories()?.is_empty());
    assert!(storage.list_devices()?.is_empty());

    let ro = match storage.read_conn.lock() {
        Ok(c) => c,
        Err(p) => p.into_inner(),
    };
    let write_attempt = ro.execute("INSERT INTO settings (key, value) VALUES ('x', 'y')", []);
    assert!(write_attempt.is_err(), "read_conn must enforce query_only");
    drop(ro);

    writer.execute_batch("ROLLBACK;")?;
    drop(writer);
    Ok(())
}
