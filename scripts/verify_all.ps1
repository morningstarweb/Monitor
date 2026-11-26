# scripts/verify_all.ps1 — Master Verification Script for PowerShell
$ErrorActionPreference = "Stop"

# Native executables do not throw on a non-zero exit code in Windows PowerShell.
function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE"
    }
}

$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "=== MONITOR MASTER VERIFICATION SUITE (POWERSHELL) ===" -ForegroundColor Cyan

# G01: Workspace integrity
Write-Host "[G01] Checking Workspace Integrity & Git Drift..." -ForegroundColor Yellow
Invoke-Checked git @('diff', '--check')
Invoke-Checked python @('scripts/sync_brand.py', '--check')

# G02 - G05: Rust checks (run inside desktop/ directory)
if (Get-Command cargo -ErrorAction SilentlyContinue) {
    Push-Location "$RootDir\desktop"
    try {
        Write-Host "[G02] Checking Rust Formatting..." -ForegroundColor Yellow
        Invoke-Checked cargo @('fmt', '--all', '--', '--check')

        Write-Host "[G03] Running Rust Workspace Check..." -ForegroundColor Yellow
        Invoke-Checked cargo @('check', '--workspace', '--all-targets')

        Write-Host "[G04] Running Clippy..." -ForegroundColor Yellow
        Invoke-Checked cargo @('clippy', '--workspace', '--all-targets', '--all-features', '--', '-D', 'warnings')

        Write-Host "[G05] Running Rust Tests..." -ForegroundColor Yellow
        Invoke-Checked cargo @('test', '--workspace', '--all-targets')
    } finally {
        Pop-Location
    }
} else {
    throw 'Cargo is required to verify the desktop application.'
}

Push-Location "$RootDir\android"
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Users\Developer\.jdks\jdk-21.0.6+7')) {
    $env:JAVA_HOME = 'C:\Users\Developer\.jdks\jdk-21.0.6+7'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
$originalJavaToolOptions = $env:JAVA_TOOL_OPTIONS
try {
    # Keep Windows JDK Unix-domain socket paths below the OS path-length limit.
    $gradleIpc = Join-Path $RootDir 'android\.gradle\ipc'
    New-Item -ItemType Directory -Path $gradleIpc -Force | Out-Null
    $env:JAVA_TOOL_OPTIONS = "$originalJavaToolOptions -Djdk.net.unixdomain.tmpdir=`"$gradleIpc`""
    Write-Host '[Android] Building APK, running unit tests and lint...' -ForegroundColor Yellow
    Invoke-Checked '.\gradlew.bat' @('assembleDebug', 'assembleRelease', 'testDebugUnitTest', 'lintDebug', '--console=plain')
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
    Pop-Location
}

# G15 / G16: Python
Write-Host "[G16] Checking source lines of code guideline..." -ForegroundColor Yellow
Invoke-Checked python @('scripts/check_loc.py')

Write-Host "=== ALL VERIFICATION CHECKS PASSED ===" -ForegroundColor Green

