use super::unique_temp_dir;
use crate::archive::{
    MANIFEST_FILE_NAME, TX_JOURNAL_FILE_NAME, extract_payload_bytes, inspect_payload_summary,
    pack_solid_bundle_zip, sanitize_relative_path,
};
use crate::engine::uninstall::execute_uninstallation_at;
use std::path::PathBuf;
use std::sync::atomic::AtomicBool;

#[test]
fn sanitize_relative_path_blocks_traversal_and_absolute_paths() {
    assert!(sanitize_relative_path("").is_err());
    assert!(sanitize_relative_path("../Monitor.exe").is_err());
    assert!(sanitize_relative_path("tools/../../evil.exe").is_err());
    assert!(sanitize_relative_path("/etc/passwd").is_err());
    assert!(sanitize_relative_path(r"\Windows\System32\cmd.exe").is_err());
    assert!(sanitize_relative_path(r"C:\Temp\a.exe").is_err());
    assert!(sanitize_relative_path("bad\0name").is_err());

    let clean = sanitize_relative_path("tools/git/mingw64/bin/git.exe");
    assert!(clean.is_ok());
    if let Ok(path) = clean {
        assert_eq!(
            path,
            PathBuf::from("tools")
                .join("git")
                .join("mingw64")
                .join("bin")
                .join("git.exe")
        );
    }
}

#[test]
fn solid_zstd_pack_extract_upgrade_and_cancel_work() {
    let sandbox = unique_temp_dir("monitor_inst_test");
    let src_v1 = sandbox.join("src_v1");
    let src_v2 = sandbox.join("src_v2");
    let target = sandbox.join("installed");
    let pkg_v1 = sandbox.join("payload_v1.zip");
    let pkg_v2 = sandbox.join("payload_v2.zip");

    let create = |p: &PathBuf, content: &[u8]| -> bool {
        if let Some(parent) = p.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        std::fs::write(p, content).is_ok()
    };

    assert!(create(&src_v1.join("Monitor.exe"), b"binary-v1"));
    assert!(create(&src_v1.join("legacy.dll"), b"old-dll-payload"));
    assert!(create(&src_v1.join("tools/git/cmd/git.exe"), b"git-bin"));

    let stats_v1 = pack_solid_bundle_zip(
        &[
            (src_v1.join("Monitor.exe"), "Monitor.exe".to_string()),
            (src_v1.join("legacy.dll"), "legacy.dll".to_string()),
            (
                src_v1.join("tools/git/cmd/git.exe"),
                "tools/git/cmd/git.exe".to_string(),
            ),
        ],
        &pkg_v1,
        3,
    );
    assert!(stats_v1.is_ok());

    let bytes_v1 = std::fs::read(&pkg_v1).unwrap_or_default();
    let summary = inspect_payload_summary(&bytes_v1);
    assert!(summary.is_ok());
    if let Ok(sum) = summary {
        assert_eq!(sum.file_count, 3);
    }

    let cancel_false = AtomicBool::new(false);
    let res1 = extract_payload_bytes(&bytes_v1, &target, &cancel_false, |_| {});
    assert!(res1.is_ok());
    assert!(target.join("Monitor.exe").is_file());
    assert!(target.join("legacy.dll").is_file());
    assert!(target.join("tools/git/cmd/git.exe").is_file());
    assert!(target.join(MANIFEST_FILE_NAME).is_file());

    assert!(create(&target.join("custom-user-note.txt"), b"keep-me"));

    assert!(create(&src_v2.join("Monitor.exe"), b"binary-v2"));
    assert!(create(&src_v2.join("new.dll"), b"new-dll-payload"));
    let stats_v2 = pack_solid_bundle_zip(
        &[
            (src_v2.join("Monitor.exe"), "Monitor.exe".to_string()),
            (src_v2.join("new.dll"), "new.dll".to_string()),
        ],
        &pkg_v2,
        3,
    );
    assert!(stats_v2.is_ok());
    let bytes_v2 = std::fs::read(&pkg_v2).unwrap_or_default();

    let cancel_true = AtomicBool::new(true);
    let res_cancel = extract_payload_bytes(&bytes_v2, &target, &cancel_true, |_| {});
    assert!(res_cancel.is_err());
    assert_eq!(
        std::fs::read(target.join("Monitor.exe")).unwrap_or_default(),
        b"binary-v1"
    );

    let res2 = extract_payload_bytes(&bytes_v2, &target, &cancel_false, |_| {});
    assert!(res2.is_ok());
    assert_eq!(
        std::fs::read(target.join("Monitor.exe")).unwrap_or_default(),
        b"binary-v2"
    );
    assert!(target.join("new.dll").is_file());
    assert!(!target.join("legacy.dll").exists());
    assert!(target.join("custom-user-note.txt").is_file());

    let _ = std::fs::remove_dir_all(&sandbox);
}

