use super::session::{
    ExtractSession, ensure_not_reparse_point, recover_interrupted_transaction,
    verify_relative_chain_not_reparse,
};
use super::{
    BUNDLE_ENTRY_NAME, ExtractProgress, ExtractReport, HEADER_SIZE, PACK_MAGIC, classify_io_error,
    create_stage_file_with_retry, read_install_manifest, remove_file_with_retry, rename_with_retry,
    sanitize_relative_path, swap_with_retry, write_install_manifest,
};
use anyhow::{Context, Result, anyhow, bail};
use std::collections::HashSet;
use std::io::{BufWriter, Cursor, Read, Seek, Write};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use zip::ZipArchive;

/// Extracts `payload.zip` (Solid `MONPAK01` Zstd bundle or ordinary ZIP entries)
/// into `target_dir` with full rollback on errors, crash-recovery journal cleanup, and stale pruning.
pub fn extract_payload_bytes<F>(
    payload_bytes: &[u8],
    target_dir: &Path,
    cancel_flag: &AtomicBool,
    mut on_progress: F,
) -> Result<ExtractReport>
where
    F: FnMut(ExtractProgress),
{
    std::fs::create_dir_all(target_dir).context("creating installation directory")?;
    ensure_not_reparse_point(target_dir)?;
    recover_interrupted_transaction(target_dir)?;

    let reader = Cursor::new(payload_bytes);
    let mut archive = ZipArchive::new(reader).context(
        "Installer payload appears corrupted or truncated. Re-download the official installer and verify its SHA-256 checksum.",
    )?;
    let is_solid = archive.by_name(BUNDLE_ENTRY_NAME).is_ok();

    let previous_manifest = read_install_manifest(target_dir);
    let mut session = ExtractSession::new(target_dir);
    session.begin_transaction()?;

    let result = if is_solid {
        match archive.by_name(BUNDLE_ENTRY_NAME).context(
            "Installer solid payload entry missing or corrupted. Re-download the official installer.",
        ) {
            Ok(entry) => extract_solid_zstd_stream(
                entry,
                target_dir,
                cancel_flag,
                &mut session,
                &mut on_progress,
            ),
            Err(err) => Err(err),
        }
    } else {
        extract_standard_zip(
            &mut archive,
            target_dir,
            cancel_flag,
            &mut session,
            &mut on_progress,
        )
    };

    if let Err(err) = result {
        session.rollback();
        return Err(err);
    }

    let new_set: HashSet<String> = session.installed_rel_paths.iter().cloned().collect();
    for old_rel in previous_manifest {
        if !new_set.contains(&old_rel)
            && let Ok(rel_path) = sanitize_relative_path(&old_rel)
        {
            let obsolete = target_dir.join(rel_path);
            if obsolete.is_file() && !crate::engine::is_reparse_or_symlink(&obsolete) {
                let _ = remove_file_with_retry(&obsolete);
            }
        }
    }

    if let Err(err) = write_install_manifest(target_dir, &session.installed_rel_paths) {
        session.rollback();
        return Err(err);
    }
    session.commit();

    let estimated_size_kb = (session.total_bytes_written / 1024).clamp(1, u32::MAX as u64) as u32;
    Ok(ExtractReport {
        installed_paths: session.installed_rel_paths,
        total_bytes_written: session.total_bytes_written,
        estimated_size_kb,
    })
}

fn extract_solid_zstd_stream<R: Read, F>(
    entry: R,
    target_dir: &Path,
    cancel_flag: &AtomicBool,
    session: &mut ExtractSession,
    on_progress: &mut F,
) -> Result<()>
where
    F: FnMut(ExtractProgress),
{
    let mut decoder = zstd::stream::read::Decoder::new(entry).context(
        "Installer payload Zstd stream is corrupted. Re-download the official installer.",
    )?;
    let mut header = [0u8; HEADER_SIZE];
    decoder.read_exact(&mut header).context(
        "Installer payload header is corrupted or truncated. Re-download the official installer.",
    )?;
    if &header[..8] != PACK_MAGIC {
        bail!("Installer payload header magic mismatch (corrupted package).");
    }

    let total_files = u64::from_le_bytes(
        header[8..16]
            .try_into()
            .map_err(|_| anyhow!("invalid solid file count"))?,
    );
    let total_bytes = u64::from_le_bytes(
        header[16..24]
            .try_into()
            .map_err(|_| anyhow!("invalid solid total bytes"))?,
    );

    let mut path_len_buf = [0u8; 2];
    let mut len_buf = [0u8; 8];
    let mut copy_buf = vec![0u8; 128 * 1024];

    for idx in 0..total_files {
        if cancel_flag.load(Ordering::Relaxed) {
            bail!("Installation cancelled by user.");
        }

        decoder.read_exact(&mut path_len_buf).context(
            "Installer payload stream truncated while reading file path length (corrupted archive).",
        )?;
        let path_len = u16::from_le_bytes(path_len_buf) as usize;
        let mut path_bytes = vec![0u8; path_len];
        decoder.read_exact(&mut path_bytes).context(
            "Installer payload stream truncated while reading file path (corrupted archive).",
        )?;
        let rel_str =
            String::from_utf8(path_bytes).context("invalid UTF-8 path in solid bundle")?;
        let clean_rel = sanitize_relative_path(&rel_str)?;
        let normalized_rel = clean_rel.to_string_lossy().replace('\\', "/");

        decoder.read_exact(&mut len_buf).context(
            "Installer payload stream truncated while reading file length (corrupted archive).",
        )?;
        let file_len = u64::from_le_bytes(len_buf);

        let dest = target_dir.join(&clean_rel);
        let mut limited = (&mut decoder).take(file_len);
        write_stream_atomic(
            &mut limited,
            target_dir,
            &clean_rel,
            &dest,
            file_len,
            cancel_flag,
            &mut copy_buf,
            session,
        )?;

        session.installed_rel_paths.push(normalized_rel.clone());
        session.total_bytes_written = session.total_bytes_written.saturating_add(file_len);

        on_progress(ExtractProgress {
            file_index: idx + 1,
            total_files,
            bytes_written: session.total_bytes_written,
            total_bytes,
            current_file: normalized_rel,
        });
    }

    Ok(())
}

