# AND-01: fail-closed verification of Android release artifacts.
# Requires owner-provided RELEASE_SIGNER_CERT_SHA256 and an apksigner binary.
# Does not sign, deploy, publish or modify source.
param(
  [Parameter(Mandatory = $true)][string]$ApkPath,
  [string]$AabPath,
  [string]$ExpectedCertSha256 = $env:RELEASE_SIGNER_CERT_SHA256
)

if ([string]::IsNullOrWhiteSpace($ExpectedCertSha256)) {
  throw "RELEASE_SIGNER_CERT_SHA256 is not set; refusing to treat an artifact as a production release"
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
  throw "APK not found: $ApkPath"
}

$androidHome = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($androidHome)) {
  throw "ANDROID_HOME is not set; cannot locate apksigner"
}
$apksigner = Get-ChildItem -Path (Join-Path $androidHome 'build-tools') -Recurse -Filter apksigner.bat -ErrorAction SilentlyContinue |
  Sort-Object FullName -Descending | Select-Object -First 1
if (-not $apksigner) {
  throw "apksigner not found under $androidHome\build-tools"
}

& $apksigner.FullName verify --print-certs --verbose $ApkPath
if ($LASTEXITCODE -ne 0) {
  throw "apksigner rejected $ApkPath"
}

$certs = & $apksigner.FullName verify --print-certs $ApkPath 2>&1 | Out-String
$expected = $ExpectedCertSha256.Replace(':', '').Trim().ToLowerInvariant()
if (-not $certs.ToLowerInvariant().Contains($expected)) {
  throw "APK signer certificate does not match RELEASE_SIGNER_CERT_SHA256"
}

if ($AabPath -and (Test-Path -LiteralPath $AabPath)) {
  $jarsigner = Get-Command jarsigner -ErrorAction SilentlyContinue
  if (-not $jarsigner) { throw "jarsigner not found for AAB verification" }
  & jarsigner -verify -verbose -certs $AabPath
  if ($LASTEXITCODE -ne 0) { throw "jarsigner rejected $AabPath" }
}

Write-Output "ANDROID RELEASE ARTIFACTS VERIFIED (expected certificate present)"
