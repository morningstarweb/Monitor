use monitor_core::CoreError;
use windows_sys::Win32::Foundation::LocalFree;
use windows_sys::Win32::Security::Cryptography::{
    CRYPT_INTEGER_BLOB, CRYPTPROTECT_UI_FORBIDDEN, CRYPTPROTECTMEMORY_SAME_LOGON, CryptProtectData,
    CryptUnprotectData, CryptUnprotectMemory,
};

const BLOCK_BYTES: usize = 16;

/// Protects secrets persistently across reboots for the current Windows user
/// using DPAPI `CryptProtectData` (`AUD-008`).
pub(super) fn protect(plaintext: &[u8]) -> Result<Vec<u8>, CoreError> {
    let len = u32::try_from(plaintext.len())
        .map_err(|_| CoreError::InvalidArgument("Secret is too large for DPAPI".to_string()))?;
    let mut input = plaintext.to_vec();
    let in_blob = CRYPT_INTEGER_BLOB {
        cbData: len,
        pbData: input.as_mut_ptr(),
    };
    let mut out_blob = CRYPT_INTEGER_BLOB {
        cbData: 0,
        pbData: core::ptr::null_mut(),
    };
    // SAFETY: `in_blob` points to valid heap memory for the synchronous call;
    // `out_blob` receives a Win32 LocalAlloc buffer that we copy then free via `LocalFree`.
    let ok = unsafe {
        CryptProtectData(
            &in_blob,
            core::ptr::null(),
            core::ptr::null(),
            core::ptr::null_mut(),
            core::ptr::null(),
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut out_blob,
        )
    };
    if ok == 0 || out_blob.pbData.is_null() {
        return Err(CoreError::Internal(
            "Windows DPAPI CryptProtectData failed to protect secret".to_string(),
        ));
    }
    let protected = unsafe {
        let slice = core::slice::from_raw_parts(out_blob.pbData, out_blob.cbData as usize);
        let copied = slice.to_vec();
        let _ = LocalFree(out_blob.pbData.cast::<core::ffi::c_void>());
        copied
    };
    Ok(protected)
}

pub(super) fn unprotect_persistent(blob: &[u8]) -> Result<Vec<u8>, CoreError> {
    let len = u32::try_from(blob.len())
        .map_err(|_| CoreError::InvalidArgument("DPAPI secret is oversized".to_string()))?;
    let mut input = blob.to_vec();
    let in_blob = CRYPT_INTEGER_BLOB {
        cbData: len,
        pbData: input.as_mut_ptr(),
    };
    let mut out_blob = CRYPT_INTEGER_BLOB {
        cbData: 0,
        pbData: core::ptr::null_mut(),
    };
    // SAFETY: `in_blob` points to `input`; `out_blob` is freed immediately via `LocalFree`.
    let ok = unsafe {
        CryptUnprotectData(
            &in_blob,
            core::ptr::null_mut(),
            core::ptr::null(),
            core::ptr::null_mut(),
            core::ptr::null(),
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut out_blob,
        )
    };
    if ok == 0 || out_blob.pbData.is_null() {
        return Err(CoreError::Internal(
            "Windows DPAPI CryptUnprotectData failed to unprotect secret".to_string(),
        ));
    }
    let plain = unsafe {
        let slice = core::slice::from_raw_parts(out_blob.pbData, out_blob.cbData as usize);
        let copied = slice.to_vec();
        let _ = LocalFree(out_blob.pbData.cast::<core::ffi::c_void>());
        copied
    };
    Ok(plain)
}

/// Legacy `CryptProtectMemory` reader for migrating keys written in the current logon session.
pub(super) fn unprotect_legacy_memory(blob: &[u8]) -> Result<Vec<u8>, CoreError> {
    if blob.is_empty() || !blob.len().is_multiple_of(BLOCK_BYTES) {
        return Err(CoreError::InvalidArgument(
            "Invalid DPAPI-protected secret length".to_string(),
        ));
    }
    let mut plain = blob.to_vec();
    let ok = unsafe {
        CryptUnprotectMemory(
            plain.as_mut_ptr().cast::<core::ffi::c_void>(),
            plain.len() as u32,
            CRYPTPROTECTMEMORY_SAME_LOGON,
        )
    };
    if ok == 0 {
        return Err(CoreError::Internal(
            "Legacy session DPAPI key cannot be decrypted after reboot".to_string(),
        ));
    }
    let len = u32::from_le_bytes(
        plain
            .get(..4)
            .ok_or_else(|| CoreError::InvalidArgument("Truncated DPAPI secret".to_string()))?
            .try_into()
            .map_err(|_| CoreError::InvalidArgument("Truncated DPAPI secret".to_string()))?,
    ) as usize;
    let end = 4usize.saturating_add(len);
    if end > plain.len() {
        return Err(CoreError::InvalidArgument(
            "DPAPI secret length exceeds the protected blob".to_string(),
        ));
    }
    Ok(plain[4..end].to_vec())
}
