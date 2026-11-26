fn git_command(repo_path: &Path) -> Command {
    let git_bin =
        crate::GitExecutor::find_git_binary().unwrap_or_else(|_| std::path::PathBuf::from("git"));
    let mut cmd = Command::new(git_bin);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    // Enforce the same security hardening as GitExecutor (`AUD-017`).
    cmd.env("GIT_TERMINAL_PROMPT", "0")
        .env("GCM_INTERACTIVE", "never")
        .env("GIT_ASKPASS", "")
        .env("SSH_ASKPASS", "")
        .args(["-c", "protocol.file.allow=user"])
        .args(["-c", "protocol.ext.allow=never"])
        .args(["-c", "core.fsmonitor=false"])
        .args(["-c", "gc.auto=0"])
        .current_dir(repo_path);
    cmd
}

fn gh_command(repo_path: &Path) -> Command {
    let mut cmd = Command::new("gh");
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    cmd.env("GH_PROMPT_DISABLED", "1")
        .env("GIT_TERMINAL_PROMPT", "0")
        .current_dir(repo_path);
    cmd
}

/// Runs a subprocess with a strict timeout so stalled remotes or credentials
/// prompts cannot hang Tokio blocking pool threads indefinitely (`AUD-017`).
fn run_bounded_output(
    mut cmd: Command,
    timeout: std::time::Duration,
) -> std::io::Result<std::process::Output> {
    use std::io::Read;
    cmd.stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped());
    let mut child = cmd.spawn()?;
    let mut out_pipe = child.stdout.take();
    let mut err_pipe = child.stderr.take();

    let out_handle = std::thread::spawn(move || {
        let mut buf = Vec::new();
        if let Some(ref mut p) = out_pipe {
            let _ = p.read_to_end(&mut buf);
        }
        buf
    });
    let err_handle = std::thread::spawn(move || {
        let mut buf = Vec::new();
        if let Some(ref mut p) = err_pipe {
            let _ = p.read_to_end(&mut buf);
        }
        buf
    });

    let start = std::time::Instant::now();
    let status = loop {
        match child.try_wait()? {
            Some(status) => break status,
            None => {
                if start.elapsed() >= timeout {
                    let _ = child.kill();
                    let _ = child.wait();
                    let _ = out_handle.join();
                    let _ = err_handle.join();
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::TimedOut,
                        "Subprocess exceeded execution timeout",
                    ));
                }
                std::thread::sleep(std::time::Duration::from_millis(25));
            }
        }
    };

    let stdout = out_handle.join().unwrap_or_default();
    let stderr = err_handle.join().unwrap_or_default();
    Ok(std::process::Output {
        status,
        stdout,
        stderr,
    })
}

/// Parses GitHub owner and repository name from the git origin URL.
pub fn parse_github_remote(repo_path: &Path) -> Option<(String, String)> {
    let output = run_bounded_output(
        {
            let mut c = git_command(repo_path);
            c.args(["config", "--get", "remote.origin.url"]);
            c
        },
        std::time::Duration::from_secs(5),
    )
    .ok()?;

    if !output.status.success() {
        return None;
    }

    let url_str = String::from_utf8_lossy(&output.stdout).trim().to_string();
    parse_github_url(&url_str)
}

fn parse_github_url(url: &str) -> Option<(String, String)> {
    let trimmed = url.trim().trim_end_matches(".git");

    for sep in ["github.com/", "github.com:"] {
        if let Some(pos) = trimmed.find(sep) {
            let rest = &trimmed[pos + sep.len()..];
            let parts: Vec<&str> = rest.split('/').collect();
            if parts.len() >= 2 && !parts[0].is_empty() && !parts[1].is_empty() {
                return Some((parts[0].to_string(), parts[1].to_string()));
            }
        }
    }

    None
}
