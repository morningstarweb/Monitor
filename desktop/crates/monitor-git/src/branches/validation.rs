use monitor_core::CoreError;

/// Automatically sanitizes any input string into a valid, standard Git branch name.
/// Converts spaces, tabs, and invalid git characters into clean hyphens, collapses
/// multiple hyphens, trims leading/trailing slashes, dots, and hyphens.
pub fn sanitize_branch_name(name: &str) -> String {
    let mut clean = String::with_capacity(name.len());
    let mut prev_hyphen = false;

    for c in name.chars() {
        if c.is_whitespace()
            || c.is_ascii_control()
            || matches!(
                c,
                '~' | '^'
                    | ':'
                    | '?'
                    | '*'
                    | '['
                    | ']'
                    | '\\'
                    | '@'
                    | '{'
                    | '}'
                    | '<'
                    | '>'
                    | '|'
                    | '"'
                    | '\''
            )
        {
            if !prev_hyphen && !clean.is_empty() {
                clean.push('-');
                prev_hyphen = true;
            }
        } else if c == '/' {
            // Keep slashes for path-based branches (e.g. feature/my-branch), but avoid duplicate or leading slashes
            if clean.ends_with('-') {
                clean.pop();
            }
            if !clean.is_empty() && !clean.ends_with('/') {
                clean.push('/');
                prev_hyphen = true;
            }
        } else if c == '.' {
            // Avoid consecutive dots (..)
            if !clean.ends_with('.') {
                clean.push('.');
                prev_hyphen = false;
            }
        } else if c == '-' {
            if !prev_hyphen && !clean.is_empty() {
                clean.push('-');
                prev_hyphen = true;
            }
        } else {
            clean.push(c);
            prev_hyphen = false;
        }
    }

    // Trim trailing and leading hyphens, dots, and slashes
    let mut result = clean.trim_matches(['-', '.', '/']).to_string();

    // If it ends with .lock, strip it
    if result.ends_with(".lock") {
        result.truncate(result.len() - 5);
        result = result.trim_end_matches(['-', '.', '/']).to_string();
    }

    // Replace any remaining ".."
    while result.contains("..") {
        result = result.replace("..", ".");
    }

    if result.is_empty() {
        "new-branch".to_string()
    } else {
        result
    }
}

/// Validates that a branch name conforms to standard Git ref naming rules.
pub fn validate_branch_name(name: &str) -> Result<(), CoreError> {
    let trimmed = name.trim();
    if trimmed.is_empty() || trimmed != name {
        return Err(CoreError::InvalidArgument(
            "Branch name cannot be empty".to_string(),
        ));
    }
    if trimmed.starts_with('-') || trimmed.starts_with('/') {
        return Err(CoreError::InvalidArgument(
            "Branch name cannot start with '-' or '/'".to_string(),
        ));
    }
    if trimmed.ends_with('/')
        || trimmed.ends_with('.')
        || trimmed == "@"
        || trimmed
            .split('/')
            .any(|part| part.is_empty() || part.starts_with('.') || part.ends_with(".lock"))
    {
        return Err(CoreError::InvalidArgument(
            "Branch name cannot end with '/' or '.lock'".to_string(),
        ));
    }
    if trimmed.contains("..")
        || trimmed.contains('~')
        || trimmed.contains('^')
        || trimmed.contains(':')
        || trimmed.contains('?')
        || trimmed.contains('*')
        || trimmed.contains('[')
        || trimmed.contains('\\')
        || trimmed.contains("@{")
        || trimmed
            .chars()
            .any(|c| c.is_whitespace() || c.is_ascii_control())
    {
        return Err(CoreError::InvalidArgument(
            "Branch name contains prohibited characters".to_string(),
        ));
    }
    Ok(())
}
