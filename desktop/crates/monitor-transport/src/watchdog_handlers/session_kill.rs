pub fn execute_surgical_kill_session(state: &AppState, session_id: &str) -> anyhow::Result<Value> {
    let sessions = state
        .storage
        .get_active_agent_sessions()
        .map_err(|e| anyhow::anyhow!("Storage error: {e}"))?;

    let session = sessions
        .into_iter()
        .find(|s| s.session_id == session_id)
        .ok_or_else(|| anyhow::anyhow!("Session '{session_id}' not found or already ended"))?;

    let current_pid = std::process::id();
    let mut killed_pids = Vec::new();

    let mut sys = sysinfo::System::new_all();
    sys.refresh_all();

    // 1. Direct process kill on target_pid (ONLY if not self and NOT a protected GUI IDE window or shell)
    if session.pid > 0 && session.pid != current_pid {
        let is_protected = sys
            .process(sysinfo::Pid::from_u32(session.pid))
            .map(|p| is_protected_gui_or_shell(&p.name().to_string_lossy()))
            .unwrap_or(false);

        if !is_protected {
            tracing::info!("Emergency stop surgical kill on target PID {}", session.pid);
            kill_pid_tree(session.pid);
            killed_pids.push(session.pid);
        } else {
            tracing::info!(
                "Session target PID {} is a protected GUI IDE or Shell; shielding window and targeting child workers/engine instead",
                session.pid
            );
        }
    }

    // 2. Deep process sweep using sysinfo:
    // Terminate any active worker commands, tools, or language server processes running in the repo or associated with the agent
    let repo_lower = session.repo_path.as_deref().unwrap_or("").to_lowercase();
    let profile_lower = session.profile_name.to_lowercase();

    for (pid, proc_info) in sys.processes() {
        let p_u32 = pid.as_u32();
        if p_u32 == current_pid {
            continue;
        }

        let name_lower = proc_info.name().to_string_lossy().to_lowercase();
        // Protect Monitor's own components and user's main IDE GUI windows
        if name_lower.contains("monitor") || is_protected_gui_window(&name_lower) {
            continue;
        }

        let cmd_str = proc_info
            .cmd()
            .iter()
            .map(|s| s.to_string_lossy().to_lowercase())
            .collect::<Vec<_>>()
            .join(" ");

        let cwd_str = proc_info
            .cwd()
            .map(|p| p.to_string_lossy().to_lowercase())
            .unwrap_or_default();

        let is_worker_or_tool = name_lower.contains("powershell")
            || name_lower.contains("cmd.exe")
            || name_lower.contains("git")
            || name_lower.contains("cargo")
            || name_lower.contains("rustc")
            || name_lower.contains("node")
            || name_lower.contains("python")
            || name_lower.contains("bash")
            || name_lower.contains("sh.exe");

        // Terminate any tool/worker running within the repository folder or targeting it
        if !repo_lower.is_empty() && is_worker_or_tool {
            // Preserve interactive shells (e.g. idle PowerShell or CMD prompt); only kill when executing commands
            let is_interactive_shell = (name_lower == "powershell.exe" || name_lower == "cmd.exe")
                && !cmd_str.contains(" -command ")
                && !cmd_str.contains(" /c ")
                && !cmd_str.contains(" -c ")
                && !cmd_str.contains(".ps1")
                && !cmd_str.contains(".bat");

            if !is_interactive_shell
                && (cwd_str.starts_with(&repo_lower) || cmd_str.contains(&repo_lower))
            {
                tracing::info!("Emergency stop terminating repo tool PID {p_u32} ({name_lower})");
                kill_pid_tree(p_u32);
                killed_pids.push(p_u32);
            }
        }

        // Terminate specific agent engines:
        // Antigravity / Gemini: terminate language_server.exe
        if profile_lower.contains("gemini")
            || profile_lower.contains("antigravity")
            || profile_lower.contains("workspace ai")
        {
            if name_lower == "language_server.exe" || name_lower == "language_server" {
                if cmd_str.contains("antigravity") || cmd_str.contains("gemini") {
                    tracing::info!(
                        "Emergency stop terminating Antigravity Language Server PID {p_u32}"
                    );
                    kill_pid_tree(p_u32);
                    killed_pids.push(p_u32);
                }
            } else if name_lower == "agy.exe" || name_lower == "gemini-cli.exe" {
                tracing::info!("Emergency stop terminating Gemini CLI PID {p_u32}");
                kill_pid_tree(p_u32);
                killed_pids.push(p_u32);
            }
        } else if profile_lower.contains("claude") {
            if name_lower.contains("claude") {
                tracing::info!("Emergency stop terminating Claude CLI PID {p_u32}");
                kill_pid_tree(p_u32);
                killed_pids.push(p_u32);
            }
        } else if profile_lower.contains("aider") && name_lower.contains("aider") {
            tracing::info!("Emergency stop terminating Aider PID {p_u32}");
            kill_pid_tree(p_u32);
            killed_pids.push(p_u32);
        }
    }

    let now_ms = chrono::Utc::now().timestamp_millis();
    let _ = state
        .storage
        .end_agent_session(session_id, Some(-9), now_ms);
    if let Some(ref repo) = session.repo_path {
        let _ = state
            .storage
            .end_all_agent_sessions_for_repo(repo, Some(-9), now_ms);
    }

    let _ = state.storage.insert_audit_entry(
        "remote_phone",
        "terminate_agent",
        &format!("{}:{}", session.profile_name, session.pid),
        "success",
    );

    let _ = state.event_tx.send(ServerEvent::ResyncRequired);

    Ok(json!({
        "success": true,
        "session_id": session_id,
        "pid": session.pid,
        "killed_pids": killed_pids,
    }))
}
