use monitor_core::CoreError;
use std::path::PathBuf;

pub(super) fn find_git_binary() -> Result<PathBuf, CoreError> {
    #[cfg(windows)]
    let candidates = windows_git_candidates();

    #[cfg(not(windows))]
    let candidates = vec![
        PathBuf::from("/usr/bin/git"),
        PathBuf::from("/usr/local/bin/git"),
        PathBuf::from("/opt/homebrew/bin/git"),
        PathBuf::from("/Library/Developer/CommandLineTools/usr/bin/git"),
        PathBuf::from("/bin/git"),
        PathBuf::from("/snap/bin/git"),
    ];

    // Check prioritized candidate list of real binaries first
    for path in candidates {
        if path.exists() {
            return Ok(path);
        }
    }

    resolve_git_from_path()
}

#[cfg(windows)]
fn windows_git_candidates() -> Vec<PathBuf> {
    let mut list = Vec::new();

    // 0. Check application-local embedded MinGit (portable bundled git)
    if let Ok(current_exe) = std::env::current_exe()
        && let Some(parent) = current_exe.parent()
    {
        list.push(parent.join(r"tools\git\cmd\git.exe"));
        list.push(parent.join(r"tools\git\bin\git.exe"));
        list.push(parent.join(r"git\cmd\git.exe"));
        list.push(parent.join(r"git\bin\git.exe"));
    }

    // 1. Check user-level installations including Monitor bundled MinGit
    if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
        let base = PathBuf::from(local_app_data);
        list.push(base.join(r"Programs\Monitor\tools\git\cmd\git.exe"));
        list.push(base.join(r"Programs\Monitor\git\cmd\git.exe"));
        list.push(base.join(r"Programs\Git\cmd\git.exe"));
        list.push(base.join(r"Programs\Git\bin\git.exe"));
    }

    // 2. Optional explicit override for non-standard/portable Git installs
    if let Some(custom_git) = std::env::var_os("MONITOR_GIT_PATH") {
        list.push(PathBuf::from(custom_git));
    }

    // 3. Check system installations via Program Files environment variables
    for env_key in &["ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"] {
        if let Ok(val) = std::env::var(env_key) {
            let base = PathBuf::from(val);
            list.push(base.join(r"Git\cmd\git.exe"));
            list.push(base.join(r"Git\bin\git.exe"));
        }
    }
    list.push(PathBuf::from(r"C:\Program Files\Git\cmd\git.exe"));
    list.push(PathBuf::from(r"C:\Program Files\Git\bin\git.exe"));
    list.push(PathBuf::from(r"C:\Program Files (x86)\Git\cmd\git.exe"));
    list.push(PathBuf::from(r"C:\Program Files (x86)\Git\bin\git.exe"));

    let mut user_dirs = Vec::new();
    for var in &["USERPROFILE", "HOME"] {
        if let Ok(val) = std::env::var(var) {
            user_dirs.push(PathBuf::from(val));
        }
    }
    if let (Ok(drive), Ok(path)) = (std::env::var("HOMEDRIVE"), std::env::var("HOMEPATH")) {
        user_dirs.push(PathBuf::from(format!("{drive}{path}")));
    }
    if let Ok(username) = std::env::var("USERNAME") {
        user_dirs.push(PathBuf::from(format!(r"C:\Users\{username}")));
    }

    for user_path in &user_dirs {
        list.push(user_path.join(r"AppData\Local\Programs\Git\cmd\git.exe"));
        list.push(user_path.join(r"AppData\Local\Programs\Git\bin\git.exe"));
        // Check Scoop real binaries in user profile (apps\git\current\...)
        list.push(user_path.join(r"scoop\apps\git\current\cmd\git.exe"));
        list.push(user_path.join(r"scoop\apps\git\current\bin\git.exe"));
        list.push(user_path.join(r"scoop\apps\git\current\mingw64\bin\git.exe"));
    }

    // 4. Check custom or global Scoop directories
    for env_key in &["SCOOP", "SCOOP_GLOBAL"] {
        if let Ok(dir) = std::env::var(env_key) {
            let base = PathBuf::from(dir);
            list.push(base.join(r"apps\git\current\cmd\git.exe"));
            list.push(base.join(r"apps\git\current\bin\git.exe"));
            list.push(base.join(r"apps\git\current\mingw64\bin\git.exe"));
        }
    }
    list.push(PathBuf::from(
        r"C:\ProgramData\scoop\apps\git\current\cmd\git.exe",
    ));
    list.push(PathBuf::from(
        r"C:\ProgramData\scoop\apps\git\current\bin\git.exe",
    ));
    list.push(PathBuf::from(
        r"C:\ProgramData\scoop\apps\git\current\mingw64\bin\git.exe",
    ));

    list
}

fn resolve_git_from_path() -> Result<PathBuf, CoreError> {
    #[cfg(windows)]
    let mut where_cmd = {
        use std::os::windows::process::CommandExt;
        let mut cmd = std::process::Command::new("where.exe");
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
        cmd.arg("git");
        cmd
    };

    #[cfg(not(windows))]
    let mut where_cmd = {
        let mut cmd = std::process::Command::new("which");
        cmd.arg("git");
        cmd
    };

    if let Ok(output) = where_cmd.output()
        && output.status.success()
        && let Ok(text) = String::from_utf8(output.stdout)
    {
        for line in text.lines() {
            let p = PathBuf::from(line.trim());
            if !p.exists() {
                continue;
            }

            #[cfg(windows)]
            {
                if super::GitExecutor::is_shim_path(&p) {
                    if let Some(resolved) = super::GitExecutor::resolve_scoop_shim(&p) {
                        return Ok(resolved);
                    }
                    // Never use an unresolved shim under CREATE_NO_WINDOW; continue searching
                    continue;
                }
            }

            return Ok(p);
        }
    }

    Err(CoreError::Internal(
        "git executable not found on system".to_string(),
    ))
}
