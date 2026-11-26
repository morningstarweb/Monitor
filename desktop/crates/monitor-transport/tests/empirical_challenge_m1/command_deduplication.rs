#[tokio::test]
async fn challenge_pending_command_deduplication_and_bounded_history() {
    let state = create_challenge_test_state();

    // Adversarial scenario:
    // Cloudflare returns pending commands containing duplicate IDs within the same response,
    // and across successive responses.
    let commands_batch_1 = vec![
        PendingCommand {
            id: "cmd_dup_1".to_string(),
            command_type: "kill_process".to_string(),
            payload: serde_json::json!({ "pid": 0 }), // protected PID -> fails safely
        },
        PendingCommand {
            id: "cmd_dup_1".to_string(), // duplicate ID
            command_type: "kill_process".to_string(),
            payload: serde_json::json!({ "pid": 0 }),
        },
        PendingCommand {
            id: "cmd_dup_2".to_string(),
            command_type: "kill_process".to_string(),
            payload: serde_json::json!({ "pid": 4 }), // protected PID -> fails safely
        },
        PendingCommand {
            id: "cmd_dup_1".to_string(), // another duplicate
            command_type: "kill_process".to_string(),
            payload: serde_json::json!({ "pid": 0 }),
        },
    ];

    let mut executed_command_ids: HashSet<String> = HashSet::new();
    let mut queued_results: Vec<CommandResult> = Vec::new();

    // Simulate the exact heartbeat loop deduplication
    for cmd in commands_batch_1 {
        if executed_command_ids.contains(&cmd.id) {
            continue;
        }
        executed_command_ids.insert(cmd.id.clone());
        if executed_command_ids.len() > 100 {
            executed_command_ids.clear();
            executed_command_ids.insert(cmd.id.clone());
        }

        let cmd_res = execute_pending_command(&state, &cmd).await;
        queued_results.push(cmd_res);
    }

    // Only cmd_dup_1 and cmd_dup_2 should have executed (2 results, not 4)
    assert_eq!(queued_results.len(), 2);
    assert_eq!(queued_results[0].id, "cmd_dup_1");
    assert_eq!(queued_results[1].id, "cmd_dup_2");

    // Second batch with cmd_dup_1 and cmd_dup_2 repeated, plus cmd_dup_3
    let commands_batch_2 = vec![
        PendingCommand {
            id: "cmd_dup_1".to_string(), // already executed in batch 1
            command_type: "kill_process".to_string(),
            payload: serde_json::json!({ "pid": 0 }),
        },
        PendingCommand {
            id: "cmd_dup_3".to_string(), // new command
            command_type: "kill_process".to_string(),
            payload: serde_json::json!({ "pid": std::process::id() }), // protected self
        },
    ];

    for cmd in commands_batch_2 {
        if executed_command_ids.contains(&cmd.id) {
            continue;
        }
        executed_command_ids.insert(cmd.id.clone());
        if executed_command_ids.len() > 100 {
            executed_command_ids.clear();
            executed_command_ids.insert(cmd.id.clone());
        }

        let cmd_res = execute_pending_command(&state, &cmd).await;
        queued_results.push(cmd_res);
    }

    // Only cmd_dup_3 should be added (total 3 results)
    assert_eq!(queued_results.len(), 3);
    assert_eq!(queued_results[2].id, "cmd_dup_3");
    assert_eq!(queued_results[2].status, "failed");
    assert!(
        queued_results[2]
            .result
            .as_ref()
            .unwrap()
            .contains("Protected system process")
    );

    // Test bounded memory: simulate 150 unique command IDs
    for i in 10..160 {
        let cmd = PendingCommand {
            id: format!("cmd_bound_{i}"),
            command_type: "unknown_test".to_string(),
            payload: serde_json::json!({}),
        };
        if executed_command_ids.contains(&cmd.id) {
            continue;
        }
        executed_command_ids.insert(cmd.id.clone());
        if executed_command_ids.len() > 100 {
            executed_command_ids.clear();
            executed_command_ids.insert(cmd.id.clone());
        }
    }
    // Set size must remain bounded <= 100
    assert!(executed_command_ids.len() <= 100);
}
