# Catalogue authoring workflow

This directory is the reviewable source of regional content. `.yaml` files that feed the
current generator use JSON syntax, which is valid YAML and intentionally keeps the initial
toolchain dependency-free.

## Source order

1. `country_territory_assignments.yaml` records the owner-approved v1 territory index and every
   Natural Earth source map unit. It is complete as an authoring decision, but point assignment
   still requires the corresponding local, versioned boundary geometry. ISO entries are an index
   for review; map-unit entries preserve islands, dependencies and boundary pieces that lack an
   ISO code.
2. Add globally deduplicated taxon identity to `taxa.yaml`.
3. Add a taxon to a pilot's `regions/<key>/catalogue.yaml`, with encounter rarity and
   inclusion provenance. The unattended policy derives encounter-frequency tiers within each
   region and animal group from verifiable iNaturalist observations. It never treats that tier as
   conservation status, and insufficient evidence is always `unknown`.
4. Curate 10 Essentials and 5 Icons in `achievements.yaml`. Icon membership is the whole of a
   species' standing; there is no separate prestige field to keep in step with it.
5. Add only licence-verified reusable media to `media_manifest.yaml` with source URL, creator,
   licence code and taxon ID. Under the adopted target contract, the frozen media input also
   carries provider asset identity, direct thumbnail/detail variants, dimensions and validation
   metadata; see [`../docs/species_content_pipeline_plan.md`](../docs/species_content_pipeline_plan.md).

Regional source directories are discovered automatically by the workbook importer, evidence
refresh, generator and audit. Add regions gradually; draft directories remain editable and strict
generation packages only the frozen regional versions.

> **Alpha transition — 24 August 2026:** content schema v4, the authoring refresh and its release
> validators are in place. All 2,419 taxa carry complete class/order/family/genus ancestry;
> 2,193 have sourced English summaries, 355 have sourced conservation records and 381 have
> byte-validated direct thumbnail/detail photo variants. Every taxon has the compact bundled group
> silhouette fallback; specific and family silhouette roles remain incremental curation. Missing coverage renders as
> unavailable and is not a release blocker. Pilot snapshots must still be frozen before promotion.

## Supported unattended pipeline

The supported catalogue workflow is one command from the repository root:

```powershell
.\catalogue.ps1
```

On macOS/Linux use `sh ./catalogue run`. The launcher uses installed Python when available and falls
back to `uv`, including the pinned authoring dependency. A run is resumable and performs the whole
chain without prompts:

1. fetch and freeze read-only regional iNaturalist species-count evidence;
2. select the configured mammal, bird, reptile, fish and amphibian targets;
3. resolve accepted species identity, common name and class/order/family/genus ancestry;
4. assign region/group encounter-frequency tiers, leaving low evidence `unknown`;
5. promote sourced Wikipedia summaries and iNaturalist/IUCN conservation fields;
6. search Commons for exact-species landscape photos, rejecting indirect evidence, dead animals
   and specimen terms, then use compatible licensed iNaturalist landscape photos as fallback;
7. independently resolve one species→genus assignment and one family→order assignment for every
   published taxon, retaining both frozen roles when available and the five bundled group
   silhouettes as the guaranteed offline fallback;
8. validate, revise, build and atomically publish SQLite, report and Android assets; and
9. verify that every published byte matches its authored source manifest.

When iNaturalist has no preferred common name, the source deliberately stores none. Runtime queries
fall back to the scientific name; the pipeline never fabricates a vernacular name to satisfy a
schema requirement.

Provider responses, validated image metadata and failures are retained under
`review/catalogue_pipeline`, so an interrupted run continues from its frozen cache. A missing
optional description, image or silhouette becomes an explicit unavailable/fallback result; it
does not invent content. Essential taxonomy or an invalid source still fails loudly. Use
`.\catalogue.ps1 -Refresh` for a deliberate fresh provider snapshot, `-Offline` to reproduce from
frozen evidence, and `-Release` only after the selected regional catalogues have been frozen.

The launcher prints live stage progress, processed/total counters, provider cache/network counts,
elapsed-time heartbeats and a compact completion or safe-interruption message. Detailed failures
remain in `catalogues/review/catalogue_pipeline/last-run.json` instead of flooding the terminal.
Add `--json` when invoking the Python module directly if a machine-readable full result is needed.

Audit silhouette coverage at any time without changing a running pipeline or contacting a provider:

```powershell
.\audit-silhouettes.ps1
```

The audit distinguishes published assignments, complete cached provider no-matches, rejected cached
assets, request failures, resolvable-but-unpublished assets and incomplete/not-yet-attempted cache
evidence. Results are explicitly provisional until the `photos-silhouettes` stage completes.

Generate the local visual catalogue review with:

```powershell
.\catalogue-review.ps1
```

Open `review/catalogue-media-review.html` to filter all published taxa by region, group, photo and
silhouette coverage, inspect media provenance and queue bad reference photos. The page stores queued
decisions only in that browser and downloads an import-compatible JSON file; apply it deliberately
with `catalogue.ps1 import-rejections -InputFile ...`. Regenerate the page after a pipeline refresh
to review the new snapshot.

When a user deliberately exports their local Wildlife data for content review, import its durable
reference-image rejection decisions with:

```powershell
.\catalogue.ps1 import-rejections -InputFile "C:\path\to\wildlife-local-data.json"
```

The import is idempotent and provenance-preserving. The next pipeline run removes any matching
Commons or iNaturalist photo assignment, excludes it from every replacement search for that taxon,
and refuses to publish while a rejected assignment remains. No rejection leaves the device unless
the user explicitly creates and supplies the local-data export.

