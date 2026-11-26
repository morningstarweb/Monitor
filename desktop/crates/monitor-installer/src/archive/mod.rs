pub mod extract;
pub mod pack;
pub mod session;

pub use extract::extract_payload_bytes;
pub use pack::pack_solid_bundle_zip;

use anyhow::{Context, Result, anyhow, bail};
use std::fs::File;
use std::io::{Cursor, Read};
use std::path::{Component, Path, PathBuf};
use std::time::Duration;
use zip::ZipArchive;

pub const BUNDLE_ENTRY_NAME: &str = "bundle.monpak.zst";
pub const MANIFEST_FILE_NAME: &str = ".monitor-install-manifest.txt";
pub const TX_JOURNAL_FILE_NAME: &str = ".monitor-install-tx.dat";
pub(super) const PACK_MAGIC: &[u8; 8] = b"MONPAK01";
pub(super) const HEADER_SIZE: usize = 8 + 8 + 8;

#[derive(Clone, Debug)]
pub struct PayloadSummary {
    pub file_count: u64,
    pub total_uncompressed_bytes: u64,
}

#[derive(Clone, Debug)]
pub struct PackStats {
    pub file_count: u64,
    pub uncompressed_bytes: u64,
    pub compressed_zip_bytes: u64,
    pub sha256_hex: String,
}

#[derive(Clone, Debug)]
pub struct ExtractProgress {
    pub file_index: u64,
    pub total_files: u64,
    pub bytes_written: u64,
    pub total_bytes: u64,
    pub current_file: String,
}

#[derive(Clone, Debug)]
pub struct ExtractReport {
    pub installed_paths: Vec<String>,
    pub total_bytes_written: u64,
    pub estimated_size_kb: u32,
}

/// Validates and normalizes an archive-relative path so extraction cannot escape `target_dir`.
pub fn sanitize_relative_path(raw: &str) -> Result<PathBuf> {
    if raw.is_empty() || raw.contains('\0') {
        bail!("archive entry contains empty or NUL path");
    }
    let normalized = raw.replace('\\', "/");
    if normalized.starts_with('/') {
        bail!("archive entry path must be relative: {raw}");
    }
    let bytes = normalized.as_bytes();
    if bytes.len() >= 2 && bytes[1] == b':' {
        bail!("archive entry contains drive prefix: {raw}");
    }

    let mut clean = PathBuf::new();
    for segment in normalized.split('/') {
        if segment.is_empty() || segment == "." {
            continue;
        }
        if segment == ".." {
            bail!("archive entry traverses parent directory: {raw}");
        }
        let candidate = Path::new(segment);
        match candidate.components().next() {
            Some(Component::Normal(_)) => clean.push(segment),
            _ => bail!("invalid path component in archive entry: {raw}"),
        }
    }

    if clean.as_os_str().is_empty() {
        bail!("archive entry resolves to empty path: {raw}");
    }
    Ok(clean)
}

/// Inspects an embedded `payload.zip` quickly to compute file count and uncompressed byte total.
pub fn inspect_payload_summary(payload_bytes: &[u8]) -> Result<PayloadSummary> {
    let reader = Cursor::new(payload_bytes);
    let mut archive = ZipArchive::new(reader).context("reading payload archive")?;

    if let Ok(entry) = archive.by_name(BUNDLE_ENTRY_NAME) {
        let mut decoder = zstd::stream::read::Decoder::new(entry)
            .context("initializing solid bundle zstd reader")?;
        let mut header = [0u8; HEADER_SIZE];
        decoder
            .read_exact(&mut header)
            .context("reading solid bundle header")?;
        if &header[..8] != PACK_MAGIC {
            bail!("corrupt solid payload header magic");
        }
        let file_count = u64::from_le_bytes(
            header[8..16]
                .try_into()
                .map_err(|_| anyhow!("invalid file count slice"))?,
        );
        let total_uncompressed_bytes = u64::from_le_bytes(
            header[16..24]
                .try_into()
                .map_err(|_| anyhow!("invalid total bytes slice"))?,
        );
        return Ok(PayloadSummary {
            file_count,
            total_uncompressed_bytes,
        });
    }

    let mut total_uncompressed_bytes = 0u64;
    let mut file_count = 0u64;
    for i in 0..archive.len() {
        let file = archive.by_index(i)?;
        if !file.is_dir() {
            file_count += 1;
            total_uncompressed_bytes = total_uncompressed_bytes.saturating_add(file.size());
        }
    }
    Ok(PayloadSummary {
        file_count,
        total_uncompressed_bytes,
    })
}

