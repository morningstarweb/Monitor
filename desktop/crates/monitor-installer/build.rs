use sha2::{Digest, Sha256};

fn fail(message: &str) -> ! {
    println!("cargo:warning={message}");
    std::process::exit(1);
}

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=src/installer.rc");
    println!("cargo:rerun-if-changed=src/monitor.ico");
    println!("cargo:rerun-if-changed=payload.zip");
    println!("cargo:rerun-if-changed=payload.expected.sha256");
    println!("cargo:rerun-if-env-changed=MONITOR_RELEASE_BUILD");

    // WIN-01: payload.zip is an explicit, hash-attested build input. A clean
    // checkout carries a tiny development fixture so workspace checks compile;
    // a release build must carry the real payload and must never ship a fixture.
    let Ok(manifest_dir) = std::env::var("CARGO_MANIFEST_DIR") else {
        fail("CARGO_MANIFEST_DIR is not set");
    };
    let payload_path = format!("{manifest_dir}/payload.zip");
    let bytes = match std::fs::read(&payload_path) {
        Ok(bytes) => bytes,
        Err(error) => fail(&format!(
            "installer payload missing at {payload_path}: {error}. Generate it before building."
        )),
    };
    let expected_path = format!("{manifest_dir}/payload.expected.sha256");
    let expected_file = match std::fs::read_to_string(&expected_path) {
        Ok(value) => value,
        Err(error) => fail(&format!("payload hash manifest unreadable: {error}")),
    };
    let expected = expected_file
        .split_whitespace()
        .next()
        .unwrap_or_default()
        .to_ascii_lowercase();
    let digest = Sha256::digest(&bytes);
    let mut actual = String::with_capacity(64);
    {
        use std::fmt::Write;
        for byte in digest {
            let _ = write!(actual, "{byte:02x}");
        }
    }
    if actual != expected {
        fail(&format!(
            "installer payload hash mismatch: expected {expected}, found {actual}"
        ));
    }
    if std::env::var("MONITOR_RELEASE_BUILD").as_deref() == Ok("1")
        && bytes
            .windows(19)
            .any(|window| window == b"DEVELOPMENT_FIXTURE")
    {
        fail("refusing to package a development fixture into a release installer");
    }

    #[cfg(windows)]
    {
        let Ok(manifest_dir) = std::env::var("CARGO_MANIFEST_DIR") else {
            return;
        };
        let rc_script = format!("{}/src/installer.rc", manifest_dir);
        let Ok(out_dir) = std::env::var("OUT_DIR") else {
            return;
        };
        let res_path = format!("{}/installer.res", out_dir);

        let rc_candidates = [
            r"C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\rc.exe",
            r"C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x64\rc.exe",
            r"C:\Program Files (x86)\Windows Kits\10\bin\10.0.19041.0\x64\rc.exe",
        ];

        let mut compiled = false;
        for rc_exe in &rc_candidates {
            if std::path::Path::new(rc_exe).exists() {
                let status = std::process::Command::new(rc_exe)
                    .args(["/fo", &res_path, &rc_script])
                    .status();
                if let Ok(s) = status
                    && s.success()
                {
                    println!("cargo:rustc-link-arg={res_path}");
                    compiled = true;
                    break;
                }
            }
        }

        if !compiled
            && let Ok(s) = std::process::Command::new("rc.exe")
                .args(["/fo", &res_path, &rc_script])
                .status()
            && s.success()
        {
            println!("cargo:rustc-link-arg={res_path}");
        }
    }
}
