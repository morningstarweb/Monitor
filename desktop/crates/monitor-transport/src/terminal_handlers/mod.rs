#[cfg(windows)]
#[allow(unsafe_code)]
mod job_object;
mod registry;
mod routes;
mod run_project;
mod stream;
#[cfg(test)]
mod tests;

pub use registry::abort_all_terminal_tasks;
pub(crate) use routes::stop_terminal_session_impl;
pub use routes::{
    execute_terminal_in_dir, get_terminal_setting_handler, set_terminal_setting_handler,
    stop_project_terminal_handler,
};
pub use run_project::run_project_terminal_handler;

/// Hard byte budget for collected terminal output (AUD-06).
pub const MAX_OUTPUT_BYTES: usize = 128 * 1024;
/// A single line longer than this is truncated at the source.
pub const MAX_LINE_BYTES: usize = 16 * 1024;
pub(super) const OUTPUT_CHANNEL_CAPACITY: usize = 256;
pub(super) const COMMAND_TIMEOUT_SECS: u64 = 60;
pub(super) const INITIAL_LOG_WAIT_SECS: u64 = 2;
pub(super) const MAX_TERMINAL_SESSIONS: usize = 32;
