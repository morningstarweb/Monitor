#requires -Version 5.1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$gitVersion = '2.56.1.windows.1'
$releaseTag = 'v2.56.1.windows.1'
$archiveName = 'MinGit-2.56.1-64-bit.zip'
$downloadUrl = "https://github.com/git-for-windows/git/releases/download/$releaseTag/$archiveName"
$expectedSha256 = '4A92850B6E917DA4FD010669B614B55DCCF5B0EC333A534CC4BE4700DFEFDAFD'

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$toolsRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'installer\tools'))
$gitRoot = Join-Path $toolsRoot 'git'
$gitExecutable = Join-Path $gitRoot 'cmd\git.exe'
$toolsPrefix = $toolsRoot.TrimEnd([char[]]@('\', '/')) + [System.IO.Path]::DirectorySeparatorChar

function Get-ToolsChildPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($script:toolsPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside installer/tools: $fullPath"
    }
    return $fullPath
}

function Test-MatchingGitVersion {
    param([Parameter(Mandatory = $true)][string]$Executable)

    if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) {
        return $false
    }

    try {
        $output = & $Executable --version 2>$null
        return ($LASTEXITCODE -eq 0) -and (($output -join "`n").Trim() -eq "git version $gitVersion")
    } catch {
        return $false
    }
}

if (Test-MatchingGitVersion -Executable $gitExecutable) {
    Write-Host "MinGit $gitVersion is already prepared."
    return
}

New-Item -ItemType Directory -Path $toolsRoot -Force | Out-Null

$archivePath = Join-Path ([System.IO.Path]::GetTempPath()) "Monitor-$archiveName"
$stagePath = Get-ToolsChildPath (Join-Path $toolsRoot ('.git-stage-' + [guid]::NewGuid().ToString('N')))
$backupPath = Get-ToolsChildPath (Join-Path $toolsRoot ('.git-backup-' + [guid]::NewGuid().ToString('N')))
$archiveHash = $null

try {
    if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
        $archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
    }

    if ($archiveHash -ne $expectedSha256) {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath -UseBasicParsing
        $archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
    }

    if ($archiveHash -ne $expectedSha256) {
        throw "MinGit SHA-256 verification failed. Expected $expectedSha256, received $archiveHash."
    }

    Expand-Archive -LiteralPath $archivePath -DestinationPath $stagePath -Force
    $stagedGit = Join-Path $stagePath 'cmd\git.exe'
    if (-not (Test-Path -LiteralPath $stagedGit -PathType Leaf)) {
        throw 'The verified MinGit archive does not contain cmd\git.exe.'
    }

    $stagedVersion = & $stagedGit --version 2>$null
    if (($LASTEXITCODE -ne 0) -or (($stagedVersion -join "`n").Trim() -ne "git version $gitVersion")) {
        throw "The extracted MinGit version did not match $gitVersion."
    }

    $hadPreviousRuntime = Test-Path -LiteralPath $gitRoot
    if ($hadPreviousRuntime) {
        Move-Item -LiteralPath (Get-ToolsChildPath $gitRoot) -Destination $backupPath
    }

    try {
        Move-Item -LiteralPath $stagePath -Destination $gitRoot
    } catch {
        if ($hadPreviousRuntime -and (Test-Path -LiteralPath $backupPath) -and -not (Test-Path -LiteralPath $gitRoot)) {
            Move-Item -LiteralPath $backupPath -Destination $gitRoot
        }
        throw
    }

    if ($hadPreviousRuntime -and (Test-Path -LiteralPath $backupPath)) {
        Remove-Item -LiteralPath (Get-ToolsChildPath $backupPath) -Recurse -Force
    }

    Write-Host "Prepared MinGit $gitVersion at $gitRoot."
} finally {
    if (Test-Path -LiteralPath $stagePath) {
        Remove-Item -LiteralPath (Get-ToolsChildPath $stagePath) -Recurse -Force
    }
    if ((Test-Path -LiteralPath $backupPath) -and (Test-Path -LiteralPath $gitRoot)) {
        Remove-Item -LiteralPath (Get-ToolsChildPath $backupPath) -Recurse -Force
    }
}
