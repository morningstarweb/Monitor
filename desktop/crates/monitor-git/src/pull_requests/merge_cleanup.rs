/// Restores a clean repository index and working tree after a failed merge, squash, or rebase operation.
fn abort_merge_operation(repo_path: &Path, method: &str) {
    let quick = std::time::Duration::from_secs(10);
    match method {
        "rebase" => {
            let mut cmd = git_command(repo_path);
            cmd.args(["rebase", "--abort"]);
            if let Ok(ref res) = run_bounded_output(cmd, quick)
                && !res.status.success()
            {
                let err = String::from_utf8_lossy(&res.stderr);
                tracing::warn!("git rebase --abort failed ({err}), attempting fallback cleanup");
                let mut q = git_command(repo_path);
                q.args(["rebase", "--quit"]);
                let _ = run_bounded_output(q, quick);
                let mut r = git_command(repo_path);
                r.args(["reset", "--merge"]);
                let _ = run_bounded_output(r, quick);
            }
        }
        "squash" => {
            let mut cmd = git_command(repo_path);
            cmd.args(["reset", "--merge"]);
            if let Ok(ref res) = run_bounded_output(cmd, quick)
                && !res.status.success()
            {
                let err = String::from_utf8_lossy(&res.stderr);
                tracing::warn!("git reset --merge failed ({err}), attempting merge --abort fallback");
                let mut m = git_command(repo_path);
                m.args(["merge", "--abort"]);
                let _ = run_bounded_output(m, quick);
            }
        }
        _ => {
            let mut cmd = git_command(repo_path);
            cmd.args(["merge", "--abort"]);
            if let Ok(ref res) = run_bounded_output(cmd, quick)
                && !res.status.success()
            {
                let err = String::from_utf8_lossy(&res.stderr);
                tracing::warn!(
                    "git merge --abort failed ({err}), attempting reset --merge fallback"
                );
                let mut r = git_command(repo_path);
                r.args(["reset", "--merge"]);
                let _ = run_bounded_output(r, quick);
            }
        }
    }

    // Defense-in-depth: Verify working tree and index cleanliness without destroying uncommitted user work
    let mut status_cmd = git_command(repo_path);
    status_cmd.args(["status", "--porcelain"]);
    if let Ok(status) = run_bounded_output(status_cmd, quick) {
        let status_str = String::from_utf8_lossy(&status.stdout);
        if !status_str.trim().is_empty() {
            tracing::warn!(
                "Working tree remains dirty after aborting {method}: {status_str}. Preserving uncommitted local files; running safe reset --merge."
            );
            let mut r = git_command(repo_path);
            r.args(["reset", "--merge"]);
            let _ = run_bounded_output(r, quick);
        }
    }
}
