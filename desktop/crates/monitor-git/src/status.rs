use crate::executor::GitExecutor;
use monitor_core::{
    CoreError, FileChangeCategory, GitFileChange, GitWorkspaceSnapshot, RepoId, Revision,
};
use std::collections::HashMap;
use std::path::Path;

/// Parsed branch metadata from porcelain v2 headers.
#[derive(Debug, Default)]
pub struct BranchMetadata {
    pub head: String,
    pub upstream: Option<String>,
    pub ahead: usize,
    pub behind: usize,
}

/// Parses porcelain v2 `-z` status output.
pub fn parse_porcelain_v2_status(
    repo_id: &RepoId,
    revision: Revision,
    raw_bytes: &[u8],
    numstat_map: &HashMap<String, (u32, u32)>,
) -> Result<GitWorkspaceSnapshot, CoreError> {
    let mut branch = BranchMetadata::default();
    let mut files: Vec<GitFileChange> = Vec::new();

    let mut is_merging = false;
    let is_rebasing = false;

    // Tokens separated by NUL byte (0x00)
    let chunks: Vec<&[u8]> = raw_bytes.split(|&b| b == 0).collect();
    let mut i = 0;

    while i < chunks.len() {
        let chunk = chunks[i];
        if chunk.is_empty() {
            i += 1;
            continue;
        }

        let line = String::from_utf8_lossy(chunk);

        if let Some(rest) = line.strip_prefix("# branch.head ") {
            branch.head = rest.trim().to_string();
            i += 1;
            continue;
        }

        if let Some(rest) = line.strip_prefix("# branch.upstream ") {
            branch.upstream = Some(rest.trim().to_string());
            i += 1;
            continue;
        }

        if let Some(rest) = line.strip_prefix("# branch.ab ") {
            // e.g. "+2 -1"
            let parts: Vec<&str> = rest.split_whitespace().collect();
            for part in parts {
                if let Some(ahead_str) = part.strip_prefix('+') {
                    branch.ahead = ahead_str.parse().unwrap_or(0);
                } else if let Some(behind_str) = part.strip_prefix('-') {
                    branch.behind = behind_str.parse().unwrap_or(0);
                }
            }
            i += 1;
            continue;
        }

        if line.starts_with("# ") {
            i += 1;
            continue;
        }

        // Type 1: Ordinary changed entry
        // 1 <XY> <sub> <mH> <mI> <mW> <hH> <hI> <path>
        if let Some(rest) = line.strip_prefix("1 ") {
            let parts: Vec<&str> = rest.splitn(8, ' ').collect();
            if parts.len() == 8 {
                let xy = parts[0];
                let path = parts[7].to_string();
                let (ins, del) = numstat_map.get(&path).copied().unwrap_or((0, 0));

                let staged_char = xy.chars().next().unwrap_or('.');
                let unstaged_char = xy.chars().nth(1).unwrap_or('.');

                // Staged change entry
                if staged_char != '.' {
                    let cat = match staged_char {
                        'A' => FileChangeCategory::Added,
                        'D' => FileChangeCategory::Deleted,
                        'R' => FileChangeCategory::Renamed,
                        'C' => FileChangeCategory::Copied,
                        _ => FileChangeCategory::Modified,
                    };
                    files.push(GitFileChange {
                        path: path.clone(),
                        original_path: None,
                        category: cat,
                        staged: true,
                        insertions: ins,
                        deletions: del,
                    });
                }

                // Unstaged change entry
                if unstaged_char != '.' {
                    let cat = match unstaged_char {
                        'D' => FileChangeCategory::Deleted,
                        _ => FileChangeCategory::Modified,
                    };
                    files.push(GitFileChange {
                        path: path.clone(),
                        original_path: None,
                        category: cat,
                        staged: false,
                        insertions: ins,
                        deletions: del,
                    });
                }
            }
            i += 1;
            continue;
        }

        // Type 2: Renamed or copied entry
        // 2 <XY> <sub> <mH> <mI> <mW> <hH> <hI> <X><score> <path>\0<origPath>
        if let Some(rest) = line.strip_prefix("2 ") {
            let parts: Vec<&str> = rest.splitn(9, ' ').collect();
            if parts.len() == 9 {
                let path = parts[8].to_string();
                let orig_path = if i + 1 < chunks.len() {
                    String::from_utf8_lossy(chunks[i + 1]).to_string()
                } else {
                    String::new()
                };
                let (ins, del) = numstat_map.get(&path).copied().unwrap_or((0, 0));

                files.push(GitFileChange {
                    path,
                    original_path: Some(orig_path),
                    category: FileChangeCategory::Renamed,
                    staged: true,
                    insertions: ins,
                    deletions: del,
                });
                i += 1; // Skip the origPath chunk
            }
            i += 1;
            continue;
        }

        // Type u: Unmerged entry (Conflict)
        // u <XY> <sub> <m1> <m2> <m3> <mW> <h1> <h2> <h3> <path>
        if let Some(rest) = line.strip_prefix("u ") {
            let parts: Vec<&str> = rest.splitn(10, ' ').collect();
            if parts.len() == 10 {
                let path = parts[9].to_string();
                is_merging = true;
                files.push(GitFileChange {
                    path,
                    original_path: None,
                    category: FileChangeCategory::Conflict,
                    staged: false,
                    insertions: 0,
                    deletions: 0,
                });
            }
            i += 1;
            continue;
        }

        // Type ?: Untracked file
        // ? <path>
        if let Some(path) = line.strip_prefix("? ") {
            files.push(GitFileChange {
                path: path.to_string(),
                original_path: None,
                category: FileChangeCategory::Untracked,
                staged: false,
                insertions: 0,
                deletions: 0,
            });
            i += 1;
            continue;
        }

        i += 1;
    }

    if repo_id.as_str().is_empty() {
        return Err(CoreError::InvalidArgument("Empty repo_id".to_string()));
    }

    Ok(GitWorkspaceSnapshot {
        repo_id: repo_id.clone(),
        revision,
        current_branch: if branch.head.is_empty() {
            "HEAD (detached)".to_string()
        } else {
            branch.head
        },
        upstream_branch: branch.upstream,
        ahead_count: branch.ahead,
        behind_count: branch.behind,
        files,
        is_merging,
        is_rebasing,
    })
}

