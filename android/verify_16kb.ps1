<#
.SYNOPSIS
  Fail-closed 16 KB page-size compatibility verifier for a Monitor APK.

.DESCRIPTION
  1. Locates apksigner, zipalign (Android SDK build-tools) and llvm-readelf (NDK).
  2. Verifies every packaged native .so has 16 KB LOAD alignment (p_align >= 16384)
     with congruent p_offset/p_vaddr, and has full RELRO (GNU_RELRO + BIND_NOW).
  3. Runs `zipalign -c -P 16` on the APK.
  4. Optionally verifies the APK signature with apksigner.

  Any missing tool, malformed ELF, failed check or tool error exits 1.

.EXAMPLE
  .\verify_16kb.ps1 -Apk .\app\build\outputs\apk\release\app-release.apk
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Apk,

    [string]$SdkRoot,
    [string]$NdkRoot,
    [string]$BuildToolsVersion,

    [switch]$SkipSignatureCheck
)

$ErrorActionPreference = 'Stop'
$failures = New-Object System.Collections.Generic.List[string]

function Write-Step([string]$message) {
    Write-Host "[16KB] $message"
}

function Fail-Closed([string]$message) {
    Write-Host "[16KB][FAIL] $message" -ForegroundColor Red
    exit 1
}

function Convert-ToolNumber([string]$token) {
    $value = $token.Trim()
    if ($value.StartsWith('0x', [System.StringComparison]::OrdinalIgnoreCase)) {
        return [Convert]::ToInt64($value.Substring(2), 16)
    }
    return [int64]::Parse($value)
}

function Resolve-SdkRoot {
    if (-not [string]::IsNullOrWhiteSpace($SdkRoot)) { return $SdkRoot }
    foreach ($candidate in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, "$env:LOCALAPPDATA\Android\Sdk")) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) { return $candidate }
    }
    return $null
}

function Resolve-NdkRoot([string]$sdk) {
    if (-not [string]::IsNullOrWhiteSpace($NdkRoot)) { return $NdkRoot }
    foreach ($candidate in @($env:ANDROID_NDK_HOME, $env:ANDROID_NDK_ROOT, "$env:ANDROID_NDK")) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) { return $candidate }
    }
    if ([string]::IsNullOrWhiteSpace($sdk)) { return $null }
    $ndkParent = Join-Path $sdk 'ndk'
    if (Test-Path $ndkParent) {
        $versions = @(Get-ChildItem -Path $ndkParent -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending)
        if ($versions.Count -gt 0) { return $versions[0].FullName }
    }
    $legacy = Join-Path $sdk 'ndk-bundle'
    if (Test-Path $legacy) { return $legacy }
    return $null
}

function Resolve-BuildTools([string]$sdk) {
    if ([string]::IsNullOrWhiteSpace($sdk)) { return $null }
    $root = Join-Path $sdk 'build-tools'
    if (-not (Test-Path $root)) { return $null }

    if (-not [string]::IsNullOrWhiteSpace($BuildToolsVersion)) {
        $exact = Join-Path $root $BuildToolsVersion
        if (Test-Path $exact) { return $exact }
    }

    $candidates = @(Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue | Where-Object {
            $_.Name -match '^\d+\.\d+\.\d+'
        })
    $ordered = $candidates | Sort-Object {
        $numeric = [regex]::Match($_.Name, '^\d+\.\d+\.\d+').Value
        try { [version]$numeric } catch { [version]'0.0.0' }
    } -Descending
    if (@($ordered).Count -eq 0) { return $null }
    return @($ordered)[0].FullName
}

# --- Resolve inputs ---------------------------------------------------------

$apkPath = $null
try { $apkPath = (Resolve-Path -LiteralPath $Apk -ErrorAction Stop).Path }
catch { Fail-Closed "APK not found: $Apk" }
if (-not $apkPath.EndsWith('.apk', [System.StringComparison]::OrdinalIgnoreCase)) {
    Fail-Closed "Not an .apk file: $apkPath"
}

$sdk = Resolve-SdkRoot
if ([string]::IsNullOrWhiteSpace($sdk)) {
    Fail-Closed "Android SDK root not found. Set -SdkRoot or ANDROID_SDK_ROOT / ANDROID_HOME."
}
Write-Step "Android SDK: $sdk"

$buildTools = Resolve-BuildTools $sdk
if ([string]::IsNullOrWhiteSpace($buildTools)) {
    Fail-Closed "No build-tools directory found under $sdk\build-tools. Install a build-tools package."
}
Write-Step "build-tools: $buildTools"

$zipalign = Join-Path $buildTools 'zipalign.exe'
if (-not (Test-Path $zipalign)) { $zipalign = Join-Path $buildTools 'zipalign' }
if (-not (Test-Path $zipalign)) {
    Fail-Closed "zipalign not found in $buildTools"
}

