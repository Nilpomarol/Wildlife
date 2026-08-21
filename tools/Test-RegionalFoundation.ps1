param(
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$catalogues = Join-Path $root 'catalogues'
$regionsFile = Join-Path $catalogues 'regions.yaml'
$assignmentsFile = Join-Path $catalogues 'country_territory_assignments.yaml'
$policyFile = Join-Path $catalogues 'region_assignment_policy.yaml'
$boundaryManifestFile = Join-Path $catalogues 'boundaries/generated/regional-boundaries-v1.manifest.json'

function Fail([string]$message) { throw "Regional foundation validation failed: $message" }

@($regionsFile, $assignmentsFile, $policyFile) | ForEach-Object {
    if (-not (Test-Path -LiteralPath $_)) { Fail "Missing required source file: $_" }
}

$expectedKeys = @(
    'mediterranean_europe', 'temperate_europe', 'boreal_europe', 'eastern_europe_steppe',
    'siberia_boreal_asia', 'maghreb_sahara', 'sahel', 'west_central_tropical_africa',
    'east_africa', 'southern_africa', 'madagascar_west_indian_ocean', 'middle_east_arabia',
    'central_asia', 'indian_subcontinent', 'temperate_east_asia', 'mainland_southeast_asia',
    'insular_southeast_asia', 'australasia', 'new_zealand_pacific',
    'temperate_boreal_north_america', 'mesoamerica', 'caribbean',
    'tropical_north_south_america_amazon', 'andes_southern_south_america'
)
$regionKeys = @([regex]::Matches((Get-Content -Raw $regionsFile), '(?m)^  - key: ([a-z0-9_]+)$') | ForEach-Object { $_.Groups[1].Value })
if ($regionKeys.Count -ne 24) { Fail "Expected 24 region keys, found $($regionKeys.Count)." }
if (($regionKeys | Sort-Object -Unique).Count -ne $regionKeys.Count) { Fail 'Duplicate region key.' }
if (Compare-Object $expectedKeys $regionKeys) { Fail 'Region keys differ from the adopted 24-region contract.' }

$assignmentManifest = Get-Content -Raw $assignmentsFile | ConvertFrom-Json
$assignmentRows = @($assignmentManifest.assignments)
if (($assignmentRows.Territory | Sort-Object -Unique).Count -ne $assignmentRows.Count) { Fail 'A country or territory has multiple regional assignments.' }
$unknownRegion = $assignmentRows | Where-Object { $_.Region -notin $regionKeys } | Select-Object -First 1
if ($unknownRegion) { Fail "Unknown region '$($unknownRegion.Region)' for $($unknownRegion.Territory)." }
$mapUnitRows = @($assignmentManifest.map_unit_assignments)
if ($mapUnitRows.Count -eq 0) { Fail 'Missing source map-unit assignments.' }
if (($mapUnitRows.map_unit | Sort-Object -Unique).Count -ne $mapUnitRows.Count) { Fail 'A source map unit has multiple assignments.' }
$invalidMapUnit = $mapUnitRows | Where-Object {
    ($_.region -and $_.region -notin $regionKeys) -or
    ($_.assignment_result -and $_.assignment_result -notin @('marine_worldwide', 'unsupported_for_regional_progression')) -or
    (-not $_.region -and -not $_.assignment_result)
} | Select-Object -First 1
if ($invalidMapUnit) { Fail "Invalid source map-unit assignment '$($invalidMapUnit.map_unit)'." }

if (-not (Test-Path -LiteralPath $boundaryManifestFile)) { Fail 'Missing generated local boundary manifest.' }
$boundaryManifest = Get-Content -Raw $boundaryManifestFile | ConvertFrom-Json
if ($boundaryManifest.boundary_version -ne $assignmentManifest.boundary_version) { Fail 'Generated boundary version does not match assignment manifest.' }
if ($boundaryManifest.source_feature_count -ne $mapUnitRows.Count) { Fail 'Generated boundary feature coverage does not match source map-unit assignments.' }

$policy = Get-Content -Raw $policyFile
foreach ($requiredPolicy in @(
    'nearest_country_fallback: prohibited',
    'strategy: reviewed_offshore_buffers_then_worldwide_fallback',
    'unmatched_result: marine_worldwide',
    'antarctica: unsupported_for_regional_progression'
)) {
    if ($policy -notmatch [regex]::Escape($requiredPolicy)) { Fail "Missing approved policy: $requiredPolicy" }
}

$coverageStatus = $assignmentManifest.coverage_status
if ($Strict -and $coverageStatus -ne 'complete') {
    Fail "Territory manifest is '$coverageStatus'; strict catalogue generation requires 'complete'."
}

Write-Output "Regional foundation valid: 24 regions, $($assignmentRows.Count) reviewed territory assignments, coverage=$coverageStatus."
