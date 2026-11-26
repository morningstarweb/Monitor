mod catalog;
mod matching;

#[cfg(test)]
mod tests;

use catalog::{classify_process_profile, is_gui_ide_proc, is_ignored_process};
use matching::find_repo_path;
use monitor_storage::StoredAgentProfile;
use std::collections::{HashMap, HashSet};
use std::path::Path;
use sysinfo::System;

pub use catalog::{UNIVERSAL_AGENT_CATALOG, UniversalCatalogEntry, is_agent_engine, is_gui_ide};

#[derive(Debug, Clone)]
pub struct DetectedAgentProcess {
    pub pid: u32,
    pub profile_name: String,
    pub repo_path: Option<String>,
    pub descendant_pids: Vec<u32>,
    pub started_at_ms: i64,
}

type AgentCandidate = (u32, String, Option<String>, i64);
type CandidateKey = (Option<String>, String);

pub fn detect_agent_processes(
    sys: &System,
    profiles: &[StoredAgentProfile],
    known_repos: &[String],
) -> Vec<DetectedAgentProcess> {
    let current_pid = std::process::id();

    let mut children_map: HashMap<u32, Vec<u32>> = HashMap::new();
    for (pid, proc_info) in sys.processes() {
        let p_u32 = pid.as_u32();
        if let Some(parent) = proc_info.parent() {
            children_map.entry(parent.as_u32()).or_default().push(p_u32);
        }
    }

    let mut candidates = Vec::new();
    let mut candidate_pids = HashSet::new();

    for (pid, proc_info) in sys.processes() {
        let p_u32 = pid.as_u32();
        if p_u32 == current_pid {
            continue;
        }

        let name = proc_info.name().to_string_lossy();
        let cmd = proc_info.cmd();
        if is_ignored_process(&name, cmd) {
            continue;
        }

        if let Some(profile_name) = classify_process_profile(&name, proc_info.exe(), cmd, profiles)
        {
            let repo_path = find_repo_path(proc_info.cwd(), cmd, known_repos);
            let started_at_ms = proc_info.start_time() as i64 * 1000;
            candidates.push((p_u32, profile_name, repo_path, started_at_ms));
            candidate_pids.insert(p_u32);
        }
    }

    // Find all parents of agent engines so we can exclude the outer GUI IDE wrapper
    let engine_parents: HashSet<u32> = candidates
        .iter()
        .filter_map(|(p, _, _, _)| {
            let proc_info = sys.process(sysinfo::Pid::from_u32(*p))?;
            let name = proc_info.name().to_string_lossy();
            if is_agent_engine(&name) {
                proc_info.parent().map(sysinfo::Pid::as_u32)
            } else {
                None
            }
        })
        .collect();

    // Filter out candidates that are children/descendants of another candidate
    // This ensures only the root agent process represents the session,
    // while giving priority to agent engines over outer GUI IDE wrappers.
    let mut root_candidates = Vec::new();
    for (p_u32, profile_name, repo_path, started_at_ms) in candidates {
        let (name, exe) = sys
            .process(sysinfo::Pid::from_u32(p_u32))
            .map(|p| {
                (
                    p.name().to_string_lossy().to_string(),
                    p.exe().map(Path::to_path_buf),
                )
            })
            .unwrap_or_default();
        if is_gui_ide_proc(&name, exe.as_deref()) && engine_parents.contains(&p_u32) {
            // Skip the outer GUI wrapper since its child agent engine is being tracked directly
            continue;
        }

        if !has_tracked_ancestor(sys, p_u32, &candidate_pids, &engine_parents) {
            root_candidates.push((p_u32, profile_name, repo_path, started_at_ms));
        }
    }

    // Preserve distinct models running concurrently even in the same repository.
    let deduped = deduplicate_candidates(root_candidates);

    let mut detected = Vec::new();
    for (pid, profile_name, repo_path, started_at_ms) in deduped {
        let descendants = collect_descendants(pid, &children_map);
        detected.push(DetectedAgentProcess {
            pid,
            profile_name,
            repo_path,
            descendant_pids: descendants,
            started_at_ms,
        });
    }

    detected
}

fn has_tracked_ancestor(
    sys: &System,
    pid: u32,
    candidate_pids: &HashSet<u32>,
    engine_parents: &HashSet<u32>,
) -> bool {
    let mut curr = pid;
    while let Some(proc_info) = sys.process(sysinfo::Pid::from_u32(curr)) {
        if let Some(parent) = proc_info.parent() {
            let parent_u32 = parent.as_u32();
            if parent_u32 != pid
                && candidate_pids.contains(&parent_u32)
                && !engine_parents.contains(&parent_u32)
            {
                return true;
            }
            curr = parent_u32;
        } else {
            break;
        }
    }
    false
}

pub(super) fn deduplicate_candidates(root_candidates: Vec<AgentCandidate>) -> Vec<AgentCandidate> {
    let mut best_by_key: HashMap<CandidateKey, AgentCandidate> = HashMap::new();
    for (p_u32, profile_name, repo_path, started_at_ms) in root_candidates {
        let key = (repo_path.clone(), profile_name.to_lowercase());
        if let Some(existing) = best_by_key.get(&key) {
            if started_at_ms < existing.3 {
                best_by_key.insert(key, (p_u32, profile_name, repo_path, started_at_ms));
            }
        } else {
            best_by_key.insert(key, (p_u32, profile_name, repo_path, started_at_ms));
        }
    }
    let mut out: Vec<_> = best_by_key.into_values().collect();
    out.sort_by_key(|item| (item.3, item.0));
    out
}

fn collect_descendants(root: u32, children_map: &HashMap<u32, Vec<u32>>) -> Vec<u32> {
    let mut descendants = Vec::new();
    let mut queue = vec![root];
    let mut visited = HashSet::new();
    visited.insert(root);

    while let Some(current) = queue.pop() {
        if let Some(children) = children_map.get(&current) {
            for &child in children {
                if visited.insert(child) {
                    descendants.push(child);
                    queue.push(child);
                }
            }
        }
    }

    descendants
}
