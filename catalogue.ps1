[CmdletBinding()]
param(
    [ValidateSet('run', 'build', 'verify', 'import-rejections')]
    [string]$Command = 'run',
    [switch]$Offline,
    [switch]$Refresh,
    [switch]$Release,
    [switch]$Json,
    [string]$InputFile,
    [string]$Region
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $repositoryRoot
try {
    $arguments = @('-m', 'tools.catalogue', $Command)
    if ($Offline) { $arguments += '--offline' }
    if ($Refresh) { $arguments += '--refresh' }
    if ($Release) { $arguments += '--release' }
    if ($Json) { $arguments += '--json' }
    if ($InputFile) { $arguments += @('--input', $InputFile) }
    if ($Region) { $arguments += @('--region', $Region) }
    $python = Get-Command python -ErrorAction SilentlyContinue
    $pythonReady = $false
    if ($python) {
        & $python.Source -c 'from PIL import Image' *> $null
        $pythonReady = $LASTEXITCODE -eq 0
    }
    if ($pythonReady) {
        & $python.Source @arguments
    } else {
        $uv = Get-Command uv -ErrorAction SilentlyContinue
        if (-not $uv) {
            throw 'Python 3.11+ with tools/requirements-content-refresh.txt installed, or uv, is required to run the catalogue pipeline.'
        }
        & $uv.Source run --with-requirements tools/requirements-content-refresh.txt python @arguments
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
