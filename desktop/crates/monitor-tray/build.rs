fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=src/monitor.rc");
    println!("cargo:rerun-if-changed=src/monitor.ico");

    #[cfg(windows)]
    {
        let Ok(manifest_dir) = std::env::var("CARGO_MANIFEST_DIR") else {
            return;
        };
        let rc_script = format!("{}/src/monitor.rc", manifest_dir);
        let Ok(out_dir) = std::env::var("OUT_DIR") else {
            return;
        };
        let res_path = format!("{}/monitor.res", out_dir);

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

        if !compiled {
            // Fallback: try rc on PATH
            if let Ok(s) = std::process::Command::new("rc.exe")
                .args(["/fo", &res_path, &rc_script])
                .status()
                && s.success()
            {
                println!("cargo:rustc-link-arg={res_path}");
            }
        }
    }
}
