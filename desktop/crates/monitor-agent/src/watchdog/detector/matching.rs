use std::path::{Path, PathBuf};

#[cfg(test)]
pub(super) fn matches_profile(
    name: &str,
    cmd_parts: &[std::ffi::OsString],
    matchers: &[String],
) -> bool {
    matches_profile_with_exe(name, None, cmd_parts, matchers)
}

pub(super) fn matches_profile_with_exe(
    name: &str,
    exe_opt: Option<&Path>,
    cmd_parts: &[std::ffi::OsString],
    matchers: &[String],
) -> bool {
    let lower_name = name.to_lowercase();
    let lower_exe = exe_opt
        .map(|p| p.to_string_lossy().to_lowercase())
        .unwrap_or_default();
    let lower_cmd = cmd_parts
        .iter()
        .map(|s| s.to_string_lossy().to_lowercase())
        .collect::<Vec<_>>()
        .join(" ");

    for matcher in matchers {
        let m = matcher.to_lowercase();
        if lower_name == m
            || lower_name == format!("{m}.exe")
            || lower_cmd.contains(&format!(" {m} "))
            || lower_cmd.contains(&format!(" {m}.exe "))
            || lower_cmd.ends_with(&format!(" {m}"))
            || lower_cmd.ends_with(&format!(" {m}.exe"))
            || lower_cmd.starts_with(&format!("{m} "))
            || lower_cmd.starts_with(&format!("{m}.exe "))
            || lower_cmd.contains(&format!("\\{m}.exe"))
            || lower_cmd.contains(&format!("/{m}.exe"))
            || lower_cmd.contains(&format!(" {m}/"))
            || lower_cmd.contains(&format!("\\{m} "))
            || lower_cmd.contains(&format!("/{m} "))
            || (m.contains('.')
                && (lower_exe.contains(&format!("\\{m}")) || lower_exe.contains(&format!("/{m}"))))
        {
            return true;
        }
    }
    false
}

pub(super) fn find_repo_path(
    cwd_opt: Option<&Path>,
    cmd_parts: &[std::ffi::OsString],
    known_repos: &[String],
) -> Option<String> {
    // 1. Inspect cwd upwards for .git
    if let Some(cwd) = cwd_opt {
        let mut current = PathBuf::from(cwd);
        loop {
            if current.join(".git").exists() {
                let raw = current.to_string_lossy().to_string();
                return Some(raw.strip_prefix(r"\\?\").unwrap_or(&raw).to_string());
            }
            if !current.pop() {
                break;
            }
        }
    }

    // 2. Check if any known repo appears in command line arguments (slash/URI/workspace_id variants)
    let cmd_str = cmd_parts
        .iter()
        .map(|s| s.to_string_lossy().to_lowercase())
        .collect::<Vec<_>>()
        .join(" ");

    for repo in known_repos {
        let stripped = repo.strip_prefix(r"\\?\").unwrap_or(repo);
        let r_back = stripped.to_lowercase();
        let r_fwd = r_back.replace('\\', "/");
        let r_uri = r_fwd.replace(':', "%3a");
        let r_ws_id = format!("file_{}", r_fwd.replace(':', "").replace('/', "_"));
        if cmd_str.contains(&r_back)
            || cmd_str.contains(&r_fwd)
            || cmd_str.contains(&r_uri)
            || cmd_str.contains(&r_ws_id)
        {
            return Some(stripped.to_string());
        }
    }

    None
}