For a source-only change, `.\catalogue.ps1 build` validates and republishes without network access.
`.\catalogue.ps1 verify` is read-only. Android `preBuild` runs the same freshness gate and refuses
to package stale catalogue assets. Publication identity is incremented automatically only when an
authored input or pipeline policy changes.

To refresh one configured region while still validating and publishing the complete bundled pack,
use its canonical key, for example:

```powershell
.\catalogue.ps1 run -Region mediterranean_europe
```

## Legacy manual content utilities

The utilities below remain available for forensic review and one-off migrations. They are no
longer the normal publication path and are not required between unattended pipeline runs.

### Refresh species content

Install the authoring-only image dependency from `tools/requirements-content-refresh.txt`, then
refresh public iNaturalist candidates and revalidate curated media separately from the Android
build:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\refresh_catalogue_content.py --refresh
```

The command rate-limits public requests, stores deterministic compressed evidence in
`review/content_refresh/raw`, fully decodes and hashes image responses, and writes separate
description, conservation and media CSV queues. Use `--offline` to reproduce queues only from the
frozen cache. `--apply-media` publishes reviewed Commons assets, licensed exact-taxon iNaturalist
default photos and deterministic exact-name PhyloPic species→genus→family→order assignments only
when the licence permits redistribution and the entire selected set passes
strict provider, URL, MIME, dimension and checksum validation. `--apply-taxonomy` resolves exact
iNaturalist ancestors and atomically updates class/order/family/genus while rejecting inactive,
renamed or rank-changed taxa.

Editorial decisions belong in `review/content_refresh/review-decisions.yaml`. Its snapshot digest
prevents decisions from being applied to refreshed candidates accidentally. Validate decisions,
then apply them atomically:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\apply_content_review.py
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\apply_content_review.py --apply
```

Every approval or waiver requires a reviewer, review date and reason. A rejection leaves the
published source unchanged.

For an explicitly owner-approved alpha bulk import of frozen exact-taxon description and
conservation candidates, record the policy honestly and bind it to the snapshot before applying:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\apply_content_review.py --prepare-sourced-approvals --reviewer "Product owner - bulk source-policy approval" --apply
```

The recorded reason states that prose was not individually edited. Do not use this as a
substitute for editorial review in a release catalogue.

### Generate and validate directly

Use the bundled Python runtime:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py
```

The current alpha command creates `generated/catalogue.sqlite`, `generated/catalogue-report.json` and
`generated/catalogue-diff.md`. The output is deterministic for identical source files and is
always marked `draft` until strict validation passes.

`content_manifest.yaml` is the immutable publication identity. After any authoring, generator,
schema or release-status change, increment `generation_sequence` and assign a new
`generation_id` before generating. Do not use `generated_at` to order releases; it is descriptive
only. The generator rejects changed output under an existing sequence/ID, sequence rollback and
ID reuse at a later sequence.

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py --strict
```

Strict mode packages frozen catalogues only and requires at least one. Each frozen catalogue needs
exactly 10 Essentials and 5 Icons, valid regional taxon fields, and validated direct thumbnail/detail
declarations for every achievement taxon. Supplied descriptions/conservation records remain
strictly provenance-validated, while missing coverage is reported. It must pass before an Android release content pack is built. The separate Python suite
exercises deterministic output, direct-media rules, taxon-change cycles and a 30,000-taxon,
25-region scale fixture.

Run the repeatable storage/query gate after schema, indexing or media-manifest changes:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\benchmark_content_pipeline.py
```

It writes `review/content-pipeline-benchmark.json` and `.md`. The fixture stores 30,000 media asset
identities and 60,000 thumbnail/detail URLs but no image bytes. Do not replace current-region
prefetch with global prefetch: the report projects the latter beyond 1.5 GiB at current validated
image sizes. The generator reports photo, specific-silhouette and family-silhouette coverage
separately; runtime always resolves local media in specific → family → bundled-group order.

## Territory review promotion

The Natural Earth worksheet is retained as review evidence. Once its recommendations have owner
approval, rebuild the runtime authoring manifest with:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\promote_territory_assignments.py
```

Then validate the regional foundation:

```powershell
.\tools\Test-RegionalFoundation.ps1
```

## Owner-managed content workbook

Use `review/wildlife-content-manager.xlsx` as the single editing surface for all three pilot
catalogues, their 10 Essentials and 5 Icons, XP awards and level thresholds. The reference name
columns are deliberately not imported: the taxon ID is the stable identity.

For the complete everyday workflow, including read-only iNaturalist candidate and rarity refresh,
see [`CONTENT_MANAGER_GUIDE.md`](CONTENT_MANAGER_GUIDE.md).

You may add, remove or reorder a regional catalogue row, provided its taxon ID already exists in
`taxa.yaml`. Every pilot always requires exactly 10 Essentials and 5 Icons.

First validate the workbook without changing sources:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_content_workbook.py
```

When validation is clean, apply the change. The importer writes the three regional catalogues,
the achievements source, the versioned `progression.yaml`, and the generated Kotlin progression
configuration as one reviewed change.

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_content_workbook.py --apply
```

Next, increment `generation_sequence` and assign a new `generation_id` in
`content_manifest.yaml`, then generate:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py
```

The importer never changes global taxon identity or media provenance. Add those separately and
keep the workbook in git with the generated source changes so every content/balance decision is
reviewable.
