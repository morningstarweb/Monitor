use crate::power::read_power_status;
use monitor_core::{CpuSnapshot, DiskSnapshot, MemorySnapshot, ProcessSnapshot, SystemSnapshot};
use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use sysinfo::{CpuRefreshKind, Disks, MemoryRefreshKind, ProcessesToUpdate, System};

#[derive(Debug, Clone, Copy, PartialEq, Eq, thiserror::Error)]
pub enum ProcessKillError {
    #[error("Protected system process cannot be terminated")]
    ProtectedProcess,
    #[error("Process not found")]
    NotFound,
    #[error("Access denied or insufficient OS privileges")]
    AccessDenied,
}

#[derive(Debug, Clone)]
pub struct TelemetrySampler {
    state: Arc<Mutex<SamplerState>>,
}

#[derive(Debug)]
struct SamplerState {
    sys: System,
    disks: Disks,
    sampled_at: Option<Instant>,
    cached: Option<SystemSnapshot>,
    history: VecDeque<(i64, f32, f32)>,
}

impl TelemetrySampler {
    pub fn new() -> Self {
        let mut sys = System::new_all();
        sys.refresh_all();
        let disks = Disks::new_with_refreshed_list();
        Self {
            state: Arc::new(Mutex::new(SamplerState {
                sys,
                disks,
                sampled_at: None,
                cached: None,
                history: VecDeque::new(),
            })),
        }
    }

    pub fn sample_system_snapshot(&self) -> SystemSnapshot {
        let mut state = match self.state.lock() {
            Ok(s) => s,
            Err(p) => p.into_inner(),
        };
        if state
            .sampled_at
            .is_some_and(|at| at.elapsed() < Duration::from_secs(2))
            && let Some(snapshot) = &state.cached
        {
            return snapshot.clone();
        }
        let sys = &mut state.sys;

        sys.refresh_cpu_specifics(CpuRefreshKind::everything());
        sys.refresh_memory_specifics(MemoryRefreshKind::everything());
        sys.refresh_processes(ProcessesToUpdate::All, true);

        let total_cpu = sys.global_cpu_usage();
        let per_core: Vec<f32> = sys.cpus().iter().map(sysinfo::Cpu::cpu_usage).collect();
        let frequency_mhz = sys.cpus().first().map_or(0, |c| c.frequency() as u32);

        let now_ms = chrono::Utc::now().timestamp_millis();

        // Top 5 processes by CPU
        let mut processes: Vec<ProcessSnapshot> = sys
            .processes()
            .iter()
            .map(|(pid, proc_info)| ProcessSnapshot {
                pid: pid.as_u32(),
                name: proc_info.name().to_string_lossy().to_string(),
                cpu_percent: (proc_info.cpu_usage() / sys.cpus().len().max(1) as f32)
                    .clamp(0.0, 100.0),
                memory_bytes: proc_info.memory(),
                observed_at_utc_ms: now_ms,
            })
            .collect();

        processes.sort_by(|a, b| {
            b.cpu_percent
                .partial_cmp(&a.cpu_percent)
                .unwrap_or(std::cmp::Ordering::Equal)
        });
        let top_processes = processes.into_iter().take(5).collect();

        let mut cpu = CpuSnapshot {
            total_usage_percent: total_cpu,
            per_core_usage_percent: per_core,
            frequency_mhz,
            history_60m_percent: Vec::new(),
            top_processes,
        };

        let total_ram = sys.total_memory();
        let used_ram = sys.used_memory();
        let avail_ram = sys.available_memory();
        let ram_percent = if total_ram > 0 {
            (used_ram as f32 / total_ram as f32) * 100.0
        } else {
            0.0
        };

        let mut ram = MemorySnapshot {
            total_bytes: total_ram,
            used_bytes: used_ram,
            available_bytes: avail_ram,
            usage_percent: ram_percent,
            swap_total_bytes: sys.total_swap(),
            swap_used_bytes: sys.used_swap(),
            history_60m_percent: Vec::new(),
        };

        let battery = read_power_status();
        // Keep only samples actually observed during this process lifetime.
        // Minute buckets bound payload size; the current bucket follows live data.
        let minute = now_ms / 60_000;
        if let Some(last) = state.history.back_mut()
            && last.0 == minute
        {
            *last = (minute, total_cpu, ram_percent);
        } else {
            state.history.push_back((minute, total_cpu, ram_percent));
        }
        while state
            .history
            .front()
            .is_some_and(|(at, _, _)| *at < minute - 59)
        {
            state.history.pop_front();
        }
        cpu.history_60m_percent = state.history.iter().map(|(_, cpu, _)| *cpu).collect();
        ram.history_60m_percent = state.history.iter().map(|(_, _, ram)| *ram).collect();

        // Refresh physical disks
        state.disks.refresh(true);
        let mut disks: Vec<DiskSnapshot> = state
            .disks
            .list()
            .iter()
            .map(|disk| {
                let raw_name = disk.name().to_string_lossy().trim().to_string();
                let mount = disk.mount_point().to_string_lossy().to_string();
                let name = if raw_name.is_empty() {
                    mount.clone()
                } else {
                    raw_name
                };
                DiskSnapshot {
                    name,
                    mount_point: mount,
                    total_bytes: disk.total_space(),
                    available_bytes: disk.available_space(),
                }
            })
            .collect();

        disks.sort_by(|a, b| a.mount_point.cmp(&b.mount_point));

        // Never synthesize fake fan RPMs from CPU load (`AUD-013`).
        let cooling = None;

        let snapshot = SystemSnapshot {
            schema_version: 3,
            observed_at_utc_ms: now_ms,
            hostname: System::host_name().unwrap_or_else(|| "Unknown computer".to_string()),
            agent_version: monitor_core::brand_config::APP_VERSION.to_string(),
            uptime_seconds: System::uptime(),
            battery,
            cpu,
            ram,
            cooling,
            disks,
        };
        state.sampled_at = Some(Instant::now());
        state.cached = Some(snapshot.clone());
        snapshot
    }

