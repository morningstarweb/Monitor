<#
.SYNOPSIS
    Optional, honest local profiling wrapper for Monitor.

.DESCRIPTION
    By default this script ONLY records which profiling methods/commands SHOULD
    be used and writes a Markdown report in which every metric says
    "NOT MEASURED". It never invents numbers.

    If you explicitly pass -Run together with -Command (and optional -Arguments),
    that one command is executed and its wall-clock time is measured with a real
    Stopwatch. No CPU, memory, network, frame-rate or cloud metric is inferred
    from that wall-clock time; those remain "NOT MEASURED".

    Typical use:
      powershell -ExecutionPolicy Bypass -File scripts/profile_resources.ps1
      powershell -ExecutionPolicy Bypass -File scripts/profile_resources.ps1 -OutputDir C:\temp\prof -Run -Command cargo -Arguments @("test","--workspace","--all-targets")

    This script makes no network calls and does not upload anything.
#>
[CmdletBinding()]
param(
    [string]$OutputDir = (Join-Path $env:TEMP "monitor-profiling"),
    [string]$Command = "",
    [string[]]$Arguments = @(),
    [switch]$Run
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Run -and [string]::IsNullOrWhiteSpace($Command)) {
    [Console]::Error.WriteLine("-Run requires -Command. Nothing was executed.")
    exit 2
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$reportPath = Join-Path $OutputDir "profiling-plan.md"
$stamp = (Get-Date).ToUniversalTime().ToString("o")

function Test-Tool {
    param([string]$Name)
    return [bool](Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue)
}

$methods = @(
    [pscustomobject]@{
        Area = "Build time"
        Method = "cargo build --release --timings (run from desktop/)"
        Tool = "cargo $(if (Test-Tool 'cargo') { '(available)' } else { '(NOT available)' })"
        Metric = "crate-by-crate compile duration"
        Status = "NOT MEASURED"
    },
    [pscustomobject]@{
        Area = "Command latency"
        Method = "hyperfine --warmup 5 '<binary> <args>'"
        Tool = "hyperfine $(if (Test-Tool 'hyperfine') { '(available)' } else { '(NOT available)' })"
        Metric = "p50/p95 wall clock"
        Status = "NOT MEASURED"
    },
    [pscustomobject]@{
        Area = "Host CPU / context switches"
        Method = "perf stat / Windows Performance Recorder"
        Tool = "external profiler; availability not probed"
        Metric = "CPU cycles, context switches, cache misses"
        Status = "NOT MEASURED"
    },
    [pscustomobject]@{
        Area = "Desktop process resources"
        Method = "Get-Counter -Counter '\Process(monitor-tray)\% Processor Time,...' or dotnet-counters"
        Tool = "Get-Counter $(if (Test-Tool 'Get-Counter') { '(available)' } else { '(NOT available)' })"
        Metric = "CPU %, working set, handle count"
        Status = "NOT MEASURED"
    },
    [pscustomobject]@{
        Area = "Android UI smoothness"
        Method = "Android Studio Profiler / adb shell dumpsys gfxinfo <package> framestats"
        Tool = "adb $(if (Test-Tool 'adb') { '(available)' } else { '(NOT available)' })"
        Metric = "frame time, jank"
        Status = "NOT MEASURED"
    },
    [pscustomobject]@{
        Area = "Cloud Worker CPU"
        Method = "Cloudflare dashboard / wrangler tail / provider metrics (owner account only)"
        Tool = "no credentials used by this script"
        Metric = "Worker CPU ms, DO duration, D1 reads/writes"
        Status = "NOT MEASURED"
    },
    [pscustomobject]@{
        Area = "Transport round-trip"
        Method = "instrumented protocol v2 PING/PONG over each active path"
        Tool = "product instrumentation; not executed here"
        Metric = "per-path RTT"
        Status = "NOT MEASURED"
    }
)

$executedRow = $null
if ($Run) {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $process = Start-Process -FilePath $Command -ArgumentList $Arguments -Wait -PassThru -NoNewWindow
        $timer.Stop()
        $executedRow = [pscustomobject]@{
            Area = "Operator-supplied command"
            Method = "$Command $($Arguments -join ' ')"
            Tool = "Stopwatch (real measurement)"
            Metric = "wall clock only"
            Status = ("MEASURED: {0:N0} ms (exit {1})" -f $timer.Elapsed.TotalMilliseconds, $process.ExitCode)
        }
    }
    catch {
        $timer.Stop()
        [Console]::Error.WriteLine("Failed to execute -Command: $($_.Exception.Message)")
        exit 2
    }
}

$lines = @(
    "# Monitor local profiling record",
    "",
    "- Generated (UTC): $stamp",
    "- Repository: Monitor (profiling methods only; no data uploaded)",
    "- Network access: none",
    "",
    "All entries below are **NOT MEASURED** unless explicitly marked otherwise.",
    "A 'NOT MEASURED' status means no number was collected and none may be quoted as evidence.",
    "",
    "| Area | Method / command | Tool status | Metric | Status |",
    "| --- | --- | --- | --- | --- |"
)
foreach ($row in $methods) {
    $lines += "| $($row.Area) | $($row.Method) | $($row.Tool) | $($row.Metric) | $($row.Status) |"
}
if ($executedRow) {
    $lines += "| $($executedRow.Area) | $($executedRow.Method) | $($executedRow.Tool) | $($executedRow.Metric) | $($executedRow.Status) |"
}
$lines += @(
    "",
    "## Rules for using measurements",
    "",
    "1. Record tool version, host/OS, commit, and exact command alongside any number.",
    "2. Never convert a wall-clock measurement into CPU, memory, or latency claims.",
    "3. Cloud metrics require owner-controlled credentials; this script never reads them.",
    "4. Real-device Android and real-network transport measurements remain external blockers.",
    ""
)

$lines -join "`n" | Set-Content -LiteralPath $reportPath -Encoding UTF8

Write-Host "Profiling record written to: $reportPath"
if ($executedRow) {
    Write-Host $executedRow.Status
}
else {
    Write-Host "No command executed. All metrics remain NOT MEASURED."
}
exit 0
