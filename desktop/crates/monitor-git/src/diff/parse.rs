use monitor_core::{DiffHunk, DiffLine, DiffLineType, FileDiffResponse};

pub const MAX_DIFF_LINES: usize = 500;
pub const MAX_DIFF_BYTES: usize = 128 * 1024; // 128 KB

/// Parses unified diff text into structured hunks with line budgeting.
pub fn parse_unified_diff(file_path: &str, raw_diff: &[u8]) -> FileDiffResponse {
    // Binary detection: check first 8KB for NUL byte or binary message
    let check_len = raw_diff.len().min(8192);
    if raw_diff[..check_len].contains(&0) || raw_diff.windows(12).any(|w| w == b"Binary files") {
        return FileDiffResponse {
            path: file_path.to_string(),
            is_binary: true,
            is_truncated: false,
            old_size_bytes: None,
            new_size_bytes: None,
            insertions: 0,
            deletions: 0,
            hunks: vec![],
        };
    }

    let text = String::from_utf8_lossy(raw_diff);
    let mut hunks: Vec<DiffHunk> = Vec::new();
    let mut current_hunk: Option<DiffHunk> = None;
    let mut old_line_counter = 0;
    let mut new_line_counter = 0;
    let mut total_lines = 0;
    let mut is_truncated = false;
    let mut total_insertions = 0;
    let mut total_deletions = 0;

    for line in text.lines() {
        if total_lines >= MAX_DIFF_LINES || raw_diff.len() > MAX_DIFF_BYTES {
            is_truncated = true;
            break;
        }

        // Hunk header: @@ -old_start,old_lines +new_start,new_lines @@
        if let Some(header_rest) = line.strip_prefix("@@ ") {
            if let Some(hunk) = current_hunk.take() {
                hunks.push(hunk);
            }

            if let Some(end_idx) = header_rest.find(" @@") {
                let range_str = &header_rest[..end_idx];
                let (old_start, old_lines, new_start, new_lines) = parse_hunk_ranges(range_str);
                old_line_counter = old_start;
                new_line_counter = new_start;

                current_hunk = Some(DiffHunk {
                    old_start,
                    old_lines,
                    new_start,
                    new_lines,
                    header: line.to_string(),
                    lines: Vec::new(),
                });
            }
            continue;
        }

        let Some(hunk) = current_hunk.as_mut() else {
            continue;
        };

        if let Some(content) = line.strip_prefix('+') {
            total_insertions += 1;
            total_lines += 1;
            hunk.lines.push(DiffLine {
                line_type: DiffLineType::Addition,
                old_line_num: None,
                new_line_num: Some(new_line_counter),
                content: content.to_string(),
            });
            new_line_counter += 1;
        } else if let Some(content) = line.strip_prefix('-') {
            total_deletions += 1;
            total_lines += 1;
            hunk.lines.push(DiffLine {
                line_type: DiffLineType::Deletion,
                old_line_num: Some(old_line_counter),
                new_line_num: None,
                content: content.to_string(),
            });
            old_line_counter += 1;
        } else if let Some(content) = line.strip_prefix(' ') {
            total_lines += 1;
            hunk.lines.push(DiffLine {
                line_type: DiffLineType::Context,
                old_line_num: Some(old_line_counter),
                new_line_num: Some(new_line_counter),
                content: content.to_string(),
            });
            old_line_counter += 1;
            new_line_counter += 1;
        }
    }

    if let Some(hunk) = current_hunk {
        hunks.push(hunk);
    }

    FileDiffResponse {
        path: file_path.to_string(),
        is_binary: false,
        is_truncated,
        old_size_bytes: None,
        new_size_bytes: None,
        insertions: total_insertions,
        deletions: total_deletions,
        hunks,
    }
}

/// Parses ranges like `-10,5 +10,8`
fn parse_hunk_ranges(range_str: &str) -> (u32, u32, u32, u32) {
    let mut parts = range_str.split_whitespace();
    let old_part = parts.next().unwrap_or("-1,1").trim_start_matches('-');
    let new_part = parts.next().unwrap_or("+1,1").trim_start_matches('+');

    let (old_start, old_lines) = parse_comma_pair(old_part);
    let (new_start, new_lines) = parse_comma_pair(new_part);

    (old_start, old_lines, new_start, new_lines)
}

fn parse_comma_pair(s: &str) -> (u32, u32) {
    let mut p = s.split(',');
    let start = p.next().and_then(|v| v.parse().ok()).unwrap_or(1);
    let lines = p.next().and_then(|v| v.parse().ok()).unwrap_or(1);
    (start, lines)
}
