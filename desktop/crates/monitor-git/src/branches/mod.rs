mod list;
mod merge;
mod ops;
mod validation;

#[cfg(test)]
mod tests;

pub use list::list_branches;
pub use merge::{merge_all_branches, merge_branch};
pub use ops::{create_branch, delete_branch, switch_branch};
pub use validation::{sanitize_branch_name, validate_branch_name};
