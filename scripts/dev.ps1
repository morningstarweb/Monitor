param(
    [switch]$Release,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host "   MONITOR // TRAY & SYSTEM DEV SERVER LAUNCHER        " -ForegroundColor Cyan
Write-Host "=======================================================`n" -ForegroundColor Cyan

# 1. Terminate any running Monitor background instances to prevent port 9480 / 9489 collisions
Write-Host "Checking for existing Monitor processes..." -ForegroundColor DarkGray
$running = Get-Process -Name monitor,Monitor,monitor-tray,monitor-agent -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "Terminating $($running.Count) existing Monitor process(es)..." -ForegroundColor Yellow
    Stop-Process -Name monitor,Monitor,monitor-tray,monitor-agent -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 600
}

# 2. Launch tray development engine
$cargoArgs = @("run", "--manifest-path", (Join-Path $projectRoot "desktop\Cargo.toml"), "-p", "monitor-tray", "--bin", "monitor")
if ($Release) {
    $cargoArgs += "--release"
    Write-Host "Starting Monitor Tray in RELEASE mode..." -ForegroundColor Green
} else {
    Write-Host "Starting Monitor Tray in DEVELOPMENT mode (cargo run)..." -ForegroundColor Green
}

Write-Host "• Port 9480: Monitor Agent & Telemetry REST/WebSocket" -ForegroundColor DarkGray
Write-Host "• Port 9489: Single Instance Mutex" -ForegroundColor DarkGray
Write-Host "• System Tray: Monitor Icon (Click to toggle 100% Native GPUI Glass Flyout)" -ForegroundColor Cyan
Write-Host "`nPress [Ctrl+C] in this terminal to stop the dev server.`n" -ForegroundColor DarkYellow

Push-Location (Join-Path $projectRoot "desktop")
try {
    & cargo @cargoArgs
} finally {
    Pop-Location
    Write-Host "`nDev server stopped." -ForegroundColor DarkGray
}
