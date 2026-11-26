use crate::archive::{
    MANIFEST_FILE_NAME, TX_JOURNAL_FILE_NAME, read_install_manifest, remove_file_with_retry,
    sanitize_relative_path,
};
use crate::engine::registry::{
    read_installed_metadata, remove_installer_preferences, remove_startup_registry,
    unregister_uninstaller,
};
use crate::engine::shortcut::{desktop_shortcut_path, remove_shortcut, start_menu_shortcut_path};
use crate::engine::{resolve_windows_system_exe, terminate_running_monitor};
use crate::state::default_install_directory;
use anyhow::Result;
use std::collections::BTreeSet;
use std::path::{Path, PathBuf};

/// Resolves the active installation directory for uninstallation:
/// 1) Parent folder of `uninstall.exe` if it contains Monitor installation artifacts
/// 2) HKCU `InstallLocation` registry value
/// 3) Default `%LOCALAPPDATA%\Programs\Monitor` directory
pub fn resolve_uninstall_directory() -> PathBuf {
    if let Ok(exe) = std::env::current_exe()
        && let Some(parent) = exe.parent()
        && (parent.join("Monitor.exe").is_file() || parent.join(MANIFEST_FILE_NAME).is_file())
    {
        return parent.to_path_buf();
    }

    if let Some((reg_dir, _)) = read_installed_metadata() {
        return reg_dir;
    }

    default_install_directory()
}

pub fn execute_uninstallation() -> Result<()> {
    let install_dir = resolve_uninstall_directory();
    execute_uninstallation_at(&install_dir)
}

pub fn execute_uninstallation_at(install_dir: &Path) -> Result<()> {
    tracing::info!(
        "Starting Monitor clean uninstallation from {}...",
        install_dir.display()
    );

    // 1. Terminate running Monitor host & bundled tools strictly within `install_dir`.
    terminate_running_monitor(Some(install_dir));

    // 2. Remove Shortcuts.
    if let Some(start_lnk) = start_menu_shortcut_path() {
        remove_shortcut(&start_lnk);
    }
    if let Some(desk_lnk) = desktop_shortcut_path() {
        remove_shortcut(&desk_lnk);
    }

    // 3. Remove Registry Integrations.
    let _ = remove_startup_registry();
    let _ = unregister_uninstaller();

    // 4. Clean Install Files safely (manifest-driven when available so shared parent dirs aren't harmed).
    let current_exe = std::env::current_exe().ok();
    if install_dir.exists() {
        let manifest = read_install_manifest(install_dir);
        if !manifest.is_empty() {
            remove_manifest_files(install_dir, &manifest, current_exe.as_deref());
        } else {
            remove_known_install_contents(install_dir, current_exe.as_deref());
        }

        if let Some(exe_path) = current_exe.as_deref()
            && exe_path.starts_with(install_dir)
        {
            schedule_self_cleanup(exe_path, install_dir);
        } else {
            let _ = std::fs::remove_dir(install_dir);
        }
    }

    tracing::info!("Uninstallation completed successfully.");
    Ok(())
}

fn remove_manifest_files(install_dir: &Path, manifest: &[String], current_exe: Option<&Path>) {
    let mut parent_dirs = BTreeSet::new();
    for rel in manifest {
        let Ok(clean_rel) = sanitize_relative_path(rel) else {
            continue;
        };
        let full = install_dir.join(&clean_rel);
        let is_self = current_exe.is_some_and(|exe| exe == full)
            || full
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .eq_ignore_ascii_case("uninstall.exe");
        if !is_self && full.is_file() {
            let _ = remove_file_with_retry(&full);
        }
        let mut cursor = full.parent();
        while let Some(dir) = cursor {
            if dir == install_dir || !dir.starts_with(install_dir) {
                break;
            }
            parent_dirs.insert(dir.to_path_buf());
            cursor = dir.parent();
        }
    }

    let manifest_file = install_dir.join(MANIFEST_FILE_NAME);
    let _ = remove_file_with_retry(&manifest_file);
    let tx_file = install_dir.join(TX_JOURNAL_FILE_NAME);
    let _ = remove_file_with_retry(&tx_file);

    // Remove empty nested subdirectories deepest-first.
    for dir in parent_dirs.into_iter().rev() {
        let _ = std::fs::remove_dir(dir);
    }
}

fn remove_known_install_contents(install_dir: &Path, current_exe: Option<&Path>) {
    const KNOWN_TOP_FILES: [&str; 9] = [
        "Monitor.exe",
        "monitor.ico",
        MANIFEST_FILE_NAME,
        TX_JOURNAL_FILE_NAME,
        "LICENSE",
        "THIRD_PARTY_NOTICES.txt",
        "EULA.txt",
        "PRIVACY.txt",
        "uninstall.exe",
    ];

    for name in KNOWN_TOP_FILES {
        let path = install_dir.join(name);
        let is_self = current_exe.is_some_and(|exe| exe == path)
            || name.eq_ignore_ascii_case("uninstall.exe");
        if !is_self && path.is_file() {
            let _ = remove_file_with_retry(&path);
        }
    }

    // Remove any interrupted installer staging/backup artifacts owned by Monitor.
    if let Ok(entries) = std::fs::read_dir(install_dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            let file_name = entry.file_name();
            let lower = file_name.to_string_lossy().to_ascii_lowercase();
            if path.is_file()
                && (lower.ends_with(".install.tmp") || lower.ends_with(".upgrade.bak"))
            {
                let _ = remove_file_with_retry(&path);
            }
        }
    }

    // Remove bundled portable Git subtree cleanly without deleting arbitrary user folders.
    let bundled_git_dir = install_dir.join("tools").join("git");
    if bundled_git_dir.is_dir() {
        let _ = std::fs::remove_dir_all(&bundled_git_dir);
    }
    let tools_dir = install_dir.join("tools");
    if tools_dir.is_dir() {
        let _ = std::fs::remove_dir(&tools_dir);
    }
}

fn schedule_self_cleanup(exe_path: &Path, install_dir: &Path) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        let cmd_exe = resolve_windows_system_exe("cmd.exe");
        let exe_str = exe_path.to_string_lossy();
        let dir_str = install_dir.to_string_lossy();
        // Wait briefly for the current process handle to close before removing the stub & empty folder.
        // Use `raw_arg` with `/S /C` because Rust's `arg()` escapes `"` as `\"` for C argv, which breaks `cmd.exe`.
        if !exe_str.contains('"') && !dir_str.contains('"') {
            let raw_cmd = format!(
                "/S /C \"ping 127.0.0.1 -n 3 >NUL & del /F /Q \"{exe_str}\" >NUL 2>&1 & rmdir \"{dir_str}\" >NUL 2>&1\""
            );
            let mut command = std::process::Command::new(cmd_exe);
            if let Some(parent) = install_dir.parent() {
                command.current_dir(parent);
            }
            let _ = command
                .raw_arg(&raw_cmd)
                .creation_flags(CREATE_NO_WINDOW)
                .spawn();
        }
    }
    #[cfg(not(windows))]
    {
        let _ = (exe_path, install_dir);
    }
}

pub fn execute_clean_wipe() {
    remove_installer_preferences();
    if let Ok(local_appdata) = std::env::var("LOCALAPPDATA") {
        let user_data = PathBuf::from(local_appdata).join("Monitor");
        if user_data.exists() {
            let _ = std::fs::remove_dir_all(user_data);
        }
    }
}
