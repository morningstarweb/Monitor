<#
.SYNOPSIS
    Fast Automated Android Deployment Script for Monitor (OxygenOS / Modern Android).
.DESCRIPTION
    Builds (optional), optimizes ADB install flags, bypasses repeated "Continue" permission
    popups on OxygenOS / ColorOS, grants all runtime permissions automatically (-g),
    and starts the app instantly.
#>

param(
    [switch]$SkipBuild,
    [string]$DeviceSerial
)

$ErrorActionPreference = "Stop"
$WorkspaceDir = Split-Path -Parent $PSScriptRoot
$ApkPath = Join-Path $WorkspaceDir "android\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " Monitor Android Fast Deploy (OxygenOS Optimized)      " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Build APK if needed
if (-not $SkipBuild) {
    Write-Host "`n[1/4] Building Android debug APK..." -ForegroundColor Yellow
    Push-Location (Join-Path $WorkspaceDir "android")
    try {
        & .\gradlew.bat assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $ApkPath)) {
    throw "APK file not found at: $ApkPath. Run with Gradle first."
}

# 2. Check Device Connection
Write-Host "`n[2/4] Checking connected ADB devices..." -ForegroundColor Yellow
$adbArgs = if ($DeviceSerial) { @("-s", $DeviceSerial) } else { @() }
$devicesOutput = & adb @adbArgs devices
Write-Host $devicesOutput

$deviceLines = ($devicesOutput -split "`r?`n") | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of" }
if ($deviceLines.Count -eq 0) {
    Write-Host "`n[!] No active ADB device found." -ForegroundColor Red
    Write-Host "Tip: Connect your phone via USB or Wireless Debugging (adb connect <ip>:<port>)." -ForegroundColor Gray
    Write-Host "Make sure 'Developer options > USB debugging' is enabled." -ForegroundColor Gray
    exit 1
}

# 3. Disable OxygenOS / Package Verifier Prompts on Target Device
Write-Host "`n[3/4] Configuring device to bypass verification prompts..." -ForegroundColor Yellow
try {
    & adb @adbArgs shell settings put global verifier_verify_adb_installs 0
    & adb @adbArgs shell settings put global package_verifier_enable 0
    Write-Host "  -> Verifier prompt bypass flags set successfully." -ForegroundColor Green
} catch {
    Write-Host "  -> Note: Verifier settings write skipped (may require root or developer mode)." -ForegroundColor Gray
}

# 4. Silent Reinstall with Runtime Permissions Granted (-g -r -d -t)
Write-Host "`n[4/4] Installing APK and launching Monitor..." -ForegroundColor Yellow
Write-Host "  Installing with auto-grant permissions (-g -r -d -t)..." -ForegroundColor Gray
& adb @adbArgs install -r -d -t -g $ApkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "  -> Installation complete!" -ForegroundColor Green
    Write-Host "  -> Launching app.monitor.MainActivity..." -ForegroundColor Cyan
    & adb @adbArgs shell am start -n app.monitor/.MainActivity
    Write-Host "`n[SUCCESS] Monitor is running on your phone!" -ForegroundColor Green
} else {
    Write-Host "`n[!] ADB install returned error code $LASTEXITCODE" -ForegroundColor Red
    Write-Host "Tip: If OxygenOS still blocked the install, enable 'Developer options > Disable permission monitoring' in your phone settings." -ForegroundColor Yellow
}
