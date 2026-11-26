use super::{CommandLedgerEntry, CommandLedgerState, LedgerAdmission};
use rusqlite::{Connection, OptionalExtension, Result, params};

type ExistingCommandRow = (
    String,
    String,
    String,
    Option<String>,
    Option<String>,
    Option<String>,
);

/// Evaluates and atomically records an incoming command into the durable SQLite ledger.
pub fn record_incoming_command(
    conn: &Connection,
    entry: &CommandLedgerEntry,
) -> Result<LedgerAdmission> {
    let existing: Option<ExistingCommandRow> = conn
        .query_row(
            "SELECT state, canonical_params_hash, viewer_device_id, result_code, result_digest, result_summary
             FROM command_ledger WHERE command_id = ?1",
            params![entry.command_id],
            |row| {
                Ok((
                    row.get(0)?,
                    row.get(1)?,
                    row.get(2)?,
                    row.get(3)?,
                    row.get(4)?,
                    row.get(5)?,
                ))
            },
        )
        .optional()?;

    if let Some((state_str, existing_hash, existing_viewer, code, digest, summary)) = existing {
        if existing_hash != entry.canonical_params_hash || existing_viewer != entry.viewer_device_id
        {
            return Ok(LedgerAdmission::Conflict);
        }
        let existing_state = CommandLedgerState::from_db_str(&state_str);
        if matches!(
            existing_state,
            CommandLedgerState::Completed
                | CommandLedgerState::Executing
                | CommandLedgerState::UnknownAfterCrash
        ) {
            let mut dup_entry = entry.clone();
            dup_entry.state = existing_state;
            dup_entry.result_code = code;
            dup_entry.result_digest = digest;
            dup_entry.result_summary = summary;
            return Ok(LedgerAdmission::Duplicate(Box::new(dup_entry)));
        }

        // Previous attempt failed or was cancelled; re-admit for execution
        conn.execute(
            "UPDATE command_ledger
             SET state = 'EXECUTING', started_at_utc_ms = ?1, finished_at_utc_ms = NULL,
                 result_code = NULL, result_digest = NULL, result_summary = NULL
             WHERE command_id = ?2",
            params![
                entry.started_at_utc_ms.unwrap_or(entry.received_at_utc_ms),
                entry.command_id
            ],
        )?;
        return Ok(LedgerAdmission::Admitted);
    }

    conn.execute(
        "INSERT INTO command_ledger (
            command_id, viewer_device_id, operation, canonical_params_hash,
            session_id, accepted_epoch, tier, state, received_at_utc_ms,
            started_at_utc_ms, deadline_ms
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
        params![
            entry.command_id,
            entry.viewer_device_id,
            entry.operation,
            entry.canonical_params_hash,
            entry.session_id,
            entry.accepted_epoch as i64,
            entry.tier,
            CommandLedgerState::Executing.as_str(),
            entry.received_at_utc_ms,
            entry.started_at_utc_ms.unwrap_or(entry.received_at_utc_ms),
            entry.deadline_ms as i64,
        ],
    )?;

    Ok(LedgerAdmission::Admitted)
}

/// Builds a stable, viewer-scoped idempotency key for a client request ID.
///
/// The length prefix prevents ambiguity between `(viewer, request)` pairs that
/// could otherwise collide when concatenated. The key is durable: a restarted
/// host recognizes the same client request and never repeats its side effect.
pub fn idempotency_key(viewer_device_id: &str, request_id: &str) -> String {
    format!(
        "v1/{len}:{viewer}:{request}",
        len = viewer_device_id.len(),
        viewer = viewer_device_id,
        request = request_id
    )
}

/// Atomically records a command under its `(viewer, request_id)` identity.
///
/// Uses the existing command ledger as the durable store. The transaction
/// makes the read-check-insert a single operation even when several dispatch
/// workers process the same request concurrently. Duplicate admissions carry
/// the previously recorded result, so callers can return it verbatim.
pub fn record_idempotent_command(
    conn: &mut Connection,
    viewer_device_id: &str,
    request_id: &str,
    entry: &CommandLedgerEntry,
) -> Result<LedgerAdmission> {
    let mut keyed = entry.clone();
    keyed.command_id = idempotency_key(viewer_device_id, request_id);
    keyed.viewer_device_id = viewer_device_id.to_string();

    let tx = conn.transaction()?;
    let admission = record_incoming_command(&tx, &keyed)?;
    tx.commit()?;
    Ok(admission)
}

/// Finalizes a command's outcome in the ledger.
pub fn finalize_command(
    conn: &Connection,
    command_id: &str,
    state: CommandLedgerState,
    result_code: Option<&str>,
    result_digest: Option<&str>,
    result_summary: Option<&str>,
) -> Result<()> {
    let now = chrono::Utc::now().timestamp_millis();
    conn.execute(
        "UPDATE command_ledger
         SET state = ?1, result_code = ?2, result_digest = ?3, result_summary = ?4, finished_at_utc_ms = ?5
         WHERE command_id = ?6",
        params![state.as_str(), result_code, result_digest, result_summary, now, command_id],
    )?;
    Ok(())
}

/// Reconciles unfinalized commands following a process or host restart.
pub fn reconcile_crash_states(conn: &Connection) -> Result<usize> {
    let affected = conn.execute(
        "UPDATE command_ledger
         SET state = 'UNKNOWN_AFTER_CRASH'
         WHERE state = 'EXECUTING'",
        [],
    )?;
    Ok(affected)
}

/// Retrieves a command entry by ID.
pub fn get_command(conn: &Connection, command_id: &str) -> Result<Option<CommandLedgerEntry>> {
    conn.query_row(
        "SELECT command_id, viewer_device_id, operation, canonical_params_hash, session_id,
                accepted_epoch, tier, state, received_at_utc_ms, started_at_utc_ms,
                finished_at_utc_ms, deadline_ms, result_code, result_digest, result_summary
         FROM command_ledger WHERE command_id = ?1",
        params![command_id],
        |row| {
            let state_str: String = row.get(7)?;
            Ok(CommandLedgerEntry {
                command_id: row.get(0)?,
                viewer_device_id: row.get(1)?,
                operation: row.get(2)?,
                canonical_params_hash: row.get(3)?,
                session_id: row.get(4)?,
                accepted_epoch: row.get::<_, i64>(5)? as u64,
                tier: row.get(6)?,
                state: CommandLedgerState::from_db_str(&state_str),
                received_at_utc_ms: row.get(8)?,
                started_at_utc_ms: row.get(9)?,
                finished_at_utc_ms: row.get(10)?,
                deadline_ms: row.get::<_, i64>(11)? as u64,
                result_code: row.get(12)?,
                result_digest: row.get(13)?,
                result_summary: row.get(14)?,
            })
        },
    )
    .optional()
}
