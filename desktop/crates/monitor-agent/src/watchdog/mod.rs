pub mod detector;
pub mod keep_awake;
pub mod pipeline;
pub mod rules;
pub mod signals;

pub use keep_awake::set_keep_awake;
pub use pipeline::spawn_watchdog_pipeline_task;
