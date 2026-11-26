use anyhow::{Context, Result, bail};
use std::io::Write;
use std::path::{Path, PathBuf};

fn collect_dir_files(base: &Path, prefix: &str, out: &mut Vec<(PathBuf, String)>) -> Result<()> {
    if !base.is_dir() {
        return Ok(());
    }
    let mut stack = vec![base.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let mut entries: Vec<_> = std::fs::read_dir(&dir)
            .with_context(|| format!("reading directory {}", dir.display()))?
            .collect::<std::result::Result<Vec<_>, _>>()?;
        entries.sort_by_key(std::fs::DirEntry::file_name);

        for entry in entries {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
            } else if path.is_file() {
                let rel_to_base = path
                    .strip_prefix(base)
                    .with_context(|| format!("computing relative path for {}", path.display()))?;
                let rel_str = if prefix.is_empty() {
                    rel_to_base.to_string_lossy().replace('\\', "/")
                } else {
                    format!(
                        "{prefix}/{}",
                        rel_to_base.to_string_lossy().replace('\\', "/")
                    )
                };
                out.push((path, rel_str));
            }
        }
    }
    Ok(())
}

fn run() -> Result<()> {
    let mut args = std::env::args().skip(1);
    let Some(project_root) = args.next().map(PathBuf::from) else {
        bail!("usage: pack-payload <project_root> [compression_level]");
    };
    let level = args
        .next()
        .and_then(|s| s.parse::<i32>().ok())
        .unwrap_or(19);

    let mut inputs: Vec<(PathBuf, String)> = Vec::new();
    let monitor_exe = project_root.join("bin").join("Monitor.exe");
    if !monitor_exe.is_file() {
        bail!("missing Monitor.exe at {}", monitor_exe.display());
    }
    inputs.push((monitor_exe, "Monitor.exe".to_string()));

    let uninstaller_exe = project_root
        .join("desktop")
        .join("target")
        .join("release")
        .join("monitor-uninstaller.exe");
    if uninstaller_exe.is_file() {
        inputs.push((uninstaller_exe, "uninstall.exe".to_string()));
    }

    for doc in ["LICENSE", "THIRD_PARTY_NOTICES.txt"] {
        let doc_path = project_root.join(doc);
        if doc_path.is_file() {
            inputs.push((doc_path, doc.to_string()));
        }
    }

    let installer_dir = project_root.join("installer");
    for asset in ["monitor.ico", "EULA.txt", "PRIVACY.txt"] {
        let p = installer_dir.join(asset);
        if p.is_file() {
            inputs.push((p, asset.to_string()));
        }
    }

    collect_dir_files(&installer_dir.join("redist"), "", &mut inputs)?;
    collect_dir_files(
        &installer_dir.join("tools").join("git"),
        "tools/git",
        &mut inputs,
    )?;

    inputs.sort_by(|a, b| a.1.cmp(&b.1));

    let crate_dir = project_root
        .join("desktop")
        .join("crates")
        .join("monitor-installer");
    let output_zip = crate_dir.join("payload.zip");
    let manifest_sha = crate_dir.join("payload.expected.sha256");

    let stats = monitor_installer::archive::pack_solid_bundle_zip(&inputs, &output_zip, level)?;
    std::fs::write(&manifest_sha, format!("{}  payload.zip", stats.sha256_hex))
        .context("writing payload.expected.sha256")?;

    let summary = format!(
        "Packed {} files ({:.2} MB raw -> {:.2} MB compressed) sha256={}\n",
        stats.file_count,
        stats.uncompressed_bytes as f64 / (1024.0 * 1024.0),
        stats.compressed_zip_bytes as f64 / (1024.0 * 1024.0),
        stats.sha256_hex
    );
    let mut out = std::io::stdout().lock();
    out.write_all(summary.as_bytes())?;
    out.flush()?;
    Ok(())
}

fn main() {
    if let Err(err) = run() {
        let _ = std::io::stderr()
            .lock()
            .write_all(format!("pack-payload error: {err:#}\n").as_bytes());
        std::process::exit(1);
    }
}
