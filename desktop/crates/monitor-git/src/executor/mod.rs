mod resolve;
#[cfg(windows)]
mod shim;

#[cfg(test)]
mod tests;

use monitor_core::CoreError;
use std::path::{Path, PathBuf};
use std::process::{Output, Stdio};
use std::time::Duration;
use tokio::process::Command;
use tracing::info;

#[derive(Debug, Clone)]
pub struct GitExecutor {
    git_binary: PathBuf,
}

impl GitExecutor {
    pub fn find_git_binary() -> Result<PathBuf, CoreError> {
        resolve::find_git_binary()
    }

    pub fn new() -> Result<Self, CoreError> {
        let git_binary = Self::find_git_binary().unwrap_or_else(|e| {
            tracing::warn!("Git binary not found during initialization: {e}. Defaulting to 'git'");
            PathBuf::from("git")
        });
        info!("Initialized GitExecutor using binary: {:?}", git_binary);
        Ok(Self { git_binary })
    }

    pub fn create_command(&self, repo_dir: &Path) -> Command {
        let mut cmd = Command::new(&self.git_binary);
        #[cfg(windows)]
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
        cmd.current_dir(repo_dir)
            .kill_on_drop(true)
            .stdin(Stdio::null())
            .env("GIT_TERMINAL_PROMPT", "0")
            .env("GCM_INTERACTIVE", "never")
            .env("GIT_EDITOR", "true")
            .env("GIT_SEQUENCE_EDITOR", "true")
            .env("GIT_MERGE_AUTOEDIT", "no")
            .env_remove("GIT_EXTERNAL_DIFF")
            .args(["--no-pager", "--literal-pathspecs"])
            .args(["-c", "core.hooksPath=/dev/null"])
            .args(["-c", "protocol.allow=never"])
            .args(["-c", "protocol.http.allow=always"])
            .args(["-c", "protocol.https.allow=always"])
            .args(["-c", "protocol.ssh.allow=always"])
            .args(["-c", "protocol.git.allow=always"])
            .args(["-c", "protocol.file.allow=user"])
            .args(["-c", "protocol.ext.allow=never"])
            .args(["-c", "core.fsmonitor=false"])
            .args(["-c", "gc.auto=0", "-c", "maintenance.auto=false"])
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        cmd
    }

    /// Dropping a timed-out output future kills the child instead of leaving Git running.
    pub async fn output(&self, command: &mut Command) -> std::io::Result<Output> {
        tokio::time::timeout(Duration::from_secs(120), command.output())
            .await
            .map_err(|_| {
                std::io::Error::new(
                    std::io::ErrorKind::TimedOut,
                    "Git exceeded 120 seconds; review repository state before retrying",
                )
            })?
    }
}
