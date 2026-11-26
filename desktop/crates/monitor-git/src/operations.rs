use crate::branches::validate_branch_name;
use crate::executor::GitExecutor;
use crate::path_guard::{resolve_safe_target_path, validate_repo_relative_path};
use monitor_core::{CoreError, Revision};
use std::path::Path;
use std::process::Stdio;
use tokio::io::AsyncWriteExt;

include!("operations/staging.rs");
include!("operations/discard.rs");
include!("operations/commits.rs");
include!("operations/remotes.rs");

#[cfg(test)]
include!("operations/tests/network_arguments.rs");
#[cfg(test)]
include!("operations/tests/redaction.rs");