fn extract_standard_zip<R: Read + Seek, F>(
    archive: &mut ZipArchive<R>,
    target_dir: &Path,
    cancel_flag: &AtomicBool,
    session: &mut ExtractSession,
    on_progress: &mut F,
) -> Result<()>
where
    F: FnMut(ExtractProgress),
{
    let total_entries = archive.len() as u64;
    let mut total_bytes = 0u64;
    for i in 0..archive.len() {
        let f = archive.by_index(i)?;
        total_bytes = total_bytes.saturating_add(f.size());
    }

    let mut copy_buf = vec![0u8; 64 * 1024];
    for i in 0..archive.len() {
        if cancel_flag.load(Ordering::Relaxed) {
            bail!("Installation cancelled by user.");
        }
        let mut file = archive.by_index(i)?;
        let raw_name = file
            .name()
            .map(std::borrow::Cow::into_owned)
            .context("invalid zip entry name")?;
        if file.is_dir() {
            if let Ok(clean_rel) = sanitize_relative_path(&raw_name) {
                let dir_dest = target_dir.join(clean_rel);
                std::fs::create_dir_all(&dir_dest)?;
                ensure_not_reparse_point(&dir_dest)?;
            }
            continue;
        }

        let clean_rel = sanitize_relative_path(&raw_name)?;
        let normalized_rel = clean_rel.to_string_lossy().replace('\\', "/");
        let dest = target_dir.join(&clean_rel);
        let file_size = file.size();

        write_stream_atomic(
            &mut file,
            target_dir,
            &clean_rel,
            &dest,
            file_size,
            cancel_flag,
            &mut copy_buf,
            session,
        )?;

        session.installed_rel_paths.push(normalized_rel.clone());
        session.total_bytes_written = session.total_bytes_written.saturating_add(file_size);

        on_progress(ExtractProgress {
            file_index: (i as u64) + 1,
            total_files: total_entries,
            bytes_written: session.total_bytes_written,
            total_bytes,
            current_file: normalized_rel,
        });
    }

    Ok(())
}

#[allow(clippy::too_many_arguments)]
fn write_stream_atomic<R: Read>(
    reader: &mut R,
    target_dir: &Path,
    clean_rel: &Path,
    dest: &Path,
    expected_len: u64,
    cancel_flag: &AtomicBool,
    buf: &mut [u8],
    session: &mut ExtractSession,
) -> Result<()> {
    verify_relative_chain_not_reparse(target_dir, clean_rel)?;
    if let Some(parent) = dest.parent()
        && !parent.exists()
    {
        std::fs::create_dir_all(parent)
            .with_context(|| format!("creating directory {}", parent.display()))?;
    }

    let existed_before = dest.exists();
    let file_name = dest
        .file_name()
        .and_then(|s| s.to_str())
        .unwrap_or("payload");
    let stage_path = dest.with_file_name(format!(".{file_name}.install.tmp"));
    session.temp_files.push(stage_path.clone());

    let stage_file = create_stage_file_with_retry(&stage_path)?;
    let mut written = 0u64;
    {
        let mut out = BufWriter::new(stage_file);
        loop {
            if cancel_flag.load(Ordering::Relaxed) {
                drop(out);
                let _ = remove_file_with_retry(&stage_path);
                bail!("Installation cancelled by user.");
            }
            let n = reader.read(buf).context(
                "Installer payload stream appears corrupted or truncated. Re-download the official installer.",
            )?;
            if n == 0 {
                break;
            }
            out.write_all(&buf[..n])
                .map_err(|err| classify_io_error(&err, "writing", &stage_path))?;
            written += n as u64;
        }
        out.flush()
            .map_err(|err| classify_io_error(&err, "flushing", &stage_path))?;
    }

    if written != expected_len {
        let _ = remove_file_with_retry(&stage_path);
        bail!(
            "Installer payload is corrupted or truncated at {} (expected {} bytes, got {}). Re-download the official installer.",
            dest.display(),
            expected_len,
            written
        );
    }

    if existed_before {
        let backup_path = dest.with_file_name(format!(".{file_name}.upgrade.bak"));
        if backup_path.exists() {
            let _ = remove_file_with_retry(&backup_path);
        }
        session.record_backup(backup_path.clone(), dest.to_path_buf());
        rename_with_retry(dest, &backup_path)?;
        swap_with_retry(&stage_path, dest)?;
    } else {
        swap_with_retry(&stage_path, dest)?;
        session.record_created_file(dest.to_path_buf());
    }
    Ok(())
}
