impl Storage {
    pub fn remove_repository(&self, repo_id: &RepoId) -> Result<bool> {
        let conn = self
            .conn
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        Ok(conn.execute(
            "DELETE FROM repositories WHERE repo_id = ?1",
            [repo_id.as_str()],
        )? > 0)
    }
    pub fn open(path: impl AsRef<Path>) -> Result<Self> {
        let path_ref = path.as_ref();
        let mut conn = Connection::open(path_ref)?;
        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA busy_timeout = 5000;
             PRAGMA foreign_keys = ON;",
        )?;
        run_migrations(&mut conn)?;

        let read_conn = Connection::open_with_flags(
            path_ref,
            rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NO_MUTEX,
        )?;
        read_conn.execute_batch(
            "PRAGMA busy_timeout = 5000;
             PRAGMA query_only = ON;",
        )?;

        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
            read_conn: Arc::new(Mutex::new(read_conn)),
        })
    }

    pub fn in_memory() -> Result<Self> {
        let mut conn = Connection::open_in_memory()?;
        conn.execute_batch(
            "PRAGMA busy_timeout = 5000;
             PRAGMA foreign_keys = ON;",
        )?;
        run_migrations(&mut conn)?;
        let shared = Arc::new(Mutex::new(conn));
        Ok(Self {
            conn: Arc::clone(&shared),
            read_conn: shared,
        })
    }

    pub fn register_repo(
        &self,
        repo_id: &RepoId,
        canonical_path: &str,
        display_name: &str,
        trust: TrustProfile,
    ) -> Result<()> {
        let conn = match self.conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let now = chrono::Utc::now().timestamp_millis();
        let trust_str = match trust {
            TrustProfile::Restricted => "Restricted",
            TrustProfile::Trusted => "Trusted",
        };

        conn.execute(
            "INSERT INTO repositories (repo_id, canonical_path, display_name, trust_profile, registered_at_utc_ms, watch_enabled, revision)
             VALUES (?1, ?2, ?3, ?4, ?5, 1, 0)
             ON CONFLICT(canonical_path) DO UPDATE SET display_name = ?3",
            params![repo_id.as_str(), canonical_path, display_name, trust_str, now],
        )?;
        Ok(())
    }

    pub fn get_repository(&self, repo_id: &RepoId) -> Result<Option<StoredRepository>> {
        let conn = match self.read_conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let mut stmt = conn.prepare(
            "SELECT repo_id, canonical_path, display_name, trust_profile, registered_at_utc_ms, watch_enabled, revision
             FROM repositories WHERE repo_id = ?1",
        )?;
        let mut rows = stmt.query(params![repo_id.as_str()])?;
        if let Some(row) = rows.next()? {
            let id: String = row.get(0)?;
            let path: String = row.get(1)?;
            let name: String = row.get(2)?;
            let trust_str: String = row.get(3)?;
            let reg_at: i64 = row.get(4)?;
            let watch: bool = row.get(5)?;
            let rev: i64 = row.get(6)?;
            let trust = if trust_str == "Trusted" {
                TrustProfile::Trusted
            } else {
                TrustProfile::Restricted
            };
            Ok(Some(StoredRepository {
                repo_id: RepoId::new(id),
                canonical_path: path,
                display_name: name,
                trust_profile: trust,
                registered_at_utc_ms: reg_at,
                watch_enabled: watch,
                revision: Revision(rev as u64),
            }))
        } else {
            Ok(None)
        }
    }

    pub fn list_repositories(&self) -> Result<Vec<StoredRepository>> {
        let conn = match self.read_conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let mut stmt = conn.prepare(
            "SELECT repo_id, canonical_path, display_name, trust_profile, registered_at_utc_ms, watch_enabled, revision
             FROM repositories ORDER BY display_name ASC",
        )?;
        let mut rows = stmt.query([])?;
        let mut list = Vec::new();
        while let Some(row) = rows.next()? {
            let id: String = row.get(0)?;
            let path: String = row.get(1)?;
            let name: String = row.get(2)?;
            let trust_str: String = row.get(3)?;
            let reg_at: i64 = row.get(4)?;
            let watch: bool = row.get(5)?;
            let rev: i64 = row.get(6)?;
            let trust = if trust_str == "Trusted" {
                TrustProfile::Trusted
            } else {
                TrustProfile::Restricted
            };
            list.push(StoredRepository {
                repo_id: RepoId::new(id),
                canonical_path: path,
                display_name: name,
                trust_profile: trust,
                registered_at_utc_ms: reg_at,
                watch_enabled: watch,
                revision: Revision(rev as u64),
            });
        }
        Ok(list)
    }

    pub fn get_repo_revision(&self, repo_id: &RepoId) -> Result<Option<Revision>> {
        let conn = match self.read_conn.lock() {
            Ok(c) => c,
            Err(p) => p.into_inner(),
        };
        let mut stmt = conn.prepare("SELECT revision FROM repositories WHERE repo_id = ?1")?;
        let mut rows = stmt.query(params![repo_id.as_str()])?;
        if let Some(row) = rows.next()? {
            let rev: i64 = row.get(0)?;
            Ok(Some(Revision(rev as u64)))
        } else {
            Ok(None)
        }
    }
}
