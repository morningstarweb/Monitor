#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use super::stream::{
    READ_CHUNK_BYTES, append_bounded, read_bounded, reserve_output_bytes, stream_bounded_lines,
};
use super::{MAX_LINE_BYTES, MAX_OUTPUT_BYTES};
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

#[test]
fn output_budget_is_shared_and_never_exceeded() {
    let used = AtomicUsize::new(0);
    let overflow = AtomicBool::new(false);
    assert!(reserve_output_bytes(1000, &used, &overflow));
    assert_eq!(used.load(Ordering::SeqCst), 1000);
    assert!(reserve_output_bytes(
        MAX_OUTPUT_BYTES - 1000,
        &used,
        &overflow
    ));
    assert_eq!(used.load(Ordering::SeqCst), MAX_OUTPUT_BYTES);
    assert!(!reserve_output_bytes(1, &used, &overflow));
    assert!(overflow.load(Ordering::SeqCst));
    assert_eq!(used.load(Ordering::SeqCst), MAX_OUTPUT_BYTES);
}

#[test]
fn append_bounded_cuts_on_utf8_boundaries() {
    let mut target = String::new();
    let mut budget = 3;
    append_bounded(&mut target, "é©abc", &mut budget);
    assert_eq!(target, "é");
    assert_eq!(budget, 1);
    append_bounded(&mut target, "more", &mut budget);
    assert_eq!(target, "ém");
    assert_eq!(budget, 0);
}

#[tokio::test]
async fn read_bounded_enforces_byte_cap() {
    let data = vec![b'x'; 100_000];
    let mut cursor = std::io::Cursor::new(data);
    let mut output = Vec::new();
    read_bounded(&mut cursor, &mut output, 1024).await.unwrap();
    assert_eq!(output.len(), 1024);
}

#[tokio::test]
async fn read_bounded_honours_small_byte_caps() {
    let data = vec![b'z'; 64 * 1024];
    for cap in [1usize, 17, 8191] {
        let mut output = Vec::new();
        read_bounded(&mut std::io::Cursor::new(&data), &mut output, cap)
            .await
            .unwrap();
        assert_eq!(output.len(), cap, "cap {cap} was exceeded");
    }
}

#[tokio::test]
async fn many_flooded_lines_each_stay_within_the_byte_cap() {
    let mut data = Vec::new();
    for _ in 0..50 {
        data.extend(std::iter::repeat_n(b'q', MAX_LINE_BYTES * 2));
        data.push(b'\n');
    }
    let overflow = AtomicBool::new(false);
    let mut lines = Vec::new();
    stream_bounded_lines(std::io::Cursor::new(data), &overflow, |line| {
        lines.push(line)
    })
    .await;
    assert_eq!(lines.len(), 50);
    assert!(lines.iter().all(|line| line.len() <= MAX_LINE_BYTES));
    assert!(overflow.load(Ordering::SeqCst));
}

#[tokio::test]
async fn newline_less_flood_is_bounded_at_source() {
    let data = vec![b'x'; 1_000_000];
    let overflow = AtomicBool::new(false);
    let mut lines = Vec::new();
    stream_bounded_lines(std::io::Cursor::new(data), &overflow, |line| {
        lines.push(line)
    })
    .await;

    assert_eq!(lines.len(), 1);
    assert_eq!(lines[0].len(), MAX_LINE_BYTES);
    assert!(overflow.load(Ordering::SeqCst));
}

#[tokio::test]
async fn bounded_lines_preserve_short_lines_and_split_long_ones() {
    let mut data = b"alpha\n".to_vec();
    data.extend(std::iter::repeat_n(b'y', MAX_LINE_BYTES * 2));
    data.extend_from_slice(b"\nomega\n");
    let overflow = AtomicBool::new(false);
    let mut lines = Vec::new();
    stream_bounded_lines(std::io::Cursor::new(data), &overflow, |line| {
        lines.push(line)
    })
    .await;

    assert_eq!(lines.len(), 3);
    assert_eq!(lines[0], "alpha");
    assert_eq!(lines[1].len(), MAX_LINE_BYTES);
    assert_eq!(lines[2], "omega");
    assert!(overflow.load(Ordering::SeqCst));
}

#[tokio::test]
async fn bounded_lines_are_utf8_safe_for_split_multibyte_characters() {
    let mut data = vec![b'a'; READ_CHUNK_BYTES - 1];
    data.extend_from_slice("é".as_bytes());
    data.extend(std::iter::repeat_n(b'z', MAX_LINE_BYTES));
    let overflow = AtomicBool::new(false);
    let mut lines = Vec::new();
    stream_bounded_lines(std::io::Cursor::new(data), &overflow, |line| {
        lines.push(line)
    })
    .await;

    assert_eq!(lines.len(), 1);
    assert!(std::str::from_utf8(lines[0].as_bytes()).is_ok());
    assert!(lines[0].len() <= MAX_LINE_BYTES);
    assert!(overflow.load(Ordering::SeqCst));
}
