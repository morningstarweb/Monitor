#[test]
fn test_merge_pull_request_argument_validation() {
    let temp = tempfile::tempdir().unwrap();
    // PR number 0 rejected
    let err0 = merge_pull_request(temp.path(), 0, Some("merge")).unwrap_err();
    assert!(matches!(err0, CoreError::InvalidArgument(_)));

    // Invalid method rejected
    let err_invalid = merge_pull_request(temp.path(), 1, Some("cherry-pick")).unwrap_err();
    assert!(matches!(err_invalid, CoreError::InvalidArgument(_)));
}
