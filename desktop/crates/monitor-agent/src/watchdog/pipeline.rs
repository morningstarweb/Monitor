use super::detector::detect_agent_processes;
use super::keep_awake::set_keep_awake;
use super::rules::{RuleEngineState, evaluate_rules};
use super::signals::SessionActivityTracker;
use monitor_core::ServerEvent;
use monitor_git::{CheckpointEngine, GitExecutor};
use monitor_storage::{StoredAgentSession, StoredCheckpoint};
use monitor_transport::AppState;
use std::collections::HashMap;
use std::path::Path;
use std::time::Duration;
use sysinfo::System;
use tokio::task::JoinHandle;
use tracing::{error, info};

pub fn spawn_watchdog_pipeline_task(state: AppState) -> JoinHandle<()> {
    tokio::spawn(async move {
        info!("Starting Monitor Watchdog Pipeline (3s fast sample interval)");
        // Clear any stale non-ended sessions left over from a previous run/upgrade.
        let _ = state
            .storage
            .end_all_active_sessions(None, chrono::Utc::now().timestamp_millis());
        let mut sys = System::new_all();
        let mut active_sessions: HashMap<String, SessionActivityTracker> = HashMap::new();
        let mut rule_state = RuleEngineState::default();
        let mut last_checkpoint_by_repo: HashMap<String, i64> = HashMap::new();
        let mut last_probe_by_repo: HashMap<String, i64> = HashMap::new();
        let mut last_dirty_seen_by_repo: HashMap<String, (String, i64)> = HashMap::new();
        let mut last_checkpoint_fp_by_repo: HashMap<String, String> = HashMap::new();
        let git_executor = GitExecutor::new().ok();

        let http_client = reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .ok();

        loop {
            tokio::time::sleep(Duration::from_secs(3)).await;

            let now_ms = chrono::Utc::now().timestamp_millis();

            // 1. Fetch profiles, rules, and registered repositories
            let profiles = state.storage.list_agent_profiles().unwrap_or_default();
            let rules = state.storage.list_alert_rules().unwrap_or_default();
            let repos = state.storage.list_repositories().unwrap_or_default();
            let known_repos: Vec<String> = repos.iter().map(|p| p.canonical_path.clone()).collect();

            // 2. Targeted process & memory refresh (avoid full disk/network/component rescan on 3s tick)
            sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);
            sys.refresh_memory();

            // 3. Multi-tier Universal Agent & IDE Detection
            let mut detected = detect_agent_processes(&sys, &profiles, &known_repos);

            // Tier 3: Working Tree Fallback if no specific process was recognized
            if detected.is_empty() {
                for repo in &repos {
                    let index_path = Path::new(&repo.canonical_path).join(".git").join("index");
                    let index_mtime_ms = std::fs::metadata(&index_path)
                        .ok()
                        .and_then(|meta| meta.modified().ok())
                        .and_then(|modified| modified.duration_since(std::time::UNIX_EPOCH).ok())
                        .map(|duration| duration.as_millis() as i64)
                        .unwrap_or(0);
                    let dirty_mtime_ms = last_dirty_seen_by_repo
                        .get(&repo.canonical_path)
                        .map(|(_, ts)| *ts)
                        .unwrap_or(0);
                    let activity_ms = index_mtime_ms.max(dirty_mtime_ms);
                    if activity_ms > 0 && now_ms - activity_ms <= 180_000 {
                        detected.push(super::detector::DetectedAgentProcess {
                            pid: 0,
                            profile_name: "Workspace AI".to_string(),
                            repo_path: Some(repo.canonical_path.clone()),
                            descendant_pids: Vec::new(),
                            started_at_ms: activity_ms,
                        });
                        break;
                    }
                }
            }

            // Update existing or add new sessions
            let mut detected_pids = HashMap::new();
            for p in &detected {
                detected_pids.insert(p.pid, p.clone());
            }

            // Sync with active trackers
            for p in &detected {
                let session_id = format!("sess_{}_{}", p.started_at_ms, p.pid);
                let tracker = active_sessions
                    .entry(session_id.clone())
                    .or_insert_with(|| {
                        let stall_threshold = profiles
                            .iter()
                            .find(|prof| prof.name == p.profile_name)
                            .map(|prof| prof.stall_threshold_s)
                            .unwrap_or(600);

                        SessionActivityTracker::new(
                            session_id,
                            p.profile_name.clone(),
                            p.pid,
                            p.repo_path.clone(),
                            stall_threshold,
                            p.started_at_ms,
                        )
                    });

                let (new_state, state_changed) =
                    tracker.sample_and_update(&sys, &p.descendant_pids, now_ms);

                if state_changed {
                    info!(
                        "Agent session {} ({}) state transition -> {}",
                        tracker.session_id, tracker.profile_name, new_state
                    );
                }

                // Persist session to SQLite
                let stored_sess = StoredAgentSession {
                    session_id: tracker.session_id.clone(),
                    profile_name: tracker.profile_name.clone(),
                    pid: tracker.root_pid,
                    repo_path: tracker.repo_path.clone(),
                    state: tracker.current_state.clone(),
                    started_at_utc_ms: tracker.started_at_ms,
                    last_activity_utc_ms: tracker.last_activity_ms,
                    ended_at_utc_ms: if tracker.current_state == "Ended" {
                        Some(now_ms)
                    } else {
                        None
                    },
                    exit_code: tracker.exit_code,
                };
                let _ = state.storage.save_agent_session(&stored_sess);
            }

            // Repo-wide automated checkpoint scanner (works regardless of which AI or editor modified files)
            if let Some(git_exec) = &git_executor {
                let any_live_agent = !detected.is_empty();
                let probe_interval_ms = if any_live_agent { 15_000 } else { 30_000 };
                let checkpoint_cooldown_ms = 120_000;

                for repo in &repos {
                    let repo_str = &repo.canonical_path;
                    let last_probe = last_probe_by_repo.get(repo_str).copied().unwrap_or(0);
                    if now_ms - last_probe < probe_interval_ms {
                        continue;
                    }
                    last_probe_by_repo.insert(repo_str.clone(), now_ms);

                    let path = Path::new(repo_str);
                    if !path.join(".git").exists() {
                        continue;
                    }

                    match CheckpointEngine::working_status_fingerprint(git_exec, path).await {
                        Ok(None) => {
                            last_checkpoint_fp_by_repo.remove(repo_str);
                        }
                        Ok(Some(fp)) => {
                            let prev_dirty = last_dirty_seen_by_repo.get(repo_str).map(|(p, _)| p);
                            if prev_dirty != Some(&fp) {
                                last_dirty_seen_by_repo
                                    .insert(repo_str.clone(), (fp.clone(), now_ms));
                            }

                            let changed_since_chk =
                                last_checkpoint_fp_by_repo.get(repo_str) != Some(&fp);
                            let last_chk =
                                last_checkpoint_by_repo.get(repo_str).copied().unwrap_or(0);
                            if last_chk == 0
                                || (changed_since_chk
                                    && now_ms - last_chk >= checkpoint_cooldown_ms)
                            {
                                let repo_norm = repo_str
                                    .strip_prefix(r"\\?\")
                                    .unwrap_or(repo_str)
                                    .to_lowercase();
                                let agent_label = detected
                                    .iter()
                                    .find(|d| {
                                        d.repo_path
                                            .as_deref()
                                            .map(|r| {
                                                r.strip_prefix(r"\\?\").unwrap_or(r).to_lowercase()
                                                    == repo_norm
                                            })
                                            .unwrap_or(false)
                                    })
                                    .or_else(|| detected.first())
                                    .map(|d| d.profile_name.as_str())
                                    .unwrap_or("Workspace Edits");

                                if let Ok(chk) = CheckpointEngine::create_checkpoint(
                                    git_exec,
                                    path,
                                    &format!("Automated Watchdog Checkpoint ({agent_label})"),
                                    false,
                                )
                                .await
                                {
                                    last_checkpoint_by_repo.insert(repo_str.clone(), now_ms);
                                    last_checkpoint_fp_by_repo.insert(repo_str.clone(), fp);
                                    info!(
                                        "Created automated git checkpoint: {} ({}) in {}",
                                        chk.checkpoint_id, chk.commit_sha, repo_str
                                    );
                                    let stored = StoredCheckpoint {
                                        checkpoint_id: chk.checkpoint_id,
                                        repo_path: chk.repo_path,
                                        branch: chk.branch,
                                        commit_sha: chk.commit_sha,
                                        created_at_utc_ms: chk.created_at_utc_ms,
                                        files_changed_count: chk.files_changed_count,
                                        lines_added: chk.lines_added,
                                        lines_deleted: chk.lines_deleted,
                                        is_safety: chk.is_safety,
                                        description: chk.description,
                                    };
                                    let _ = state.storage.insert_checkpoint(&stored);
                                    let _ = state.event_tx.send(ServerEvent::ResyncRequired);

                                    if let Some(alert) = CheckpointEngine::evaluate_mass_change(
                                        stored.files_changed_count,
                                        stored.lines_deleted,
                                    ) {
                                        let mass_alert = monitor_storage::StoredAlert {
                                            alert_id: format!("alert_{now_ms}_mass_change"),
                                            rule_kind: "repo.mass_change".to_string(),
                                            severity: "critical".to_string(),
                                            title: "Critical: Repository Mass Change Detected"
                                                .to_string(),
                                            body: alert.message,
                                            dedup_key: format!("mass_change_{}", stored.repo_path),
                                            session_id: detected.first().map(|d| {
                                                format!("sess_{}_{}", d.started_at_ms, d.pid)
                                            }),
                                            value: Some(stored.files_changed_count as f64),
                                            created_at_utc_ms: now_ms,
                                            delivered_at_utc_ms: None,
                                            acked_at_utc_ms: None,
                                            snoozed_until_utc_ms: None,
                                            recovered_at_utc_ms: None,
                                        };
                                        let _ = state.storage.insert_alert(&mass_alert);
                                    }
                                }
                            }
                        }
                        Err(_) => {}
                    }
                }
            }

            // Check sessions that ended (no longer running)
            let mut ended_keys = Vec::new();
            for (sess_id, tracker) in &mut active_sessions {
                if !detected_pids.contains_key(&tracker.root_pid)
                    && tracker.current_state != "Ended"
                {
                    tracker.current_state = "Ended".to_string();
                    ended_keys.push(sess_id.clone());

                    let stored_sess = StoredAgentSession {
                        session_id: tracker.session_id.clone(),
                        profile_name: tracker.profile_name.clone(),
                        pid: tracker.root_pid,
                        repo_path: tracker.repo_path.clone(),
                        state: "Ended".to_string(),
                        started_at_utc_ms: tracker.started_at_ms,
                        last_activity_utc_ms: tracker.last_activity_ms,
                        ended_at_utc_ms: Some(now_ms),
                        exit_code: tracker.exit_code,
                    };
                    let _ = state.storage.save_agent_session(&stored_sess);
                }
            }

            // Update Windows Keep-Awake lock based on active sessions
            let any_active_session = active_sessions
                .values()
                .any(|t| t.current_state == "Active" || t.current_state == "Quiet");
            set_keep_awake(any_active_session);

            // 4. Sample telemetry for system-level rules
            let telemetry = state.telemetry.sample_system_snapshot();

            // 5. Evaluate alert rules
            let eval_result = evaluate_rules(
                &rules,
                &active_sessions,
                &telemetry,
                &mut rule_state,
                now_ms,
            );

            // Handle recovered alerts
            for dedup_key in eval_result.recovered_dedup_keys {
                info!("Watchdog alert recovered: {dedup_key}");
                let _ = state.storage.mark_alert_recovered(&dedup_key, now_ms);
            }

            // Handle newly fired alerts
            for alert in eval_result.new_alerts {
                info!(
                    "Watchdog alert fired: [{}] {} - {}",
                    alert.severity, alert.title, alert.body
                );
                let _ = state.storage.insert_alert(&alert);

                // Broadcast over WebSocket to active clients
                let event = ServerEvent::AlertTriggered {
                    alert_id: alert.alert_id.clone(),
                    rule_name: alert.rule_kind.clone(),
                    message: alert.body.clone(),
                    severity: alert.severity.clone(),
                };
                let _ = state.event_tx.send(event);

                // Cloud push delivery
                let maybe_account = {
                    state
                        .cloud_account
                        .lock()
                        .map(|g| g.clone())
                        .unwrap_or(None)
                };

                if let Some(account) = maybe_account
                    && let Some(client) = &http_client
                {
                    let broker_url = monitor_transport::cloud::broker_base_url();
                    let push_url = format!("{broker_url}/api/v1/alerts/push");
                    let payload = serde_json::json!({
                        "alert_id": alert.alert_id,
                        "kind": alert.rule_kind,
                        "severity": alert.severity,
                        "title": alert.title,
                        "body": alert.body,
                        "timestamp": alert.created_at_utc_ms,
                        "dedup_key": alert.dedup_key,
                    });

                    let req = client
                        .post(&push_url)
                        .header("Authorization", format!("Bearer {}", account.token))
                        .json(&payload);

                    tokio::spawn(async move {
                        if let Err(e) = req.send().await {
                            error!("Failed to relay alert to cloud broker: {e}");
                        }
                    });
                }
            }

            // Clean up ended sessions from in-memory map if ended for over 5 minutes
            active_sessions.retain(|_, tracker| {
                if tracker.current_state == "Ended" {
                    now_ms - tracker.last_activity_ms < 300_000
                } else {
                    true
                }
            });
        }
    })
}
