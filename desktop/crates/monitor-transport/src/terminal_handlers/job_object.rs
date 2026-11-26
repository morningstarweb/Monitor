use std::collections::HashMap;
use std::ffi::c_void;
use std::sync::{Arc, LazyLock, Mutex};
use windows_sys::Win32::Foundation::{CloseHandle, HANDLE};
use windows_sys::Win32::System::JobObjects::{
    AssignProcessToJobObject, CreateJobObjectW, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE,
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION, JobObjectExtendedLimitInformation,
    SetInformationJobObject, TerminateJobObject,
};
use windows_sys::Win32::System::Threading::{OpenProcess, PROCESS_SET_QUOTA, PROCESS_TERMINATE};

#[derive(Debug)]
pub(super) struct ChildJob(HANDLE);

// SAFETY: Win32 kernel Job Handles can be shared/dropped from any thread and are closed once on Drop.
unsafe impl Send for ChildJob {}
unsafe impl Sync for ChildJob {}

impl ChildJob {
    pub(super) fn attach_pid(pid: u32) -> Option<Self> {
        unsafe {
            let job = CreateJobObjectW(core::ptr::null(), core::ptr::null());
            if job.is_null() {
                return None;
            }
            let mut info: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = core::mem::zeroed();
            info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            let set_ok = SetInformationJobObject(
                job,
                JobObjectExtendedLimitInformation,
                (&raw const info).cast::<c_void>(),
                core::mem::size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>() as u32,
            );
            if set_ok == 0 {
                let _ = CloseHandle(job);
                return None;
            }
            let proc_handle = OpenProcess(PROCESS_SET_QUOTA | PROCESS_TERMINATE, 0, pid);
            if proc_handle.is_null() {
                let _ = CloseHandle(job);
                return None;
            }
            let assign_ok = AssignProcessToJobObject(job, proc_handle);
            let _ = CloseHandle(proc_handle);
            if assign_ok == 0 {
                let _ = CloseHandle(job);
                return None;
            }
            Some(Self(job))
        }
    }

    pub(super) fn terminate_all(&self) {
        unsafe {
            let _ = TerminateJobObject(self.0, 1);
        }
    }
}

impl Drop for ChildJob {
    fn drop(&mut self) {
        unsafe {
            let _ = TerminateJobObject(self.0, 1);
            let _ = CloseHandle(self.0);
        }
    }
}

pub(super) static ACTIVE_TERMINAL_JOBS: LazyLock<Mutex<HashMap<String, Arc<ChildJob>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));
