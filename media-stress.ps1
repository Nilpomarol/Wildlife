[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $repositoryRoot
try {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    .\gradlew.bat testDebugUnitTest `
        --tests com.wildlife.feasibility.MediaPipelineStressTest `
        --tests com.wildlife.feasibility.MediaPrefetchStoreTest `
        --tests com.wildlife.feasibility.PublishedMediaStoreTest `
        --tests com.wildlife.feasibility.ObservationDensityStoreTest `
        --tests com.wildlife.feasibility.CatalogueLifecycleTest `
        --tests com.wildlife.feasibility.ui.screens.speciesdetail.SpeciesDetailProjectionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
