mod parse;
mod query;

#[cfg(test)]
mod tests;

pub use parse::{MAX_DIFF_BYTES, MAX_DIFF_LINES, parse_unified_diff};
pub use query::{query_commit_files, query_file_diff};
