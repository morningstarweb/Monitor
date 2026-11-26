use super::{MAX_LINE_BYTES, MAX_OUTPUT_BYTES};
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use tokio::io::{AsyncRead, AsyncReadExt};

/// Fixed read size for the streaming output path.
pub(super) const READ_CHUNK_BYTES: usize = 8192;

/// Atomically reserve bytes in the shared output budget.
pub(super) fn reserve_output_bytes(
    bytes: usize,
    used: &AtomicUsize,
    overflow: &AtomicBool,
) -> bool {
    loop {
        let current = used.load(Ordering::SeqCst);
        let next = current.saturating_add(bytes);
        if next > MAX_OUTPUT_BYTES {
            overflow.store(true, Ordering::SeqCst);
            return false;
        }
        if used
            .compare_exchange(current, next, Ordering::SeqCst, Ordering::SeqCst)
            .is_ok()
        {
            return true;
        }
    }
}

/// Read at most `limit` bytes from an async source into a bounded buffer.
pub(super) async fn read_bounded<R: AsyncRead + Unpin>(
    reader: &mut R,
    target: &mut Vec<u8>,
    limit: usize,
) -> std::io::Result<()> {
    let mut chunk = [0u8; 8192];
    while target.len() < limit {
        let read = reader.read(&mut chunk).await?;
        if read == 0 {
            break;
        }
        let take = read.min(limit - target.len());
        target.extend_from_slice(&chunk[..take]);
    }
    Ok(())
}

/// Emit one line whose byte length has already been clamped to `MAX_LINE_BYTES`,
/// cutting at a UTF-8 boundary, then reuse the buffer.
fn emit_bounded_line<F: FnMut(String)>(
    pending: &mut Vec<u8>,
    overflow: &AtomicBool,
    mut on_line: F,
) {
    let mut end = pending.len().min(MAX_LINE_BYTES);
    while end > 0 && std::str::from_utf8(&pending[..end]).is_err() {
        end -= 1;
    }
    if pending.len() > MAX_LINE_BYTES {
        overflow.store(true, Ordering::SeqCst);
    }
    if end > 0 {
        on_line(String::from_utf8_lossy(&pending[..end]).into_owned());
    }
    pending.clear();
}

/// Stream lines from an async reader with a hard per-line memory bound (AUD-06 / NEW-02).
pub(super) async fn stream_bounded_lines<R, F>(mut reader: R, overflow: &AtomicBool, mut on_line: F)
where
    R: AsyncRead + Unpin,
    F: FnMut(String),
{
    let mut pending: Vec<u8> = Vec::with_capacity(MAX_LINE_BYTES.min(READ_CHUNK_BYTES));
    let mut chunk = [0u8; READ_CHUNK_BYTES];
    loop {
        let n = match reader.read(&mut chunk).await {
            Ok(0) | Err(_) => break,
            Ok(n) => n,
        };
        let mut pos = 0;
        while pos < n {
            match chunk[pos..n]
                .iter()
                .position(|&byte| byte == b'\n' || byte == b'\r')
            {
                Some(relative) => {
                    let sep = pos + relative;
                    let room = MAX_LINE_BYTES.saturating_sub(pending.len());
                    let take = (sep - pos).min(room);
                    pending.extend_from_slice(&chunk[pos..pos + take]);
                    if take < sep - pos {
                        overflow.store(true, Ordering::SeqCst);
                    }
                    emit_bounded_line(&mut pending, overflow, &mut on_line);
                    pos = if chunk[sep] == b'\r' && sep + 1 < n && chunk[sep + 1] == b'\n' {
                        sep + 2
                    } else {
                        sep + 1
                    };
                }
                None => {
                    let room = MAX_LINE_BYTES.saturating_sub(pending.len());
                    let take = (n - pos).min(room);
                    if take > 0 {
                        pending.extend_from_slice(&chunk[pos..pos + take]);
                    }
                    if take < n - pos {
                        overflow.store(true, Ordering::SeqCst);
                    }
                    pos = n;
                }
            }
        }
    }
    if !pending.is_empty() {
        emit_bounded_line(&mut pending, overflow, &mut on_line);
    }
}

/// Append a line within a fixed byte budget, cutting only at UTF-8 boundaries.
pub(super) fn append_bounded(target: &mut String, line: &str, budget: &mut usize) {
    if *budget == 0 {
        return;
    }
    let mut take = line.len().min(*budget);
    while take > 0 && !line.is_char_boundary(take) {
        take -= 1;
    }
    if take > 0 {
        target.push_str(&line[..take]);
        *budget -= take;
    }
}
