pub mod branches;
pub mod checkpoints;
pub mod diff;
pub mod executor;
pub mod graph;
pub mod operations;
pub mod path_guard;
pub mod pull_requests;
pub mod scheduler;
pub mod status;

pub use branches::{
    create_branch, delete_branch, list_branches, merge_branch, sanitize_branch_name, switch_branch,
    validate_branch_name,
};
pub use checkpoints::{CheckpointEngine, CheckpointInfo, MassChangeAlert};
pub use diff::{
    MAX_DIFF_BYTES, MAX_DIFF_LINES, parse_unified_diff, query_commit_files, query_file_diff,
};
pub use executor::GitExecutor;
pub use graph::{compute_dag_geometry, query_git_graph};
pub use operations::{
    commit, commit_and_push, discard_file, fetch, pull, push, redact_secrets, stage_all,
    stage_files, unstage_all, unstage_files,
};
pub use path_guard::{resolve_safe_target_path, validate_repo_relative_path};
pub use pull_requests::{list_pull_requests, merge_pull_request, parse_github_remote};
pub use scheduler::RepoCoordinator;
pub use status::{parse_porcelain_v2_status, query_git_status};
