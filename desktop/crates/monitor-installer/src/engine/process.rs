use std::fs::OpenOptions;
use std::path::Path;
use std::process::Command;
use std::time::Duration;

#[cfg(windows)]
use super::resolve_windows_system_exe;

/// Terminates any running Monitor host binary (and bundled MinGit processes strictly inside `install_dir`).
pub fn terminate_running_monitor(install_dir: Option<&Path>) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;

        let taskkill_exe = resolve_windows_system_exe("taskkill.exe");
        let initial_pids = collect_matching_process_pids(install_dir);
        if !initial_pids.is_empty() {
            for pid in &initial_pids {
                let _ = Command::new(&taskkill_exe)
                    .args(["/PID", &pid.to_string(), "/T"])
                    .creation_flags(CREATE_NO_WINDOW)
                    .output();
            }
            std::thread::sleep(Duration::from_millis(350));

            terminate_processes_matching(install_dir);

            let remaining_pids = collect_matching_process_pids(install_dir);
            for pid in &remaining_pids {
                let _ = Command::new(&taskkill_exe)
                    .args(["/F", "/PID", &pid.to_string(), "/T"])
                    .creation_flags(CREATE_NO_WINDOW)
                    .output();
            }
            std::thread::sleep(Duration::from_millis(250));
        }

        if let Some(dir) = install_dir {
            wait_for_target_unlocked(dir);
        }
    }
    #[cfg(not(windows))]
    {
        let _ = install_dir;
        let _ = Command::new("pkill").args(["-f", "monitor"]);
    }
}

#[cfg(windows)]
fn wait_for_target_unlocked(install_dir: &Path) {
    for candidate in [
        install_dir.join("Monitor.exe"),
        install_dir.join("uninstall.exe"),
    ] {
        if !candidate.is_file() {
            continue;
        }
        for _ in 0..10 {
            match OpenOptions::new().read(true).write(true).open(&candidate) {
                Ok(file) => {
                    drop(file);
                    break;
                }
                Err(_) => std::thread::sleep(Duration::from_millis(120)),
            }
        }
    }
}

#[cfg(windows)]
fn collect_matching_process_pids(install_dir: Option<&Path>) -> Vec<u32> {
    let mut pids = Vec::new();
    for_each_matching_process(install_dir, |pid, _| pids.push(pid));
    pids
}

#[cfg(windows)]
fn terminate_processes_matching(install_dir: Option<&Path>) {
    use windows::Win32::System::Threading::{TerminateProcess, WaitForSingleObject};

    for_each_matching_process(install_dir, |_pid, proc_handle| unsafe {
        let _ = TerminateProcess(proc_handle, 0);
        let _ = WaitForSingleObject(proc_handle, 2000);
    });
}

#[cfg(windows)]
fn for_each_matching_process<F>(install_dir: Option<&Path>, mut visitor: F)
where
    F: FnMut(u32, windows::Win32::Foundation::HANDLE),
{
    use windows::Win32::Foundation::CloseHandle;
    use windows::Win32::System::Diagnostics::ToolHelp::{
        CreateToolhelp32Snapshot, PROCESSENTRY32W, Process32FirstW, Process32NextW,
        TH32CS_SNAPPROCESS,
    };
    use windows::Win32::System::Threading::{
        OpenProcess, PROCESS_NAME_FORMAT, PROCESS_QUERY_LIMITED_INFORMATION, PROCESS_SYNCHRONIZE,
        PROCESS_TERMINATE, QueryFullProcessImageNameW,
    };
    use windows::core::PWSTR;

    let current_pid = std::process::id();
    let normalized_dir = install_dir
        .and_then(|p| {
            std::fs::canonicalize(p)
                .ok()
                .or_else(|| Some(p.to_path_buf()))
        })
        .map(|p| {
            let mut s = p
                .to_string_lossy()
                .trim_start_matches(r"\\?\")
                .to_ascii_lowercase();
            if !s.ends_with('\\') {
                s.push('\\');
            }
            s
        });

    let Ok(snapshot) = (unsafe { CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0) }) else {
        return;
    };

    let mut entry = PROCESSENTRY32W {
        dwSize: std::mem::size_of::<PROCESSENTRY32W>() as u32,
        ..Default::default()
    };

    if unsafe { Process32FirstW(snapshot, &mut entry) }.is_ok() {
        loop {
            let pid = entry.th32ProcessID;
            if pid != 0
                && pid != current_pid
                && let Ok(proc_handle) = unsafe {
                    OpenProcess(
                        PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_TERMINATE | PROCESS_SYNCHRONIZE,
                        false,
                        pid,
                    )
                }
            {
                let mut buf = [0u16; 1024];
                let mut len = buf.len() as u32;
                let queried = unsafe {
                    QueryFullProcessImageNameW(
                        proc_handle,
                        PROCESS_NAME_FORMAT(0),
                        PWSTR::from_raw(buf.as_mut_ptr()),
                        &mut len,
                    )
                };
                if queried.is_ok() {
                    let image_path = String::from_utf16_lossy(&buf[..len as usize])
                        .trim_start_matches(r"\\?\")
                        .to_ascii_lowercase();
                    let is_match = match normalized_dir.as_ref() {
                        Some(dir_prefix) => {
                            image_path.starts_with(dir_prefix)
                                && !image_path.ends_with(r"\uninstall.exe")
                                && !image_path.ends_with(r"\monitor-installer.exe")
                        }
                        None => image_path.ends_with(r"\monitor\monitor.exe"),
                    };
                    if is_match {
                        visitor(pid, proc_handle);
                    }
                }
                unsafe {
                    let _ = CloseHandle(proc_handle);
                }
            }
            if unsafe { Process32NextW(snapshot, &mut entry) }.is_err() {
                break;
            }
        }
    }

    unsafe {
        let _ = CloseHandle(snapshot);
    }
}
