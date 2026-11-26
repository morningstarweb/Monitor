use crate::branches::{
    create_branch, delete_branch, merge_all_branches, merge_branch, switch_branch,
};
use crate::executor::GitExecutor;
use crate::operations::{
    commit, commit_and_push, discard_file, fetch, pull, push, stage_all, stage_files, unstage_all,
    unstage_files,
};
use monitor_core::{
    CoreError, GitOperation, GitOperationRequest, GitOperationResult, OperationId, OperationStatus,
    RepoId, Revision,
};
use std::collections::{HashMap, VecDeque};
use std::path::Path;
use std::sync::Arc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::sync::Mutex;
use tokio::time::sleep;
use tracing::{info, warn};

fn current_time_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[derive(Debug)]
pub struct RepoState {
    pub revision: Revision,
    completed: VecDeque<(GitOperationRequest, GitOperationResult)>,
}

/// Coordinates per-repository serialized operations and optimistic concurrency.
#[derive(Debug, Clone)]
pub struct RepoCoordinator {
    executor: GitExecutor,
    locks: Arc<Mutex<HashMap<RepoId, Arc<Mutex<RepoState>>>>>,
}

impl RepoCoordinator {
    pub fn new(executor: GitExecutor) -> Self {
        Self {
            executor,
            locks: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub async fn get_or_create_repo_lock(
        &self,
        repo_id: &RepoId,
        initial_revision: Revision,
    ) -> Arc<Mutex<RepoState>> {
        let mut map = self.locks.lock().await;
        Arc::clone(map.entry(repo_id.clone()).or_insert_with(|| {
            Arc::new(Mutex::new(RepoState {
                revision: initial_revision,
                completed: VecDeque::new(),
            }))
        }))
    }

    /// Checks if .git/index.lock is present, retrying up to 3 times with 150ms backoff.
    async fn wait_for_index_lock(repo_root: &Path) -> Result<(), CoreError> {
        let lock_file = repo_root.join(".git").join("index.lock");
        for attempt in 1..=3 {
            if !lock_file.exists() {
                return Ok(());
            }
            warn!(
                "Repository is locked by external process (.git/index.lock present). Attempt {}/3",
                attempt
            );
            sleep(Duration::from_millis(150)).await;
        }

        if lock_file.exists() {
            Err(CoreError::Conflict(
                "REPO_LOCKED_BY_EXTERNAL_PROCESS: .git/index.lock held by another process"
                    .to_string(),
            ))
        } else {
            Ok(())
        }
    }

    /// Executes a typed Git operation with optimistic concurrency check.
    pub async fn execute_operation(
        &self,
        repo_id: &RepoId,
        repo_root: &Path,
        request: GitOperationRequest,
    ) -> Result<GitOperationResult, CoreError> {
        let repo_lock = self.get_or_create_repo_lock(repo_id, Revision(1)).await;
        let mut state = repo_lock.lock().await;
        if request.request_id.is_empty()
            || request.request_id.len() > 128
            || request.request_id.chars().any(char::is_control)
        {
            return Err(CoreError::InvalidArgument(
                "Request ID must contain 1 to 128 printable characters".to_string(),
            ));
        }
        if let Some((previous, result)) = state
            .completed
            .iter()
            .find(|(previous, _)| previous.request_id == request.request_id)
        {
            if previous != &request {
                return Err(CoreError::Conflict(
                    "Request ID was already used for a different operation".to_string(),
                ));
            }
            return Ok(result.clone());
        }

        let started_at = current_time_ms();
        let op_id = OperationId::generate();

        // 1. Optimistic concurrency check
        if request.expected_repo_revision != state.revision {
            warn!(
                "Stale repo state for {}: expected rev {}, current rev {}",
                repo_id, request.expected_repo_revision.0, state.revision.0
            );
            return Ok(GitOperationResult {
                request_id: request.request_id,
                operation_id: op_id,
                status: OperationStatus::StaleConflict,
                new_repo_revision: state.revision,
                commit_oid: None,
                push_completed: false,
                user_message: "Repository files changed. Please review updated workspace status."
                    .to_string(),
                error_code: Some("STALE_REPO_STATE".to_string()),
                started_at_utc_ms: started_at,
                finished_at_utc_ms: current_time_ms(),
            });
        }

        // 2. Wait for external index.lock
        Self::wait_for_index_lock(repo_root).await?;

        // 3. Execute the operation
        let mut commit_oid = None;
        let mut push_completed = false;
        let user_message;
        let mut status = OperationStatus::Succeeded;
        let mut error_code = None;

        match &request.operation {
            GitOperation::StageFiles { paths } => {
                stage_files(&self.executor, repo_root, paths).await?;
                user_message = format!("Staged {} file(s)", paths.len());
            }
            GitOperation::StageAll => {
                stage_all(&self.executor, repo_root).await?;
                user_message = "Staged all changes".to_string();
            }
            GitOperation::UnstageFiles { paths } => {
                unstage_files(&self.executor, repo_root, paths).await?;
                user_message = format!("Unstaged {} file(s)", paths.len());
            }
            GitOperation::UnstageAll => {
                unstage_all(&self.executor, repo_root).await?;
                user_message = "Unstaged all changes".to_string();
            }
            GitOperation::DiscardFile {
                path,
                confirmation_token,
            } => {
                discard_file(
                    &self.executor,
                    repo_root,
                    path,
                    confirmation_token,
                    state.revision,
                )
                .await?;
                user_message = format!("Discarded changes in {path}");
            }
            GitOperation::Commit {
                message_summary,
                message_body,
                stage_all_tracked,
                paths,
            } => {
                let stage_all = match paths {
                    Some(p) if !p.is_empty() => {
                        unstage_all(&self.executor, repo_root).await?;
                        stage_files(&self.executor, repo_root, p).await?;
                        false
                    }
                    _ => *stage_all_tracked,
                };
                let oid = commit(
                    &self.executor,
                    repo_root,
                    message_summary,
                    message_body.as_deref(),
                    stage_all,
                )
                .await?;
                let short_oid = if oid.len() >= 7 { &oid[..7] } else { &oid };
                user_message = format!("Committed ({short_oid})");
                commit_oid = Some(oid);
            }
            GitOperation::CommitAndPush {
                message_summary,
                message_body,
                stage_all_tracked,
                paths,
            } => {
                let stage_all = match paths {
                    Some(p) if !p.is_empty() => {
                        unstage_all(&self.executor, repo_root).await?;
                        stage_files(&self.executor, repo_root, p).await?;
                        false
                    }
                    _ => *stage_all_tracked,
                };
                let (oid, pushed, push_err) = commit_and_push(
                    &self.executor,
                    repo_root,
                    message_summary,
                    message_body.as_deref(),
                    stage_all,
                )
                .await?;
                commit_oid = Some(oid.clone());
                push_completed = pushed;

                let short_oid = if oid.len() >= 7 { &oid[..7] } else { &oid };
                if pushed {
                    user_message = format!("Committed ({short_oid}) & pushed");
                } else {
                    status = OperationStatus::PartialSuccess;
                    let _err = push_err.unwrap_or_else(|| "Push failed".to_string());
                    user_message = format!("Committed ({short_oid}), push failed");
                    error_code = Some("PUSH_FAILED".to_string());
                }
            }
            GitOperation::Fetch { remote } => {
                let msg = fetch(&self.executor, repo_root, remote.as_deref()).await?;
                user_message = msg;
            }
            GitOperation::Pull { remote, branch } => {
                let msg = pull(
                    &self.executor,
                    repo_root,
                    remote.as_deref(),
                    branch.as_deref(),
                )
                .await?;
                user_message = msg;
            }
            GitOperation::Push {
                remote,
                branch,
                set_upstream,
            } => {
                let msg = push(
                    &self.executor,
                    repo_root,
                    remote.as_deref(),
                    branch.as_deref(),
                    *set_upstream,
                )
                .await?;
                push_completed = true;
                user_message = msg;
            }
            GitOperation::CreateBranch {
                branch_name,
                start_point,
            } => {
                let clean_name = crate::branches::sanitize_branch_name(branch_name);
                create_branch(
                    &self.executor,
                    repo_root,
                    &clean_name,
                    start_point.as_deref(),
                )
                .await?;
                // Automatically switch to the newly created branch so user immediately operates on it
                let switched = switch_branch(&self.executor, repo_root, &clean_name)
                    .await
                    .is_ok();
                user_message = if switched {
                    format!("Switched to '{clean_name}'")
                } else {
                    format!("Created '{clean_name}'")
                };
            }
            GitOperation::SwitchBranch { branch_name } => {
                switch_branch(&self.executor, repo_root, branch_name).await?;
                user_message = format!("Switched to '{branch_name}'");
            }
            GitOperation::DeleteBranch { branch_name, force } => {
                let trimmed = branch_name.trim();
                if trimmed.eq_ignore_ascii_case("main") || trimmed.eq_ignore_ascii_case("master") {
                    return Err(CoreError::InvalidArgument(format!(
                        "Protected branch: cannot delete '{trimmed}'"
                    )));
                }
                delete_branch(&self.executor, repo_root, branch_name, *force).await?;
                user_message = format!("Deleted '{branch_name}'");
            }
            GitOperation::MergeBranch { source_branch } => {
                let msg = merge_branch(&self.executor, repo_root, source_branch).await?;
                user_message = msg;
            }
            GitOperation::MergeAllBranches { source_branches } => {
                let msg = merge_all_branches(&self.executor, repo_root, source_branches).await?;
                user_message = msg;
            }
        }

        // 4. Monotonic increment of revision on mutating operation
        state.revision = Revision(state.revision.0 + 1);
        info!(
            "Executed Git operation {:?} for {}: new rev {}",
            op_id, repo_id, state.revision.0
        );

        let result = GitOperationResult {
            request_id: request.request_id.clone(),
            operation_id: op_id,
            status,
            new_repo_revision: state.revision,
            commit_oid,
            push_completed,
            user_message,
            error_code,
            started_at_utc_ms: started_at,
            finished_at_utc_ms: current_time_ms(),
        };
        state.completed.push_back((request, result.clone()));
        if state.completed.len() > 128 {
            state.completed.pop_front();
        }
        Ok(result)
    }
}
