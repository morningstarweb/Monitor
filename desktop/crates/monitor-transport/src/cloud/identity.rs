/// RFC 7636 Base64-URL encoder without padding for PKCE code verifiers and challenges.
fn base64_url_encode(data: &[u8]) -> String {
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    let mut result = String::with_capacity((data.len() * 4).div_ceil(3));
    for chunk in data.chunks(3) {
        let b0 = chunk[0];
        let b1 = if chunk.len() > 1 { chunk[1] } else { 0 };
        let b2 = if chunk.len() > 2 { chunk[2] } else { 0 };

        result.push(ALPHABET[(b0 >> 2) as usize] as char);
        result.push(ALPHABET[(((b0 & 0x03) << 4) | (b1 >> 4)) as usize] as char);
        if chunk.len() > 1 {
            result.push(ALPHABET[(((b1 & 0x0f) << 2) | (b2 >> 6)) as usize] as char);
        }
        if chunk.len() > 2 {
            result.push(ALPHABET[(b2 & 0x3f) as usize] as char);
        }
    }
    result
}

/// Generates a cryptographically random RFC 7636 code verifier and S256 code challenge.
pub fn generate_pkce_verifier_and_challenge() -> (String, String) {
    let random_bytes: [u8; 32] = rand::random();
    let verifier = base64_url_encode(&random_bytes);
    let digest = Sha256::digest(verifier.as_bytes());
    let challenge = base64_url_encode(&digest);
    (verifier, challenge)
}

/// Obtains or creates a persistent workstation identifier.
pub fn get_or_create_device_id(storage: &Storage) -> String {
    if let Ok(Some(existing)) = storage.get_setting("machine_device_id")
        && !existing.is_empty()
    {
        return existing;
    }

    let new_id = format!("win_{}", uuid::Uuid::new_v4().simple());
    let _ = storage.set_setting("machine_device_id", &new_id);
    new_id
}
