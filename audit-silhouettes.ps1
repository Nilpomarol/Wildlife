[CmdletBinding()]
param(
    [switch]$Json,
    [string]$Output
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $repositoryRoot
try {
    $arguments = @('-m', 'tools.audit_silhouette_refresh')
    if ($Json) { $arguments += '--json' }
    if ($Output) { $arguments += @('--output', $Output) }
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        & $python.Source @arguments
    } else {
        $uv = Get-Command uv -ErrorAction SilentlyContinue
        if (-not $uv) {
            throw 'Python 3.11+ or uv is required to run the silhouette audit.'
        }
        & $uv.Source run --with-requirements tools/requirements-content-refresh.txt python @arguments
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