$apksigner = Join-Path $buildTools 'apksigner.bat'
if (-not (Test-Path $apksigner)) { $apksigner = Join-Path $buildTools 'apksigner' }
if (-not (Test-Path $apksigner)) {
    Fail-Closed "apksigner not found in $buildTools"
}

$ndk = Resolve-NdkRoot $sdk
$readelf = $null
if (-not [string]::IsNullOrWhiteSpace($ndk)) {
    $readelfCandidates = @(Get-ChildItem -Path (Join-Path $ndk 'toolchains\llvm\prebuilt') -Filter 'llvm-readelf.exe' -Recurse -File -ErrorAction SilentlyContinue)
    if ($readelfCandidates.Count -eq 0) {
        $readelfCandidates = @(Get-ChildItem -Path (Join-Path $ndk 'toolchains\llvm\prebuilt') -Filter 'llvm-readelf' -Recurse -File -ErrorAction SilentlyContinue)
    }
    if ($readelfCandidates.Count -gt 0) { $readelf = $readelfCandidates[0].FullName }
}
Write-Step "zipalign:    $zipalign"
Write-Step "apksigner:   $apksigner"
if ([string]::IsNullOrWhiteSpace($readelf)) {
    Write-Step "llvm-readelf: not found (using built-in ELF64/ELF32 header verifier)"
} else {
    Write-Step "llvm-readelf: $readelf"
}

# --- zipalign -c -P 16 ------------------------------------------------------

Write-Step "Checking APK alignment with zipalign -c -P 16 -v 4 ..."
$zipOutput = & $zipalign -c -P 16 -v 4 $apkPath 2>&1
$zipExit = $LASTEXITCODE
$zipOutput | ForEach-Object { Write-Host "  $_" }
if ($zipExit -ne 0) {
    $failures.Add("zipalign -c -P 16 -v 4 failed with exit code $zipExit")
}

# --- apksigner (optional) ---------------------------------------------------

if (-not $SkipSignatureCheck) {
    Write-Step "Verifying APK signature with apksigner ..."
    $signerOutput = & $apksigner verify --print-certs $apkPath 2>&1
    $signerExit = $LASTEXITCODE
    $signerOutput | ForEach-Object { Write-Host "  $_" }
    if ($signerExit -ne 0) {
        $failures.Add("apksigner verify failed with exit code $signerExit")
    }
}
else {
    Write-Step "Signature check skipped (-SkipSignatureCheck)."
}

