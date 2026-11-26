use monitor_core::{CoreError, PullRequestSummary};
use serde::Deserialize;
use std::path::Path;
use std::process::Command;
include!("pull_requests/remote.rs");
include!("pull_requests/models.rs");
include!("pull_requests/listing.rs");
include!("pull_requests/merge.rs");
include!("pull_requests/merge_cleanup.rs");

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used)]
mod tests {
    use super::*;
    include!("pull_requests/tests/remote_parsing.rs");
    include!("pull_requests/tests/argument_validation.rs");
    include!("pull_requests/tests/rebase_conflict_cleanup.rs");
    include!("pull_requests/tests/squash_conflict_cleanup.rs");
}
