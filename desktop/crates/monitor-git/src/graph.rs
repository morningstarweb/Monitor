use crate::executor::GitExecutor;
use crate::operations::redact_secrets;
use monitor_core::{CommitNode, CoreError, GitGraphPage, GraphEdge, GraphEdgeType, RepoId};
use std::path::Path;

/// Raw commit metadata parsed directly from git log.
#[derive(Debug, Clone)]
pub struct RawCommit {
    pub oid: String,
    pub author_name: String,
    pub author_email: String,
    pub timestamp_utc_ms: i64,
    pub summary: String,
    pub parent_oids: Vec<String>,
}

/// Parses the output of `git log` with delimited format fields.
pub fn parse_git_log_lines(output: &str) -> Vec<RawCommit> {
    let mut commits = Vec::new();

    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }

        let parts: Vec<&str> = trimmed.split('|').collect();
        if parts.len() < 5 {
            continue;
        }

        let oid = parts[0].to_string();
        let author_name = parts[1].to_string();
        let author_email = parts[2].to_string();
        let timestamp_sec: i64 = parts[3].parse().unwrap_or(0);
        let summary = parts[4].to_string();

        let parent_oids = if parts.len() >= 6 && !parts[5].trim().is_empty() {
            parts[5]
                .split_whitespace()
                .map(std::string::ToString::to_string)
                .collect()
        } else {
            Vec::new()
        };

        commits.push(RawCommit {
            oid,
            author_name,
            author_email,
            timestamp_utc_ms: timestamp_sec * 1000,
            summary,
            parent_oids,
        });
    }

    commits
}

const MAX_GRAPH_LANES: usize = 32;

/// Computes the deterministic topological swimlanes and geometry per PRD §5.4.
pub fn compute_dag_geometry(
    repo_id: &RepoId,
    raw_commits: &[RawCommit],
    skip: usize,
    limit: usize,
) -> GitGraphPage {
    let total_available = raw_commits.len();
    let page_commits: Vec<RawCommit> = raw_commits.iter().skip(skip).take(limit).cloned().collect();

    let has_more = total_available > skip + page_commits.len();
    let next_skip = if has_more {
        Some(skip + page_commits.len())
    } else {
        None
    };

    let mut lanes: Vec<Option<String>> = Vec::new();
    let mut commit_nodes = Vec::with_capacity(page_commits.len());

    for (row_idx, commit) in page_commits.iter().enumerate() {
        let mut edges = Vec::new();

        // 1. Locate commit lane: lowest lane index where lanes[L] == Some(commit.oid)
        let raw_commit_lane =
            if let Some(idx) = lanes.iter().position(|l| l.as_deref() == Some(&commit.oid)) {
                idx
            } else {
                // Tip commit not yet tracked: allocate first None slot or push
                if let Some(free_idx) = lanes.iter().position(Option::is_none) {
                    lanes[free_idx] = Some(commit.oid.clone());
                    free_idx
                } else if lanes.len() < MAX_GRAPH_LANES {
                    lanes.push(Some(commit.oid.clone()));
                    lanes.len() - 1
                } else {
                    MAX_GRAPH_LANES - 1
                }
            };
        let commit_lane = raw_commit_lane.min(MAX_GRAPH_LANES - 1);

        let color_index = commit_lane % 12;

        // 2. Collapse duplicate lanes converging onto this commit
        for (l, slot) in lanes.iter_mut().enumerate().skip(commit_lane + 1) {
            if slot.as_deref() == Some(&commit.oid) {
                let from_l = l.min(MAX_GRAPH_LANES - 1);
                edges.push(GraphEdge {
                    from_lane: from_l,
                    to_lane: commit_lane,
                    edge_type: GraphEdgeType::MergeIn,
                    color_index: from_l % 12,
                });
                *slot = None;
            }
        }

        // Keep track of which lanes are modified in this step
        let mut active_before_step: Vec<bool> = lanes.iter().map(Option::is_some).collect();
        if commit_lane < active_before_step.len() {
            active_before_step[commit_lane] = false;
        }

        // 3. Process parents
        if commit.parent_oids.is_empty() {
            // Root commit: free lane
            if commit_lane < lanes.len() {
                lanes[commit_lane] = None;
            }
        } else {
            // Primary parent P0
            let p0 = &commit.parent_oids[0];
            if let Some(p_lane) = lanes.iter().position(|l| l.as_deref() == Some(p0)) {
                let clamped_p = p_lane.min(MAX_GRAPH_LANES - 1);
                if clamped_p != commit_lane {
                    edges.push(GraphEdge {
                        from_lane: commit_lane,
                        to_lane: clamped_p,
                        edge_type: GraphEdgeType::MergeToParent,
                        color_index,
                    });
                    if commit_lane < lanes.len() {
                        lanes[commit_lane] = None;
                    }
                } else {
                    edges.push(GraphEdge {
                        from_lane: commit_lane,
                        to_lane: commit_lane,
                        edge_type: GraphEdgeType::Straight,
                        color_index,
                    });
                }
            } else {
                if commit_lane < lanes.len() {
                    lanes[commit_lane] = Some(p0.clone());
                }
                edges.push(GraphEdge {
                    from_lane: commit_lane,
                    to_lane: commit_lane,
                    edge_type: GraphEdgeType::Straight,
                    color_index,
                });
            }

            // Secondary merge parents P1..Pk
            for merge_parent in commit.parent_oids.iter().skip(1) {
                if let Some(m_lane) = lanes
                    .iter()
                    .position(|l| l.as_deref() == Some(merge_parent))
                {
                    let clamped_m = m_lane.min(MAX_GRAPH_LANES - 1);
                    edges.push(GraphEdge {
                        from_lane: commit_lane,
                        to_lane: clamped_m,
                        edge_type: GraphEdgeType::MergeBranch,
                        color_index: clamped_m % 12,
                    });
                } else {
                    let raw_new_lane =
                        if let Some(free_idx) = lanes.iter().position(Option::is_none) {
                            lanes[free_idx] = Some(merge_parent.clone());
                            free_idx
                        } else if lanes.len() < MAX_GRAPH_LANES {
                            lanes.push(Some(merge_parent.clone()));
                            lanes.len() - 1
                        } else {
                            MAX_GRAPH_LANES - 1
                        };
                    let new_lane = raw_new_lane.min(MAX_GRAPH_LANES - 1);

                    edges.push(GraphEdge {
                        from_lane: commit_lane,
                        to_lane: new_lane,
                        edge_type: GraphEdgeType::BranchOut,
                        color_index: new_lane % 12,
                    });
                }
            }
        }

        // 4. Pass-through for other active lanes
        for (other_lane, &was_active) in active_before_step.iter().enumerate() {
            if other_lane < MAX_GRAPH_LANES && was_active && lanes[other_lane].is_some() {
                edges.push(GraphEdge {
                    from_lane: other_lane,
                    to_lane: other_lane,
                    edge_type: GraphEdgeType::PassThrough,
                    color_index: other_lane % 12,
                });
            }
        }

        let short_oid = if commit.oid.len() >= 7 {
            commit.oid[..7].to_string()
        } else {
            commit.oid.clone()
        };

        commit_nodes.push(CommitNode {
            oid: commit.oid.clone(),
            short_oid,
            author_name: commit.author_name.clone(),
            author_email: commit.author_email.clone(),
            timestamp_utc_ms: commit.timestamp_utc_ms,
            summary: commit.summary.clone(),
            parent_oids: commit.parent_oids.clone(),
            row: row_idx,
            lane: commit_lane,
            color_index,
            edges,
        });
    }

    GitGraphPage {
        repo_id: repo_id.clone(),
        commits: commit_nodes,
        has_more,
        next_skip,
    }
}

