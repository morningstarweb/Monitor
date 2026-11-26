#[tokio::test]
async fn challenge_zero_mock_and_physical_disks_telemetry() {
    let sampler = TelemetrySampler::new();
    let snapshot = sampler.sample_system_snapshot();

    // 1. Cooling telemetry check (active fan support or None on fanless)
    if let Some(cooling) = &snapshot.cooling {
        assert!(cooling.has_fan || cooling.is_fanless);
        if cooling.has_fan {
            assert!(cooling.fan_rpm > 0);
        }
    }

    // 2. Physical disks are enumerated from the actual OS via sysinfo::Disks
    assert!(
        !snapshot.disks.is_empty(),
        "Physical disks list must not be empty on a running Windows system"
    );

    for disk in &snapshot.disks {
        assert!(
            !disk.mount_point.is_empty(),
            "Mount point must not be empty"
        );
        assert!(
            disk.total_bytes > 0,
            "Total disk bytes must be > 0 for physical drive: {:?}",
            disk
        );
        assert!(
            disk.available_bytes <= disk.total_bytes,
            "Available bytes ({}) cannot exceed total bytes ({})",
            disk.available_bytes,
            disk.total_bytes
        );
    }

    // 3. Mount points must be sorted deterministically
    let is_sorted = snapshot
        .disks
        .windows(2)
        .all(|pair| pair[0].mount_point <= pair[1].mount_point);
    assert!(
        is_sorted,
        "Disks list must be sorted deterministically by mount_point"
    );

    // 4. Test JSON serialization and deserialization
    let serialized = serde_json::to_string(&snapshot).expect("serialize SystemSnapshot");
    let mut json_val: serde_json::Value =
        serde_json::from_str(&serialized).expect("parse JSON snapshot");

    // Verify cooling in JSON
    if let Some(cooling_val) = json_val.get("cooling") {
        if !cooling_val.is_null() {
            assert!(cooling_val.get("has_fan").is_some());
        }
    }

    // Verify disks in JSON
    let disks_val = json_val
        .get("disks")
        .expect("disks field present in JSON")
        .as_array()
        .expect("disks is array");
    assert_eq!(disks_val.len(), snapshot.disks.len());

    // 5. Boundary case: Test deserialization when disks/cooling are omitted (backward compatibility)
    if let Some(obj) = json_val.as_object_mut() {
        obj.remove("disks");
        obj.remove("cooling");
    }
    let stripped_json = serde_json::to_string(&json_val).unwrap();
    let decoded: SystemSnapshot =
        serde_json::from_str(&stripped_json).expect("deserialize legacy snapshot without disks");
    assert!(
        decoded.disks.is_empty(),
        "Disks must default to empty array when missing from JSON"
    );
    assert!(decoded.cooling.is_none());
}
