pub fn hex_encode(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        use std::fmt::Write;
        let _ = write!(s, "{:02x}", b);
    }
    s
}

pub fn hex_decode(s: &str) -> Result<Vec<u8>, CoreError> {
    if !s.len().is_multiple_of(2) {
        return Err(CoreError::InvalidArgument("Invalid hex length".to_string()));
    }
    let mut bytes = Vec::with_capacity(s.len() / 2);
    for i in (0..s.len()).step_by(2) {
        let byte = u8::from_str_radix(&s[i..i + 2], 16)
            .map_err(|_| CoreError::InvalidArgument("Invalid hex character".to_string()))?;
        bytes.push(byte);
    }
    Ok(bytes)
}
