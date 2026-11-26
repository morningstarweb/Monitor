pub async fn kill_agent_session_handler(
    State(state): State<AppState>,
    Extension(device): Extension<AuthenticatedDevice>,
    AxumPath(session_id): AxumPath<String>,
) -> Result<Json<Value>, StatusCode> {
    if device.role == DeviceRole::Viewer {
        return Err(StatusCode::FORBIDDEN);
    }
    let delete_allowed = state
        .storage
        .get_setting("allow_delete_operations")
        .unwrap_or(None)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !delete_allowed {
        return Err(StatusCode::FORBIDDEN);
    }

    match execute_surgical_kill_session(&state, &session_id) {
        Ok(val) => Ok(Json(val)),
        Err(e) => {
            let msg = e.to_string();
            if msg.contains("not found") {
                Err(StatusCode::NOT_FOUND)
            } else {
                Err(StatusCode::INTERNAL_SERVER_ERROR)
            }
        }
    }
}

fn kill_pid_tree(pid: u32) {
    #[cfg(windows)]
    {
        use std::process::Command;
        let mut cmd = Command::new("taskkill");
        cmd.args(["/PID", &pid.to_string(), "/T", "/F"]);
        let _ = cmd.output();
    }
    #[cfg(not(windows))]
    {
        use std::process::Command;
        let mut cmd = Command::new("kill");
        cmd.args(["-9", &pid.to_string()]);
        let _ = cmd.output();
    }
}

fn is_protected_gui_window(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower == "antigravity.exe"
        || lower == "antigravity ide.exe"
        || lower == "cursor.exe"
        || lower == "code.exe"
        || lower == "windsurf.exe"
        || lower == "idea.exe"
        || lower == "idea64.exe"
        || lower == "pycharm.exe"
        || lower == "pycharm64.exe"
        || lower == "webstorm.exe"
        || lower == "studio64.exe"
        || lower == "zed.exe"
        || lower == "fleet.exe"
}

fn is_protected_gui_or_shell(name: &str) -> bool {
    let lower = name.to_lowercase();
    is_protected_gui_window(&lower)
        || lower == "powershell.exe"
        || lower == "pwsh.exe"
        || lower == "cmd.exe"
        || lower == "wt.exe"
        || lower == "windowsterminal.exe"
        || lower == "conhost.exe"
        || lower == "bash.exe"
        || lower == "zsh.exe"
}
