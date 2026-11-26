param([string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'bin'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$destination = Join-Path $outputPath 'Monitor.exe'
$running = Get-Process -Name monitor,monitor-agent,monitor-tray,monitor-ui -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -and (Split-Path -Parent $_.Path) -eq $outputPath }
if ($running) {
    throw "Exit Monitor from its tray menu before packaging into $outputPath."
}

Push-Location (Join-Path $projectRoot 'desktop')
try {
    & cargo build --release --locked -p monitor-tray --bin monitor
    if ($LASTEXITCODE -ne 0) { throw "Release build failed ($LASTEXITCODE)." }
} finally { Pop-Location }

# Only these known legacy application binaries are retired. User data is in LOCALAPPDATA.
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$source = Join-Path $projectRoot 'desktop\target\release\monitor.exe'
$stagingPath = Join-Path $outputPath '.monitor-release.tmp'
try {
    Copy-Item -LiteralPath $source -Destination $stagingPath -Force
    Move-Item -LiteralPath $stagingPath -Destination $destination -Force
} finally {
    if (Test-Path -LiteralPath $stagingPath -PathType Leaf) {
        Remove-Item -LiteralPath $stagingPath -Force
    }
}
foreach ($legacyName in @('monitor-agent.exe', 'monitor-tray.exe', 'monitor-ui.exe')) {
    $legacyPath = Join-Path $outputPath $legacyName
    if (Test-Path -LiteralPath $legacyPath -PathType Leaf) {
        Remove-Item -LiteralPath $legacyPath -Force
    }
}
$binary = Get-Item -LiteralPath $destination
Write-Host "Packaged $($binary.FullName) ($([Math]::Round($binary.Length / 1MB, 1)) MB)"
Get-FileHash -LiteralPath $destination -Algorithm SHA256
