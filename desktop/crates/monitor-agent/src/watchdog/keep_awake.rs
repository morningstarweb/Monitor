use std::sync::atomic::{AtomicBool, Ordering};
use tracing::info;

static KEEP_AWAKE_ACTIVE: AtomicBool = AtomicBool::new(false);

#[cfg(windows)]
#[allow(unsafe_code)]
pub fn set_keep_awake(enabled: bool) -> bool {
    use windows_sys::Win32::System::Power::{
        ES_AWAYMODE_REQUIRED, ES_CONTINUOUS, ES_SYSTEM_REQUIRED, SetThreadExecutionState,
    };

    let flags = if enabled {
        ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_AWAYMODE_REQUIRED
    } else {
        ES_CONTINUOUS
    };

    // SAFETY: SetThreadExecutionState is a standard Win32 kernel API taking bitmask flags by value.
    // It dereferences no user pointers, mutates no heap memory, and returns 0 (NULL) on failure.
    let res = unsafe { SetThreadExecutionState(flags) };
    if res != 0 {
        let prev = KEEP_AWAKE_ACTIVE.swap(enabled, Ordering::SeqCst);
        if prev != enabled {
            info!("Windows Keep-Awake state updated: enabled={enabled}");
        }
        true
    } else {
        false
    }
}

#[cfg(not(windows))]
pub fn set_keep_awake(enabled: bool) -> bool {
    KEEP_AWAKE_ACTIVE.store(enabled, Ordering::SeqCst);
    true
}