/// Queries the Git log and generates the paginated commit graph.
pub async fn query_git_graph(
    executor: &GitExecutor,
    repo_root: &Path,
    repo_id: &RepoId,
    skip: usize,
    limit: usize,
    branch: Option<&str>,
) -> Result<GitGraphPage, CoreError> {
    let mut cmd = executor.create_command(repo_root);
    cmd.args(["log", "--topo-order"]);

    if let Some(target_ref) = branch.filter(|b| !b.trim().is_empty() && *b != "all" && *b != "auto")
    {
        cmd.arg(target_ref);
    } else if branch == Some("all") {
        cmd.args(["--branches", "--remotes", "--tags", "HEAD"]);
    } else {
        // "auto" or default: matches VS Code's Source Control Graph behavior
        // Shows HEAD (active branch) and the base integration branch (upstream/main or origin/main)
        cmd.arg("HEAD");
        for candidate in [
            "upstream/main",
            "origin/main",
            "main",
            "upstream/master",
            "origin/master",
            "master",
        ] {
            let mut check_cmd = executor.create_command(repo_root);
            check_cmd.args(["rev-parse", "--verify", "--quiet", candidate]);
            if let Ok(output) = executor.output(&mut check_cmd).await
                && output.status.success()
            {
                cmd.arg(candidate);
                break;
            }
        }
    }

    cmd.arg("--format=%H|%an|%ae|%at|%s|%P");

    let output = executor
        .output(&mut cmd)
        .await
        .map_err(|e| CoreError::Internal(format!("Failed to execute git log: {e}")))?;

    if !output.status.success() {
        let err = redact_secrets(&String::from_utf8_lossy(&output.stderr));
        return Err(CoreError::Internal(format!("git log failed: {err}")));
    }

    let stdout_str = String::from_utf8_lossy(&output.stdout);
    let raw_commits = parse_git_log_lines(&stdout_str);

    Ok(compute_dag_geometry(repo_id, &raw_commits, skip, limit))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dag_geometry_linear_history() {
        let commits = vec![
            RawCommit {
                oid: "commit_3".to_string(),
                author_name: "Dev".to_string(),
                author_email: "dev@local".to_string(),
                timestamp_utc_ms: 3000,
                summary: "Third".to_string(),
                parent_oids: vec!["commit_2".to_string()],
            },
            RawCommit {
                oid: "commit_2".to_string(),
                author_name: "Dev".to_string(),
                author_email: "dev@local".to_string(),
                timestamp_utc_ms: 2000,
                summary: "Second".to_string(),
                parent_oids: vec!["commit_1".to_string()],
            },
            RawCommit {
                oid: "commit_1".to_string(),
                author_name: "Dev".to_string(),
                author_email: "dev@local".to_string(),
                timestamp_utc_ms: 1000,
                summary: "Root".to_string(),
                parent_oids: vec![],
            },
        ];

        let repo_id = RepoId::new("r_test");
        let page = compute_dag_geometry(&repo_id, &commits, 0, 10);

        assert_eq!(page.commits.len(), 3);
        assert!(!page.has_more);
        assert_eq!(page.next_skip, None);

        // All commits in a linear branch remain on lane 0 with Straight edges
        assert_eq!(page.commits[0].lane, 0);
        assert_eq!(page.commits[1].lane, 0);
        assert_eq!(page.commits[2].lane, 0);

        assert_eq!(page.commits[0].edges[0].edge_type, GraphEdgeType::Straight);
        assert_eq!(page.commits[1].edges[0].edge_type, GraphEdgeType::Straight);
        assert_eq!(page.commits[2].edges.len(), 0); // Root has no parent edges
    }
}
