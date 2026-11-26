param(
    [switch]$Uninstall,
    [switch]$Release
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Write-Host "`n=======================================================" -ForegroundColor Magenta
Write-Host "   MONITOR // 100% NATIVE GLASS INSTALLER DEV LAUNCHER " -ForegroundColor Magenta
Write-Host "=======================================================`n" -ForegroundColor Magenta

# 1. Terminate any previous installer instances
$running = Get-Process -Name monitor-installer,uninstall -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "Terminating active installer processes..." -ForegroundColor Yellow
    Stop-Process -Name monitor-installer,uninstall -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 400
}

# 2. Build cargo launch arguments
$cargoArgs = @("run", "--manifest-path", (Join-Path $projectRoot "desktop\Cargo.toml"), "-p", "monitor-installer", "--bin", "monitor-installer")

if ($Release) {
    $cargoArgs += "--release"
}

if ($Uninstall) {
    $cargoArgs += "--"
    $cargoArgs += "--uninstall"
    Write-Host "Starting Monitor Uninstaller mode..." -ForegroundColor Yellow
} else {
    Write-Host "Starting Monitor Custom Glass Installer UI..." -ForegroundColor Green
}

Write-Host "• 100% Native GPUI Kit Glass Window (800x540, Draggable)" -ForegroundColor DarkGray
Write-Host "• Deep Dark Acrylic Aesthetics with Real Scrollable Legal Docs" -ForegroundColor DarkGray
Write-Host "• 6-Step Smart Upgrade Workflow with Solid Zstd Compression & MinGit" -ForegroundColor DarkGray
Write-Host "`nPress [Ctrl+C] to close or use the window titlebar buttons.`n" -ForegroundColor DarkYellow

Push-Location (Join-Path $projectRoot "desktop")
try {
    & cargo @cargoArgs
} finally {
    Pop-Location
    Write-Host "`nInstaller dev session closed." -ForegroundColor DarkGray
}