pub(super) fn create_stage_file_with_retry(stage_path: &Path) -> Result<File> {
    let mut last_err = None;
    for attempt in 0..6 {
        match File::create(stage_path) {
            Ok(file) => return Ok(file),
            Err(err) => {
                let transient = err.kind() == std::io::ErrorKind::PermissionDenied
                    || err.raw_os_error() == Some(32)
                    || err.raw_os_error() == Some(5);
                if transient && attempt < 5 {
                    last_err = Some(err);
                    std::thread::sleep(Duration::from_millis(80 * (attempt + 1)));
                    continue;
                }
                return Err(classify_io_error(&err, "creating temp file", stage_path));
            }
        }
    }
    Err(classify_io_error(
        &last_err.unwrap_or_else(|| {
            std::io::Error::new(std::io::ErrorKind::PermissionDenied, "file locked")
        }),
        "creating temp file",
        stage_path,
    ))
}

pub(super) fn classify_io_error(err: &std::io::Error, action: &str, path: &Path) -> anyhow::Error {
    if err.kind() == std::io::ErrorKind::StorageFull
        || matches!(err.raw_os_error(), Some(39) | Some(112))
    {
        return anyhow!(
            "Disk full while {action} {}. Free up space on the target drive and retry.",
            path.display()
        );
    }
    anyhow!("I/O error while {action} {}: {err}", path.display())
}

pub(super) fn rename_with_retry(src: &Path, dest: &Path) -> Result<()> {
    let mut last_err = None;
    for attempt in 0..6 {
        match std::fs::rename(src, dest) {
            Ok(()) => return Ok(()),
            Err(e) => {
                last_err = Some(e);
                std::thread::sleep(Duration::from_millis(120 * (attempt + 1)));
            }
        }
    }
    Err(anyhow!(
        "failed to move {} -> {}: {}",
        src.display(),
        dest.display(),
        last_err
            .map(|e| e.to_string())
            .unwrap_or_else(|| "file locked".to_string())
    ))
}

pub(super) fn swap_with_retry(src_tmp: &Path, dest: &Path) -> Result<()> {
    let mut last_err = None;
    for attempt in 0..6 {
        if dest.exists()
            && let Err(e) = std::fs::remove_file(dest)
        {
            last_err = Some(e);
            std::thread::sleep(Duration::from_millis(120 * (attempt + 1)));
            continue;
        }
        match std::fs::rename(src_tmp, dest) {
            Ok(()) => return Ok(()),
            Err(e) => {
                last_err = Some(e);
                std::thread::sleep(Duration::from_millis(120 * (attempt + 1)));
            }
        }
    }
    let _ = std::fs::remove_file(src_tmp);
    Err(anyhow!(
        "failed to install {}: {}",
        dest.display(),
        last_err
            .map(|e| e.to_string())
            .unwrap_or_else(|| "file locked".to_string())
    ))
}

pub fn remove_file_with_retry(path: &Path) -> Result<()> {
    for attempt in 0..5 {
        match std::fs::remove_file(path) {
            Ok(()) => return Ok(()),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(()),
            Err(_) if attempt < 4 => {
                std::thread::sleep(Duration::from_millis(100 * (attempt + 1)));
            }
            Err(e) => return Err(e.into()),
        }
    }
    Ok(())
}

pub fn read_install_manifest(target_dir: &Path) -> Vec<String> {
    let manifest_path = target_dir.join(MANIFEST_FILE_NAME);
    let Ok(raw) = std::fs::read_to_string(manifest_path) else {
        return Vec::new();
    };
    raw.lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .map(ToOwned::to_owned)
        .collect()
}

pub(super) fn write_install_manifest(target_dir: &Path, rel_paths: &[String]) -> Result<()> {
    let manifest_path = target_dir.join(MANIFEST_FILE_NAME);
    let tmp_path = target_dir.join(format!("{MANIFEST_FILE_NAME}.tmp"));
    let content = rel_paths.join("\n");
    std::fs::write(&tmp_path, content).context("writing install manifest")?;
    swap_with_retry(&tmp_path, &manifest_path)
}
