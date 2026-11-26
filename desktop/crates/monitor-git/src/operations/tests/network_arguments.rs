#[cfg(test)]
mod argument_tests {
    use super::validate_network_arguments;

    #[test]
    fn network_arguments_reject_options_refspecs_and_transport_helpers() {
        for remote in [
            "",
            "--upload-pack=evil",
            "ext::anything",
            "file:///tmp/repo",
            ".",
            "https://host/repo",
            "origin\n",
        ] {
            assert!(validate_network_arguments(Some(remote), None).is_err());
        }
        for branch in ["--force", ":main", "main:other", " main ", "feat/../main"] {
            assert!(validate_network_arguments(Some("origin"), Some(branch)).is_err());
        }
        assert!(validate_network_arguments(Some("origin"), Some("feature/safe-name")).is_ok());
        assert!(validate_network_arguments(None, Some("main")).is_err());
    }
}
