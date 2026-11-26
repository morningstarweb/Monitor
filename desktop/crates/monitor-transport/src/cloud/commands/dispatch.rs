async fn execute_pending_command_inner(state: &AppState, cmd: &PendingCommand) -> CommandResult {
    if is_v2_operation(&cmd.command_type) {
        return execute_v2_command(state, cmd).await;
    }
    match cmd.command_type.as_str() {
        "merge_pr" => execute_merge_pr_command(state, cmd).await,
        "kill_process" => execute_kill_process_command(state, cmd).await,
        "git_operation" => execute_git_operation_command(state, cmd).await,
        "kill_agent_session" => execute_kill_agent_session_command(state, cmd).await,
        "terminal_command" => execute_terminal_command(state, cmd).await,
        "terminal_stop" => execute_terminal_stop_command(state, cmd).await,
        other => CommandResult {
            id: cmd.id.clone(),
            status: "failed".to_string(),
            result: Some(format!("Unsupported command type '{other}'")),
        },
    }
}

fn is_v2_operation(operation: &str) -> bool {
    matches!(
        operation,
        "telemetry.poll"
            | "projects.list"
            | "permissions.get"
            | "process.kill"
            | "git.workspace"
            | "git.diff"
            | "git.commit_files"
            | "git.branches"
            | "git.graph"
            | "git.pull_requests"
            | "git.identity.get"
            | "git.identity.set"
            | "git.stage"
            | "git.unstage"
            | "git.discard_unstaged"
            | "git.commit"
            | "git.fetch"
            | "git.pull"
            | "git.push"
            | "git.create_branch"
            | "git.switch_branch"
            | "git.delete_branch"
            | "git.merge_branch"
            | "git.merge_all_branches"
            | "git.merge_pr"
            | "merge_pr"
            | "watchdog.alerts"
            | "watchdog.ack"
            | "watchdog.snooze"
            | "watchdog.sessions"
            | "watchdog.kill"
            | "watchdog.checkpoints"
            | "watchdog.checkpoint_create"
            | "watchdog.checkpoint_rollback"
            | "terminal.start"
            | "terminal.stop"
            | "terminal.setting.get"
            | "terminal.setting.set"
    )
}
