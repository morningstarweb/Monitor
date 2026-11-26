<#
.SYNOPSIS
    Secret scan of the working tree AND full git history using gitleaks.

.DESCRIPTION
    Required external tool: gitleaks >= 8.28 (https://github.com/gitleaks/gitleaks).
    This script never prints secret values: gitleaks runs with --redact, findings
    are written to temporary JSON reports, and only exit codes / finding counts
    are echoed. All reports are deleted afterwards.

    Fail-closed behavior:
      * exit 1  -> findings detected OR gitleaks returned a non-zero status
      * exit 2  -> tool/config missing (nothing was verified)
      * exit 0  -> clean, or explicitly skipped with -AllowMissingTool

    The allowlist is intentionally narrow and lives in .gitleaks.toml. Do not
    widen it to suppress real findings; review and remediate instead.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/scan_secrets.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/scan_secrets.ps1 -GitleaksPath C:\tools\gitleaks.exe
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [string]$GitleaksPath = "gitleaks",
    [string]$ConfigPath = ".gitleaks.toml",
    [switch]$AllowMissingTool
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $scriptDir = $PSScriptRoot
    if ([string]::IsNullOrWhiteSpace($scriptDir)) {
        $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    }
    $RepoRoot = Split-Path -Parent $scriptDir
}
$root = (Resolve-Path -LiteralPath $RepoRoot).Path
$configFile = Join-Path $root $ConfigPath
$tool = Get-Command $GitleaksPath -CommandType Application -ErrorAction SilentlyContinue

if (-not $tool) {
    $message = "gitleaks was not found ('$GitleaksPath'). No secret scan was performed."
    if ($AllowMissingTool) {
        Write-Warning "$message Explicitly skipped via -AllowMissingTool; this is NOT a clean result."
        exit 0
    }
    [Console]::Error.WriteLine("$message Install gitleaks >= 8.28 and re-run, or pass -AllowMissingTool to acknowledge an unverified state.")
    exit 2
}

if (-not (Test-Path -LiteralPath $configFile -PathType Leaf)) {
    [Console]::Error.WriteLine("gitleaks config not found: $configFile")
    exit 2
}

$reportDir = Join-Path ([System.IO.Path]::GetTempPath()) ("monitor-gitleaks-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null

function Invoke-Gitleaks {
    param([string[]]$Arguments)
    # Output is captured and intentionally NOT echoed: it may contain sensitive context.
    $prevPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $null = & $tool.Source @Arguments 2>&1
        return $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $prevPreference
    }
}

function Get-FindingCount {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return 0 }
    try {
        $raw = Get-Content -LiteralPath $Path -Raw
        if ([string]::IsNullOrWhiteSpace($raw)) { return 0 }
        $parsed = $raw | ConvertFrom-Json
        if ($null -eq $parsed) { return 0 }
        return @($parsed).Count
    }
    catch {
        return -1
    }
}

try {
    $common = @("--config", $configFile, "--redact", "--no-banner", "--report-format", "json")
    $worktreeReport = Join-Path $reportDir "worktree.json"
    $historyReport = Join-Path $reportDir "history.json"

    # 1) Working tree only (files as they exist now).
    $worktreeArgs = @("dir", $root) + $common + @("--report-path", $worktreeReport)
    $worktreeCode = Invoke-Gitleaks -Arguments $worktreeArgs

    # 2) Full history. --log-opts=--all ensures all refs, not just HEAD.
    $historyArgs = @("detect", "--source", $root) + $common + @("--log-opts=--all", "--report-path", $historyReport)
    $historyCode = Invoke-Gitleaks -Arguments $historyArgs

    $worktreeCount = Get-FindingCount -Path $worktreeReport
    $historyCount = Get-FindingCount -Path $historyReport

    Write-Host "gitleaks worktree : exit=$worktreeCode findings=$worktreeCount"
    Write-Host "gitleaks history  : exit=$historyCode findings=$historyCount (--log-opts=--all)"

    $failed = ($worktreeCode -ne 0) -or ($historyCode -ne 0) -or ($worktreeCount -gt 0) -or ($historyCount -gt 0) -or ($worktreeCount -lt 0) -or ($historyCount -lt 0)
    if ($failed) {
        [Console]::Error.WriteLine("SECRET SCAN FAILED CLOSED. No values are printed; remediate and re-run.")
        exit 1
    }
    Write-Host "Secret scan clean (working tree and full history)."
    exit 0
}
finally {
    Remove-Item -LiteralPath $reportDir -Recurse -Force -ErrorAction SilentlyContinue
}
