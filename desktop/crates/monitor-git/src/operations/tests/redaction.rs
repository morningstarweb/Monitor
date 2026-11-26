#[cfg(test)]
mod redaction_tests {
    use super::redact_secrets;

    #[test]
    fn bearer_value_and_url_password_never_escape_redaction() {
        let output = redact_secrets(
            "Authorization: Bearer secret.jwt/value\nhttps://user:password@example.com/repo.git ghp_abc123",
        );
        for secret in ["secret.jwt/value", "password", "user:", "ghp_abc123"] {
            assert!(!output.contains(secret));
        }
        assert!(output.contains("@example.com/repo.git"));
        assert_eq!(redact_secrets("normal output"), "normal output");
    }
}
