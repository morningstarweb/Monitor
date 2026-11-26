# scripts/run_e2e_tests.ps1 — Executes the 4-Tier Automated E2E Test Suite
param(
    [string]$Tier = "all",
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot

$pythonExe = "python"
$runnerScript = Join-Path $RootDir "tests\e2e\runner.py"

$arguments = @($runnerScript, "--tier", $Tier)
if ($Verbose) {
    $arguments += "-v"
}

& $pythonExe @arguments
exit $LASTEXITCODE
