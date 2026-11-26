use super::{
    MANIFEST_FILE_NAME, TX_JOURNAL_FILE_NAME, classify_io_error, remove_file_with_retry,
    rename_with_retry, sanitize_relative_path,
};
use anyhow::{Result, bail};
use std::io::Write;
use std::path::{Path, PathBuf};

pub(super) struct ExtractSession {
    pub target_dir: PathBuf,
    pub tx_path: PathBuf,
    pub backups: Vec<(PathBuf, PathBuf)>,
    pub created_new_files: Vec<PathBuf>,
    pub temp_files: Vec<PathBuf>,
    pub installed_rel_paths: Vec<String>,
    pub total_bytes_written: u64,
}

impl ExtractSession {
    pub(super) fn new(target_dir: &Path) -> Self {
        Self {
            target_dir: target_dir.to_path_buf(),
            tx_path: target_dir.join(TX_JOURNAL_FILE_NAME),
            backups: Vec::new(),
            created_new_files: Vec::new(),
            temp_files: Vec::new(),
            installed_rel_paths: Vec::new(),
            total_bytes_written: 0,
        }
    }

    pub(super) fn begin_transaction(&self) -> Result<()> {
        std::fs::write(&self.tx_path, "MONITOR_TX_V1\n").map_err(|err| {
            classify_io_error(&err, "creating install transaction log", &self.tx_path)
        })
    }

    fn append_tx_line(&self, line: &str) {
        if let Ok(mut file) = std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.tx_path)
        {
            let _ = writeln!(file, "{line}");
            let _ = file.flush();
        }
    }

    pub(super) fn record_backup(&mut self, backup_path: PathBuf, dest_path: PathBuf) {
        if let (Ok(b_rel), Ok(d_rel)) = (
            backup_path.strip_prefix(&self.target_dir),
            dest_path.strip_prefix(&self.target_dir),
        ) {
            let b_str = b_rel.to_string_lossy().replace('\\', "/");
            let d_str = d_rel.to_string_lossy().replace('\\', "/");
            self.append_tx_line(&format!("B\t{b_str}\t{d_str}"));
        }
        self.backups.push((backup_path, dest_path));
    }

    pub(super) fn record_created_file(&mut self, dest_path: PathBuf) {
        if let Ok(rel) = dest_path.strip_prefix(&self.target_dir) {
            let rel_str = rel.to_string_lossy().replace('\\', "/");
            self.append_tx_line(&format!("C\t{rel_str}"));
        }
        self.created_new_files.push(dest_path);
    }

    pub(super) fn commit(&self) {
        for (backup_path, _) in &self.backups {
            if backup_path.exists() {
                let _ = remove_file_with_retry(backup_path);
            }
        }
        for tmp in &self.temp_files {
            if tmp.exists() {
                let _ = remove_file_with_retry(tmp);
            }
        }
        if self.tx_path.exists() {
            let _ = remove_file_with_retry(&self.tx_path);
        }
    }

    pub(super) fn rollback(&self) {
        for tmp in &self.temp_files {
            if tmp.exists() {
                let _ = remove_file_with_retry(tmp);
            }
        }
        for path in self.created_new_files.iter().rev() {
            if path.exists() {
                let _ = remove_file_with_retry(path);
            }
        }
        for (backup_path, dest_path) in self.backups.iter().rev() {
            if backup_path.exists() {
                if dest_path.exists() {
                    let _ = remove_file_with_retry(dest_path);
                }
                let _ = rename_with_retry(backup_path, dest_path);
            }
        }
        if self.tx_path.exists() {
            let _ = remove_file_with_retry(&self.tx_path);
        }
    }
}

pub(super) fn ensure_not_reparse_point(path: &Path) -> Result<()> {
    if crate::engine::is_reparse_or_symlink(path) {
        bail!(
            "Refusing to follow symbolic link or NTFS junction during installation: {}",
            path.display()
        );
    }
    Ok(())
}

pub(super) fn verify_relative_chain_not_reparse(target_dir: &Path, clean_rel: &Path) -> Result<()> {
    let mut cursor = target_dir.to_path_buf();
    for component in clean_rel.components() {
        cursor.push(component);
        if cursor.exists() {
            ensure_not_reparse_point(&cursor)?;
        }
    }
    Ok(())
}

pub(super) fn recover_interrupted_transaction(target_dir: &Path) -> Result<()> {
    let tx_path = target_dir.join(TX_JOURNAL_FILE_NAME);
    if tx_path.is_file() {
        if let Ok(raw) = std::fs::read_to_string(&tx_path) {
            let mut backup_pairs = Vec::new();
            let mut created_rels = Vec::new();
            for line in raw.lines().skip(1) {
                let mut parts = line.split('\t');
                match parts.next() {
                    Some("B") => {
                        if let (Some(b_rel), Some(d_rel)) = (parts.next(), parts.next())
                            && let (Ok(b_clean), Ok(d_clean)) =
                                (sanitize_relative_path(b_rel), sanitize_relative_path(d_rel))
                        {
                            backup_pairs.push((target_dir.join(b_clean), target_dir.join(d_clean)));
                        }
                    }
                    Some("C") => {
                        if let Some(c_rel) = parts.next()
                            && let Ok(c_clean) = sanitize_relative_path(c_rel)
                        {
                            created_rels.push(target_dir.join(c_clean));
                        }
                    }
                    _ => {}
                }
            }

            for created in created_rels.iter().rev() {
                if created.is_file() && !crate::engine::is_reparse_or_symlink(created) {
                    let _ = remove_file_with_retry(created);
                }
            }
            for (backup_path, dest_path) in backup_pairs.iter().rev() {
                if backup_path.is_file() && !crate::engine::is_reparse_or_symlink(backup_path) {
                    if dest_path.exists() {
                        let _ = remove_file_with_retry(dest_path);
                    }
                    let _ = rename_with_retry(backup_path, dest_path);
                }
            }
        }
        let _ = remove_file_with_retry(&tx_path);
    }

    sweep_stale_staging_files(target_dir);
    Ok(())
}

fn sweep_stale_staging_files(dir: &Path) {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if crate::engine::is_reparse_or_symlink(&path) {
            continue;
        }
        if path.is_dir() {
            sweep_stale_staging_files(&path);
        } else if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
            let lower = name.to_ascii_lowercase();
            if lower.ends_with(".install.tmp")
                || lower.ends_with(".upgrade.bak")
                || lower == format!("{MANIFEST_FILE_NAME}.tmp").to_ascii_lowercase()
            {
                let _ = remove_file_with_retry(&path);
            }
        }
    }
}
