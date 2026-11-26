use super::*;
use monitor_core::DiffLineType;

#[test]
fn test_parse_unified_diff() {
    let sample_diff = b"diff --git a/src/main.rs b/src/main.rs
index e69de29..49c8d1 100644
--- a/src/main.rs
+++ b/src/main.rs
@@ -1,3 +1,4 @@
 fn main() {
-    println!(\"Hello\");
+    let greeting = \"Hello World\";
+    println!(\"{greeting}\");
 }";

    let res = parse_unified_diff("src/main.rs", sample_diff);
    assert!(!res.is_binary);
    assert!(!res.is_truncated);
    assert_eq!(res.insertions, 2);
    assert_eq!(res.deletions, 1);
    assert_eq!(res.hunks.len(), 1);

    let hunk = &res.hunks[0];
    assert_eq!(hunk.old_start, 1);
    assert_eq!(hunk.new_start, 1);
    assert_eq!(hunk.lines.len(), 5);

    assert_eq!(hunk.lines[1].line_type, DiffLineType::Deletion);
    assert_eq!(hunk.lines[1].old_line_num, Some(2));
    assert_eq!(hunk.lines[2].line_type, DiffLineType::Addition);
    assert_eq!(hunk.lines[2].new_line_num, Some(2));
}

#[test]
fn test_binary_detection() {
    let binary_bytes = b"GIF89a\x00\x00\x01\x00";
    let res = parse_unified_diff("icon.gif", binary_bytes);
    assert!(res.is_binary);
    assert_eq!(res.hunks.len(), 0);
}