/// Executes a full status scan using `git.exe` porcelain-v2.
pub async fn query_git_status(
    executor: &GitExecutor,
    repo_root: &Path,
    repo_id: &RepoId,
    revision: Revision,
) -> Result<GitWorkspaceSnapshot, CoreError> {
    let mut cmd = executor.create_command(repo_root);
    cmd.args([
        "status",
        "--porcelain=v2",
        "--branch",
        "--untracked-files=all",
        "-z",
    ]);

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git status: {e}")))?;

    if !output.status.success() {
        let err_text = String::from_utf8_lossy(&output.stderr);
        return Err(CoreError::Internal(format!(
            "git status failed: {err_text}"
        )));
    }

    let mut snapshot =
        parse_porcelain_v2_status(repo_id, revision, &output.stdout, &HashMap::new())?;
    for staged in [false, true] {
        let mut diff = executor.create_command(repo_root);
        diff.args(["diff", "--no-ext-diff", "--no-textconv", "--numstat", "-z"]);
        if staged {
            diff.arg("--cached");
        }
        if let Ok(output) = executor.output(&mut diff).await
            && output.status.success()
        {
            let counts = parse_numstat(&output.stdout);
            for file in snapshot
                .files
                .iter_mut()
                .filter(|file| file.staged == staged)
            {
                if let Some((insertions, deletions)) = counts.get(&file.path) {
                    file.insertions = *insertions;
                    file.deletions = *deletions;
                }
            }
        }
    }
    Ok(snapshot)
}

fn parse_numstat(raw: &[u8]) -> HashMap<String, (u32, u32)> {
    let mut result = HashMap::new();
    let mut records = raw.split(|byte| *byte == 0);
    while let Some(record) = records.next() {
        let text = String::from_utf8_lossy(record);
        let mut fields = text.splitn(3, '\t');
        let (Some(insertions), Some(deletions), Some(path)) =
            (fields.next(), fields.next(), fields.next())
        else {
            continue;
        };
        let path = if path.is_empty() {
            let _old = records.next();
            match records.next() {
                Some(new) => String::from_utf8_lossy(new).into_owned(),
                None => continue,
            }
        } else {
            path.to_string()
        };
        result.insert(
            path,
            (
                insertions.parse().unwrap_or(0),
                deletions.parse().unwrap_or(0),
            ),
        );
    }
    result
}

#[cfg(test)]
mod tests {
    #![allow(clippy::unwrap_used)]
    use super::*;

    #[test]
    fn numstat_uses_tabs_inside_nul_records_and_handles_renames() {
        let counts = parse_numstat(b"3\t2\tfile with spaces.rs\0-\t-\timage.png\0".as_slice());
        assert_eq!(counts.get("file with spaces.rs"), Some(&(3, 2)));
        assert_eq!(counts.get("image.png"), Some(&(0, 0)));
        let renamed = parse_numstat(b"5\t1\t\0old.rs\0new.rs\0");
        assert_eq!(renamed.get("new.rs"), Some(&(5, 1)));
    }

    #[test]
    fn test_parse_porcelain_v2() {
        let raw = b"# branch.oid a1b2c3d4\0# branch.head feat/test\0# branch.upstream origin/feat/test\0# branch.ab +3 -1\x001 M. N... 100644 100644 100644 a1b2 c3d4 src/main.rs\0? tests/new_file.rs\0";
        let numstats = HashMap::from([("src/main.rs".to_string(), (15, 3))]);

        let repo_id = RepoId::new("r_test");
        let snapshot = parse_porcelain_v2_status(&repo_id, Revision(1), raw, &numstats).unwrap();

        assert_eq!(snapshot.current_branch, "feat/test");
        assert_eq!(
            snapshot.upstream_branch,
            Some("origin/feat/test".to_string())
        );
        assert_eq!(snapshot.ahead_count, 3);
        assert_eq!(snapshot.behind_count, 1);
        assert_eq!(snapshot.files.len(), 2);

        let file1 = &snapshot.files[0];
        assert_eq!(file1.path, "src/main.rs");
        assert!(file1.staged);
        assert_eq!(file1.category, FileChangeCategory::Modified);
        assert_eq!(file1.insertions, 15);
        assert_eq!(file1.deletions, 3);

        let file2 = &snapshot.files[1];
        assert_eq!(file2.path, "tests/new_file.rs");
        assert!(!file2.staged);
        assert_eq!(file2.category, FileChangeCategory::Untracked);
    }
}
