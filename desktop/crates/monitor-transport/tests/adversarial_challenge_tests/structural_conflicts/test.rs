#[tokio::test]
async fn test_adversarial_structural_conflicts() {
    for method in ["merge", "squash", "rebase"] {
        verify_add_add_conflict(method);
    }
    for method in ["merge", "squash", "rebase"] {
        verify_modify_delete_conflict(method);
    }
}
