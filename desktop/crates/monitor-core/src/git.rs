use crate::types::{OperationId, RepoId, Revision};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

/// Repository security trust profile.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum TrustProfile {
    Restricted,
    Trusted,
}

/// Brief repository summary for project lists.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct RepoSummary {
    pub repo_id: RepoId,
    pub display_name: String,
    pub path: String,
    pub current_branch: String,
    pub is_clean: bool,
    pub modified_count: usize,
    pub staged_count: usize,
    pub untracked_count: usize,
    pub ahead_count: usize,
    pub behind_count: usize,
    pub last_activity_utc_ms: i64,
    pub watch_enabled: bool,
    pub trust_profile: TrustProfile,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status_error: Option<String>,
}

/// Category of file change in status tree.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum FileChangeCategory {
    Modified,
    Added,
    Deleted,
    Renamed,
    Copied,
    Untracked,
    Conflict,
}

/// Individual file change record.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct GitFileChange {
    pub path: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub original_path: Option<String>,
    pub category: FileChangeCategory,
    pub staged: bool,
    pub insertions: u32,
    pub deletions: u32,
}

/// Complete Git workspace state for a repository.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct GitWorkspaceSnapshot {
    pub repo_id: RepoId,
    pub revision: Revision,
    pub current_branch: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub upstream_branch: Option<String>,
    pub ahead_count: usize,
    pub behind_count: usize,
    pub files: Vec<GitFileChange>,
    pub is_merging: bool,
    pub is_rebasing: bool,
}

/// Summary of an open Pull Request on GitHub.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct PullRequestSummary {
    pub number: u64,
    pub title: String,
    pub author: String,
    pub head_branch: String,
    pub base_branch: String,
    pub updated_at: String,
    pub url: String,
    pub state: String,
    pub is_draft: bool,
}

/// Typed Git operations requested from client.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
#[serde(tag = "type")]
pub enum GitOperation {
    StageFiles {
        paths: Vec<String>,
    },
    StageAll,
    UnstageFiles {
        paths: Vec<String>,
    },
    UnstageAll,
    DiscardFile {
        path: String,
        confirmation_token: String,
    },
    Commit {
        message_summary: String,
        message_body: Option<String>,
        stage_all_tracked: bool,
        #[serde(default)]
        paths: Option<Vec<String>>,
    },
    CommitAndPush {
        message_summary: String,
        message_body: Option<String>,
        stage_all_tracked: bool,
        #[serde(default)]
        paths: Option<Vec<String>>,
    },
    Fetch {
        remote: Option<String>,
    },
    Pull {
        remote: Option<String>,
        branch: Option<String>,
    },
    Push {
        remote: Option<String>,
        branch: Option<String>,
        set_upstream: bool,
    },
    CreateBranch {
        branch_name: String,
        start_point: Option<String>,
    },
    SwitchBranch {
        branch_name: String,
    },
    DeleteBranch {
        branch_name: String,
        force: bool,
    },
    MergeBranch {
        source_branch: String,
    },
    MergeAllBranches {
        #[serde(default)]
        source_branches: Vec<String>,
    },
}

/// Request envelope for executing Git operations.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct GitOperationRequest {
    pub request_id: String,
    pub expected_repo_revision: Revision,
    pub operation: GitOperation,
}

/// Status outcome of a Git operation.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum OperationStatus {
    Succeeded,
    PartialSuccess,
    Failed,
    StaleConflict,
}

/// Result envelope for Git operations.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct GitOperationResult {
    pub request_id: String,
    pub operation_id: OperationId,
    pub status: OperationStatus,
    pub new_repo_revision: Revision,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub commit_oid: Option<String>,
    pub push_completed: bool,
    pub user_message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_code: Option<String>,
    pub started_at_utc_ms: i64,
    pub finished_at_utc_ms: i64,
}

/// Topological graph swimlane edge types.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum GraphEdgeType {
    Straight,
    PassThrough,
    MergeIn,
    MergeToParent,
    MergeBranch,
    BranchOut,
}

/// Pre-calculated graph edge connection.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct GraphEdge {
    pub from_lane: usize,
    pub to_lane: usize,
    pub edge_type: GraphEdgeType,
    pub color_index: usize,
}

/// Pre-calculated commit node coordinate for mobile Canvas rendering.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct CommitNode {
    pub oid: String,
    pub short_oid: String,
    pub author_name: String,
    pub author_email: String,
    pub timestamp_utc_ms: i64,
    pub summary: String,
    pub parent_oids: Vec<String>,
    pub row: usize,
    pub lane: usize,
    pub color_index: usize,
    pub edges: Vec<GraphEdge>,
}

/// Detailed branch metadata.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct BranchInfo {
    pub name: String,
    pub is_current: bool,
    pub is_remote: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub upstream: Option<String>,
    pub ahead_count: usize,
    pub behind_count: usize,
    #[serde(default)]
    pub is_merged: bool,
    #[serde(default)]
    pub unmerged_commits: usize,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub changed_files: Vec<String>,
}

/// Paginated commit graph response.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct GitGraphPage {
    pub repo_id: RepoId,
    pub commits: Vec<CommitNode>,
    pub has_more: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub next_skip: Option<usize>,
}

/// Line type in unified diff hunk.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub enum DiffLineType {
    Context,
    Addition,
    Deletion,
}

/// Individual line inside a unified diff hunk.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct DiffLine {
    pub line_type: DiffLineType,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub old_line_num: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub new_line_num: Option<u32>,
    pub content: String,
}

/// Unified diff hunk with line numbering header.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct DiffHunk {
    pub old_start: u32,
    pub old_lines: u32,
    pub new_start: u32,
    pub new_lines: u32,
    pub header: String,
    pub lines: Vec<DiffLine>,
}

/// Full response for a file diff query.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct FileDiffResponse {
    pub path: String,
    pub is_binary: bool,
    pub is_truncated: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub old_size_bytes: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub new_size_bytes: Option<u64>,
    pub insertions: u32,
    pub deletions: u32,
    pub hunks: Vec<DiffHunk>,
}

/// Request payload for merging a Pull Request.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema, Default)]
pub struct MergePrRequest {
    #[serde(default)]
    pub method: Option<String>,
}

/// Response payload after merging a Pull Request.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, JsonSchema)]
pub struct MergePrResponse {
    pub success: bool,
    pub message: String,
}