    /// Lightweight single-PID liveness check sharing the existing `sysinfo::System`
    /// instance rather than re-scanning every process table (`AUD-025`).
    pub fn is_pid_alive(&self, pid: u32) -> bool {
        if pid == 0 {
            return false;
        }
        let mut state = match self.state.lock() {
            Ok(s) => s,
            Err(p) => p.into_inner(),
        };
        let pid_sys = sysinfo::Pid::from_u32(pid);
        state
            .sys
            .refresh_processes(ProcessesToUpdate::Some(&[pid_sys]), true);
        state.sys.process(pid_sys).is_some()
    }

    /// Attempts to terminate an operating system process by PID.
    pub fn kill_process(&self, pid: u32) -> Result<(), ProcessKillError> {
        if pid == 0 || pid == 4 || pid == std::process::id() {
            return Err(ProcessKillError::ProtectedProcess);
        }

        let mut state = match self.state.lock() {
            Ok(s) => s,
            Err(p) => p.into_inner(),
        };

        let pid_sys = sysinfo::Pid::from(pid as usize);
        state
            .sys
            .refresh_processes(ProcessesToUpdate::Some(&[pid_sys]), true);

        let Some(process) = state.sys.process(pid_sys) else {
            return Err(ProcessKillError::NotFound);
        };

        if process.kill() {
            Ok(())
        } else {
            Err(ProcessKillError::AccessDenied)
        }
    }
}

impl Default for TelemetrySampler {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn simultaneous_clients_share_real_samples_and_bounded_history() {
        let sampler = TelemetrySampler::new();
        let first = sampler.sample_system_snapshot();
        let second = sampler.clone().sample_system_snapshot();
        assert_eq!(first, second);
        assert!(first.ram.total_bytes > 0);
        assert!(!first.cpu.per_core_usage_percent.is_empty());
        assert_eq!(first.cpu.history_60m_percent.len(), 1);
        assert_eq!(
            first.cpu.history_60m_percent[0],
            first.cpu.total_usage_percent
        );
        assert_eq!(first.ram.history_60m_percent[0], first.ram.usage_percent);
        assert!(
            first
                .cpu
                .top_processes
                .iter()
                .all(|process| process.cpu_percent <= 100.0)
        );
        assert!(
            first.cooling.is_none(),
            "synthetic fan RPM fabrication must stay disabled until hardware sensor integration"
        );
        assert!(!first.disks.is_empty());
        for disk in &first.disks {
            assert!(!disk.mount_point.is_empty());
            assert!(disk.total_bytes > 0);
        }
    }

    #[test]
    fn test_kill_protected_process_rejected() {
        let sampler = TelemetrySampler::new();
        assert_eq!(
            sampler.kill_process(0),
            Err(ProcessKillError::ProtectedProcess)
        );
        assert_eq!(
            sampler.kill_process(4),
            Err(ProcessKillError::ProtectedProcess)
        );
        assert_eq!(
            sampler.kill_process(std::process::id()),
            Err(ProcessKillError::ProtectedProcess)
        );
    }

    #[test]
    fn test_kill_nonexistent_process() {
        let sampler = TelemetrySampler::new();
        assert_eq!(
            sampler.kill_process(999_999_999),
            Err(ProcessKillError::NotFound)
        );
    }
}
