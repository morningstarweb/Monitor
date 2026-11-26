# REL-01: single reproducible verification gate.
# Records exact commands, HEAD, toolchain versions and exit codes.
# Never signs, deploys, publishes or modifies source.
param(
  [string]$LogDir = (Join-Path $env:TEMP 'monitor-verify-gates')
)

$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $LogDir 'verify_gates.log'

function Write-Log($line) {
  Write-Output $line
  Add-Content -Path $log -Value $line
}

function Invoke-Gate($name, $command, $workingDirectory) {
  Write-Log "=== GATE $name :: $command ==="
  $output = & cmd /c "cd /d `"$workingDirectory`" && $command 2>&1"
  $output | ForEach-Object { Add-Content -Path $log -Value $_ }
  $code = $LASTEXITCODE
  Write-Log "=== EXIT $name :: $code ==="
  if ($code -ne 0) {
    throw "Gate $name failed with exit code $code"
  }
}

Set-Location $root
Write-Log "HEAD: $(git rev-parse HEAD)"
Write-Log "BRANCH: $(git branch --show-current)"
Write-Log "RUSTC: $(rustc --version)"
Write-Log "NODE: $(node --version)"
Write-Log "PYTHON: $(python --version)"
Write-Log "LOGDIR: $LogDir"

try {
  Invoke-Gate 'rust-fmt' 'cargo fmt --all -- --check' (Join-Path $root 'desktop')
  Invoke-Gate 'rust-check' 'cargo check --workspace --all-targets --offline' (Join-Path $root 'desktop')
  Invoke-Gate 'rust-clippy' 'cargo clippy --workspace --all-targets --offline -- -D warnings' (Join-Path $root 'desktop')
  Invoke-Gate 'rust-tests' 'cargo test --workspace --all-targets --offline' (Join-Path $root 'desktop')
  Invoke-Gate 'cloud-check' 'npm run check:all' (Join-Path $root 'cloud')
  Invoke-Gate 'python-e2e' 'python tests/e2e/runner.py' $root
  Write-Log 'ALL GATES PASSED'
} catch {
  Write-Log "GATE FAILURE: $($_.Exception.Message)"
  exit 1
}
