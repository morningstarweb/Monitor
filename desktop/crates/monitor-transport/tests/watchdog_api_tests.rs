#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

#[path = "watchdog_api_tests/helpers.rs"]
mod helpers;
#[path = "watchdog_api_tests/lifecycle_tests.rs"]
mod lifecycle_tests;
#[path = "watchdog_api_tests/rbac_tests.rs"]
mod rbac_tests;
#[path = "watchdog_api_tests/rollback_tests.rs"]
mod rollback_tests;
