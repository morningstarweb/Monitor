#![allow(clippy::unwrap_used, clippy::expect_used)]

use super::*;

#[test]
fn test_find_git_binary_resolves_and_not_a_shim() {
    let binary = GitExecutor::find_git_binary().expect("should find git binary");
    assert!(binary.exists(), "resolved binary must exist: {:?}", binary);

    #[cfg(windows)]
    {
        let path_str = binary.to_string_lossy().to_lowercase();
        assert!(
            !path_str.ends_with(r"scoop\shims\git.exe"),
            "resolved git binary must not be a scoop shim: {:?}",
            binary
        );
    }
}

#[cfg(windows)]
#[test]
fn test_is_shim_path_detection() {
    let temp = tempfile::tempdir().unwrap();
    let shims_dir = temp.path().join("shims");
    std::fs::create_dir_all(&shims_dir).unwrap();

    let fake_shim = shims_dir.join("git.exe");
    std::fs::write(&fake_shim, b"fake shim binary").unwrap();

    let fake_shim_meta = shims_dir.join("git.shim");
    std::fs::write(&fake_shim_meta, "path = \"foo\"").unwrap();

    assert!(GitExecutor::is_shim_path(&fake_shim));

    let non_shim_dir = temp.path().join("cmd");
    std::fs::create_dir_all(&non_shim_dir).unwrap();
    let fake_real = non_shim_dir.join("git.exe");
    std::fs::write(&fake_real, b"real binary").unwrap();

    assert!(!GitExecutor::is_shim_path(&fake_real));
}

#[cfg(windows)]
#[test]
fn test_resolve_scoop_shim_via_companion_file() {
    let temp = tempfile::tempdir().unwrap();
    let root = temp.path();

    let shims_dir = root.join("shims");
    let apps_cmd_dir = root.join(r"apps\git\current\cmd");
    std::fs::create_dir_all(&shims_dir).unwrap();
    std::fs::create_dir_all(&apps_cmd_dir).unwrap();

    let fake_shim = shims_dir.join("git.exe");
    let fake_meta = shims_dir.join("git.shim");
    let real_binary = apps_cmd_dir.join("git.exe");

    std::fs::write(&fake_shim, b"shim").unwrap();
    std::fs::write(&real_binary, b"real git").unwrap();

    // Write path to companion .shim file
    let shim_content = format!("path = \"{}\"", real_binary.display());
    std::fs::write(&fake_meta, shim_content).unwrap();

    let resolved = GitExecutor::resolve_scoop_shim(&fake_shim).expect("should resolve via .shim");
    assert_eq!(resolved, real_binary);
}

#[cfg(windows)]
#[test]
fn test_resolve_scoop_shim_via_directory_structure() {
    let temp = tempfile::tempdir().unwrap();
    let root = temp.path();

    let shims_dir = root.join("shims");
    let apps_cmd_dir = root.join(r"apps\git\current\cmd");
    std::fs::create_dir_all(&shims_dir).unwrap();
    std::fs::create_dir_all(&apps_cmd_dir).unwrap();

    let fake_shim = shims_dir.join("git.exe");
    let real_binary = apps_cmd_dir.join("git.exe");

    std::fs::write(&fake_shim, b"shim").unwrap();
    std::fs::write(&real_binary, b"real git").unwrap();

    // Notice: no .shim file written, testing directory-based fallback
    let resolved = GitExecutor::resolve_scoop_shim(&fake_shim)
        .expect("should resolve via directory structure");
    assert_eq!(resolved, real_binary);
}