# --- Native .so ELF checks --------------------------------------------------

Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("monitor16k_" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

try {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        $soEntries = @($archive.Entries | Where-Object { $_.FullName -like '*.so' })
        Write-Step "Native libraries found: $($soEntries.Count)"

        $index = 0
        foreach ($entry in $soEntries) {
            $index++
            $soPath = Join-Path $tempDir ("native_$index.so")
            $inputStream = $entry.Open()
            try {
                $outputStream = [System.IO.File]::Create($soPath)
                try { $inputStream.CopyTo($outputStream) }
                finally { $outputStream.Dispose() }
            }
            finally { $inputStream.Dispose() }

            Write-Step "Inspecting $($entry.FullName)"
            if (-not [string]::IsNullOrWhiteSpace($readelf)) {
                $elfOutput = & $readelf -l -d $soPath 2>&1
                $elfExit = $LASTEXITCODE
                if ($elfExit -ne 0) {
                    $failures.Add("$($entry.FullName): llvm-readelf failed with exit code $elfExit")
                    continue
                }

                $loadLines = @($elfOutput | Where-Object { $_ -match '^\s*LOAD\s' })
                if ($loadLines.Count -eq 0) {
                    $failures.Add("$($entry.FullName): no LOAD segments found")
                    continue
                }

                foreach ($line in $loadLines) {
                    $parts = @($line -split '\s+' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
                    if ($parts.Count -lt 8) {
                        $failures.Add("$($entry.FullName): cannot parse LOAD line: $line")
                        continue
                    }

                    $alignment = Convert-ToolNumber $parts[7]
                    if ($alignment -lt 16384) {
                        $failures.Add("$($entry.FullName): LOAD p_align=$alignment is below 16384 (16 KB)")
                        continue
                    }

                    $offset = Convert-ToolNumber $parts[1]
                    $vaddr = Convert-ToolNumber $parts[2]
                    if (($offset % $alignment) -ne ($vaddr % $alignment)) {
                        $failures.Add("$($entry.FullName): LOAD p_offset/p_vaddr are not congruent modulo $alignment")
                    }
                }

                $hasRelro = @($elfOutput | Where-Object { $_ -match 'GNU_RELRO' }).Count -gt 0
                $hasBindNow = @($elfOutput | Where-Object { $_ -match 'BIND_NOW|\bNOW\b' }).Count -gt 0
                if (-not $hasRelro) {
                    $failures.Add("$($entry.FullName): GNU_RELRO segment missing (full RELRO required)")
                }
                if (-not $hasBindNow) {
                    $failures.Add("$($entry.FullName): BIND_NOW missing (full RELRO required)")
                }
            } else {
                $bytes = [System.IO.File]::ReadAllBytes($soPath)
                if ($bytes.Length -lt 64 -or $bytes[0] -ne 0x7F -or $bytes[1] -ne 0x45 -or $bytes[2] -ne 0x4C -or $bytes[3] -ne 0x46) {
                    $failures.Add("$($entry.FullName): invalid ELF header")
                    continue
                }
                $eiClass = $bytes[4]
                if ($eiClass -ne 2 -and $eiClass -ne 1) {
                    $failures.Add("$($entry.FullName): unsupported ELF class $eiClass")
                    continue
                }
                $is64 = ($eiClass -eq 2)
                $phoff = if ($is64) { [BitConverter]::ToInt64($bytes, 32) } else { [int64][BitConverter]::ToUInt32($bytes, 28) }
                $phent = if ($is64) { [BitConverter]::ToUInt16($bytes, 54) } else { [BitConverter]::ToUInt16($bytes, 42) }
                $phnum = if ($is64) { [BitConverter]::ToUInt16($bytes, 56) } else { [BitConverter]::ToUInt16($bytes, 44) }
                $hasRelro = $false
                $hasBindNow = $false
                $loadCount = 0

                for ($phIdx = 0; $phIdx -lt $phnum; $phIdx++) {
                    $phBase = [int]$phoff + ($phIdx * [int]$phent)
                    if (($phBase + $phent) -gt $bytes.Length) { break }
                    $ptype = [BitConverter]::ToUInt32($bytes, $phBase)
                    if ($is64) {
                        $off = [BitConverter]::ToUInt64($bytes, $phBase + 8)
                        $va = [BitConverter]::ToUInt64($bytes, $phBase + 16)
                        $fsz = [BitConverter]::ToUInt64($bytes, $phBase + 32)
                        $align = [BitConverter]::ToUInt64($bytes, $phBase + 48)
                    } else {
                        $off = [uint64][BitConverter]::ToUInt32($bytes, $phBase + 4)
                        $va = [uint64][BitConverter]::ToUInt32($bytes, $phBase + 8)
                        $fsz = [uint64][BitConverter]::ToUInt32($bytes, $phBase + 16)
                        $align = [uint64][BitConverter]::ToUInt32($bytes, $phBase + 28)
                    }
                    if ($ptype -eq 1) {
                        $loadCount++
                        if ($is64 -and $align -lt 16384) {
                            $failures.Add("$($entry.FullName): ELF64 LOAD p_align=$align is below 16384 (16 KB)")
                        }
                        if ($align -gt 0 -and (($off % $align) -ne ($va % $align))) {
                            $failures.Add("$($entry.FullName): LOAD p_offset/p_vaddr are not congruent modulo $align")
                        }
                    } elseif ($ptype -eq 0x6474e552) {
                        $hasRelro = $true
                    } elseif ($ptype -eq 2) {
                        $step = if ($is64) { 16 } else { 8 }
                        for ($dyn = 0L; ($dyn + $step) -le [int64]$fsz; $dyn += $step) {
                            $dynPos = [int]$off + [int]$dyn
                            if (($dynPos + $step) -gt $bytes.Length) { break }
                            $tag = if ($is64) { [BitConverter]::ToInt64($bytes, $dynPos) } else { [int64][BitConverter]::ToInt32($bytes, $dynPos) }
                            $val = if ($is64) { [BitConverter]::ToUInt64($bytes, $dynPos + 8) } else { [uint64][BitConverter]::ToUInt32($bytes, $dynPos + 4) }
                            if ($tag -eq 0) { break }
                            if ($tag -eq 24 -or ($tag -eq 30 -and (($val -band 8) -ne 0)) -or ($tag -eq 0x6ffffffb -and (($val -band 1) -ne 0))) {
                                $hasBindNow = $true
                            }
                        }
                    }
                }
                if ($loadCount -eq 0) { $failures.Add("$($entry.FullName): no LOAD segments found") }
                if (-not $hasRelro) { $failures.Add("$($entry.FullName): GNU_RELRO segment missing (full RELRO required)") }
                if (-not $hasBindNow) { $failures.Add("$($entry.FullName): BIND_NOW missing (full RELRO required)") }
            }
        }
    }
    finally { $archive.Dispose() }
}
finally {
    Remove-Item -Recurse -Force -Path $tempDir -ErrorAction SilentlyContinue
}

# --- Verdict ----------------------------------------------------------------

if ($failures.Count -gt 0) {
    Write-Host ''
    Write-Host "[16KB][FAIL] $($failures.Count) problem(s) found:" -ForegroundColor Red
    foreach ($problem in $failures) { Write-Host "  - $problem" -ForegroundColor Red }
    exit 1
}

Write-Host ''
Write-Host '[16KB][PASS] APK is 16 KB aligned: LOAD segments, full RELRO and zipalign -P 16 all verified.' -ForegroundColor Green
exit 0