#[test]
fn upgrade_rollback_and_crash_journal_recovery_preserve_existing_files() {
    let sandbox = unique_temp_dir("monitor_tx_test");
    let target = sandbox.join("install");
    let src_v1 = sandbox.join("src_v1");
    let pkg_v1 = sandbox.join("pkg_v1.zip");
    assert!(std::fs::create_dir_all(&src_v1).is_ok());
    assert!(std::fs::write(src_v1.join("Monitor.exe"), b"original-v1").is_ok());
    assert!(std::fs::write(src_v1.join("config.toml"), b"port=9480").is_ok());
    assert!(
        pack_solid_bundle_zip(
            &[
                (src_v1.join("Monitor.exe"), "Monitor.exe".to_string()),
                (src_v1.join("config.toml"), "config.toml".to_string()),
            ],
            &pkg_v1,
            3,
        )
        .is_ok()
    );

    let bytes_v1 = std::fs::read(&pkg_v1).unwrap_or_default();
    let cancel_false = AtomicBool::new(false);
    assert!(extract_payload_bytes(&bytes_v1, &target, &cancel_false, |_| {}).is_ok());
    assert!(!target.join(TX_JOURNAL_FILE_NAME).exists());

    let src_v2 = sandbox.join("src_v2");
    let pkg_v2 = sandbox.join("pkg_v2.zip");
    assert!(std::fs::create_dir_all(&src_v2).is_ok());
    assert!(std::fs::write(src_v2.join("Monitor.exe"), b"broken-v2-bytes").is_ok());
    assert!(std::fs::write(src_v2.join("new_only.dll"), b"new-dll-bytes").is_ok());
    assert!(
        pack_solid_bundle_zip(
            &[
                (src_v2.join("Monitor.exe"), "Monitor.exe".to_string()),
                (src_v2.join("new_only.dll"), "new_only.dll".to_string()),
            ],
            &pkg_v2,
            3,
        )
        .is_ok()
    );
    let mut bad_bytes = std::fs::read(&pkg_v2).unwrap_or_default();
    let truncated_len = bad_bytes.len().saturating_sub(18);
    bad_bytes.truncate(truncated_len);

    let fail_res = extract_payload_bytes(&bad_bytes, &target, &cancel_false, |_| {});
    assert!(fail_res.is_err());
    assert_eq!(
        std::fs::read(target.join("Monitor.exe")).unwrap_or_default(),
        b"original-v1"
    );
    assert!(!target.join("new_only.dll").exists());
    assert!(!target.join(TX_JOURNAL_FILE_NAME).exists());

    assert!(std::fs::write(target.join(".Monitor.exe.upgrade.bak"), b"original-v1").is_ok());
    assert!(std::fs::write(target.join("Monitor.exe"), b"half-written").is_ok());
    assert!(std::fs::write(target.join(".orphan.install.tmp"), b"temp").is_ok());
    assert!(std::fs::write(target.join("stale_added.dll"), b"orphan").is_ok());
    assert!(
        std::fs::write(
            target.join(TX_JOURNAL_FILE_NAME),
            "MONITOR_TX_V1\nB\t.Monitor.exe.upgrade.bak\tMonitor.exe\nC\tstale_added.dll\n",
        )
        .is_ok()
    );

    assert!(extract_payload_bytes(&bytes_v1, &target, &cancel_false, |_| {}).is_ok());
    assert!(!target.join(".orphan.install.tmp").exists());
    assert!(!target.join("stale_added.dll").exists());
    assert_eq!(
        std::fs::read(target.join("Monitor.exe")).unwrap_or_default(),
        b"original-v1"
    );

    let _ = std::fs::remove_dir_all(&sandbox);
}

#[test]
fn manifestless_uninstall_never_deletes_unrelated_user_files() {
    let sandbox = unique_temp_dir("monitor_safe_uninst");
    let target = sandbox.join("shared_folder");
    assert!(std::fs::create_dir_all(target.join("tools/git/bin")).is_ok());
    assert!(std::fs::create_dir_all(target.join("my_projects/secret_repo")).is_ok());
    assert!(std::fs::write(target.join("Monitor.exe"), b"host").is_ok());
    assert!(std::fs::write(target.join("tools/git/bin/git.exe"), b"git").is_ok());
    assert!(std::fs::write(target.join("personal-notes.md"), b"important").is_ok());
    assert!(
        std::fs::write(
            target.join("my_projects/secret_repo/main.rs"),
            b"fn main(){}"
        )
        .is_ok()
    );

    assert!(execute_uninstallation_at(&target).is_ok());
    assert!(!target.join("Monitor.exe").exists());
    assert!(!target.join("tools/git").exists());
    assert!(target.join("personal-notes.md").is_file());
    assert!(target.join("my_projects/secret_repo/main.rs").is_file());

    let _ = std::fs::remove_dir_all(&sandbox);
}
