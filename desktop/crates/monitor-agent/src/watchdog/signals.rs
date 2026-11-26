use std::collections::HashSet;
use std::path::Path;
use sysinfo::{Pid, System};

#[derive(Debug, Clone)]
pub struct ProcessTreeSample {
    pub total_cpu_usage: f32,
    pub disk_read_bytes: u64,
    pub disk_written_bytes: u64,
    pub child_pids: HashSet<u32>,
    pub git_index_mtime_ms: Option<i64>,
}

#[derive(Debug, Clone)]
pub struct SessionActivityTracker {
    pub session_id: String,
    pub profile_name: String,
    pub root_pid: u32,
    pub repo_path: Option<String>,
    pub stall_threshold_s: u32,
    pub last_sample: Option<ProcessTreeSample>,
    pub last_activity_ms: i64,
    pub started_at_ms: i64,
    pub current_state: String, // "Active", "Quiet", "Stalled", "Ended"
    pub exit_code: Option<i32>,
}

impl SessionActivityTracker {
    pub fn new(
        session_id: String,
        profile_name: String,
        root_pid: u32,
        repo_path: Option<String>,
        stall_threshold_s: u32,
        started_at_ms: i64,
    ) -> Self {
        Self {
            session_id,
            profile_name,
            root_pid,
            repo_path,
            stall_threshold_s,
            last_sample: None,
            last_activity_ms: started_at_ms,
            started_at_ms,
            current_state: "Active".to_string(),
            exit_code: None,
        }
    }

    pub fn sample_and_update(
        &mut self,
        sys: &System,
        descendants: &[u32],
        now_ms: i64,
    ) -> (String, bool) {
        // Check if root process is still alive (if not a synthesized workspace session where root_pid == 0)
        if self.root_pid != 0 {
            let root_alive = sys.process(Pid::from_u32(self.root_pid)).is_some();
            if !root_alive {
                let was_ended = self.current_state == "Ended";
                self.current_state = "Ended".to_string();
                return (self.current_state.clone(), !was_ended);
            }
        }

        // Collect tree metrics
        let mut total_cpu = 0.0f32;
        let mut disk_read = 0u64;
        let mut disk_write = 0u64;
        let mut children = HashSet::new();

        let all_pids = std::iter::once(&self.root_pid).chain(descendants.iter());
        for &pid in all_pids {
            if let Some(proc_info) = sys.process(Pid::from_u32(pid)) {
                total_cpu += proc_info.cpu_usage();
                let disk_usage = proc_info.disk_usage();
                disk_read += disk_usage.read_bytes;
                disk_write += disk_usage.written_bytes;
                if pid != self.root_pid {
                    children.insert(pid);
                }
            }
        }

        let git_mtime = self.sample_git_index_mtime();

        let current_sample = ProcessTreeSample {
            total_cpu_usage: total_cpu,
            disk_read_bytes: disk_read,
            disk_written_bytes: disk_write,
            child_pids: children,
            git_index_mtime_ms: git_mtime,
        };

        let mut had_activity = false;
        if let Some(prev) = &self.last_sample {
            let is_cpu_active = current_sample.total_cpu_usage > 0.05;
            let cpu_delta = (current_sample.total_cpu_usage - prev.total_cpu_usage).abs();
            let disk_delta = (current_sample.disk_read_bytes > prev.disk_read_bytes)
                || (current_sample.disk_written_bytes > prev.disk_written_bytes);
            let has_active_children = !current_sample.child_pids.is_empty();
            let has_new_child = current_sample
                .child_pids
                .difference(&prev.child_pids)
                .count()
                > 0;
            let git_delta = current_sample.git_index_mtime_ms != prev.git_index_mtime_ms;

            if is_cpu_active
                || cpu_delta > 0.1
                || disk_delta
                || has_active_children
                || has_new_child
                || git_delta
            {
                had_activity = true;
            }
        } else {
            // First sample marks initial activity
            had_activity = true;
        }

        self.last_sample = Some(current_sample);

        if had_activity {
            self.last_activity_ms = now_ms;
        }

        let quiet_threshold_ms = 90_000;
        let stall_threshold_ms = self.stall_threshold_s as i64 * 1000;
        let elapsed_since_activity = now_ms - self.last_activity_ms;

        let prev_state = self.current_state.clone();
        if elapsed_since_activity >= stall_threshold_ms {
            self.current_state = "Stalled".to_string();
        } else if elapsed_since_activity >= quiet_threshold_ms {
            self.current_state = "Quiet".to_string();
        } else {
            self.current_state = "Active".to_string();
        }

        let state_changed = prev_state != self.current_state;
        (self.current_state.clone(), state_changed)
    }

    fn sample_git_index_mtime(&self) -> Option<i64> {
        let repo = self.repo_path.as_ref()?;
        let index_path = Path::new(repo).join(".git").join("index");
        if let Ok(meta) = std::fs::metadata(&index_path)
            && let Ok(modified) = meta.modified()
            && let Ok(duration) = modified.duration_since(std::time::UNIX_EPOCH)
        {
            return Some(duration.as_millis() as i64);
        }
        None
    }
}
