use super::{BUNDLE_ENTRY_NAME, PACK_MAGIC, PackStats, sanitize_relative_path};
use anyhow::{Context, Result, anyhow, bail};
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::{BufReader, BufWriter, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

/// Packs `(disk_path, relative_archive_path)` items into a Solid Zstd bundle inside `output_zip`.
pub fn pack_solid_bundle_zip(
    inputs: &[(PathBuf, String)],
    output_zip: &Path,
    compression_level: i32,
) -> Result<PackStats> {
    let mut prepared: Vec<(PathBuf, String, u64)> = Vec::with_capacity(inputs.len());
    let mut total_uncompressed = 0u64;

    for (disk_path, rel) in inputs {
        let clean_rel = sanitize_relative_path(rel)?;
        let normalized_rel = clean_rel.to_string_lossy().replace('\\', "/");
        let meta = std::fs::metadata(disk_path)
            .with_context(|| format!("reading metadata for {}", disk_path.display()))?;
        let len = meta.len();
        total_uncompressed = total_uncompressed.saturating_add(len);
        prepared.push((disk_path.clone(), normalized_rel, len));
    }

    if let Some(parent) = output_zip.parent() {
        std::fs::create_dir_all(parent).context("creating output directory")?;
    }

    let stage_zip = output_zip.with_extension("zip.tmp");
    let stage_pak = output_zip.with_extension("monpak.zst.tmp");

    {
        let pak_file = File::create(&stage_pak).context("creating solid zstd staging file")?;
        let mut encoder = zstd::stream::write::Encoder::new(pak_file, compression_level)
            .context("creating zstd encoder")?;
        encoder.write_all(PACK_MAGIC)?;
        encoder.write_all(&(prepared.len() as u64).to_le_bytes())?;
        encoder.write_all(&total_uncompressed.to_le_bytes())?;

        let mut buf = vec![0u8; 256 * 1024];
        for (disk_path, rel_str, expected_len) in &prepared {
            let path_bytes = rel_str.as_bytes();
            let path_len = u16::try_from(path_bytes.len())
                .map_err(|_| anyhow!("path exceeds u16 length: {rel_str}"))?;
            encoder.write_all(&path_len.to_le_bytes())?;
            encoder.write_all(path_bytes)?;
            encoder.write_all(&expected_len.to_le_bytes())?;

            let mut src = BufReader::new(
                File::open(disk_path)
                    .with_context(|| format!("opening input file {}", disk_path.display()))?,
            );
            let mut copied = 0u64;
            loop {
                let n = src.read(&mut buf)?;
                if n == 0 {
                    break;
                }
                encoder.write_all(&buf[..n])?;
                copied += n as u64;
            }
            if copied != *expected_len {
                bail!(
                    "file size changed while packing {}: expected {}, read {}",
                    disk_path.display(),
                    expected_len,
                    copied
                );
            }
        }
        let pak_inner = encoder.finish().context("finalizing zstd stream")?;
        pak_inner.sync_all()?;
    }

    let compressed_zip_bytes = {
        let zip_file = File::create(&stage_zip).context("creating output zip staging file")?;
        let mut writer = ZipWriter::new(BufWriter::new(zip_file));
        let options = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
        writer.start_file(BUNDLE_ENTRY_NAME, options)?;
        let mut src = BufReader::new(File::open(&stage_pak)?);
        std::io::copy(&mut src, &mut writer)?;
        let mut inner = writer
            .finish()?
            .into_inner()
            .map_err(std::io::IntoInnerError::into_error)?;
        inner.flush()?;
        inner.seek(SeekFrom::End(0))?
    };
    let _ = std::fs::remove_file(&stage_pak);

    if output_zip.exists() {
        let _ = std::fs::remove_file(output_zip);
    }
    std::fs::rename(&stage_zip, output_zip).context("moving staged zip into output path")?;

    let mut hasher = Sha256::new();
    let mut hash_reader = BufReader::new(File::open(output_zip)?);
    let mut hbuf = [0u8; 64 * 1024];
    loop {
        let n = hash_reader.read(&mut hbuf)?;
        if n == 0 {
            break;
        }
        hasher.update(&hbuf[..n]);
    }
    let digest = hasher.finalize();
    let mut sha256_hex = String::with_capacity(64);
    for byte in digest {
        use std::fmt::Write as _;
        let _ = write!(sha256_hex, "{byte:02x}");
    }

    Ok(PackStats {
        file_count: prepared.len() as u64,
        uncompressed_bytes: total_uncompressed,
        compressed_zip_bytes,
        sha256_hex,
    })
}
