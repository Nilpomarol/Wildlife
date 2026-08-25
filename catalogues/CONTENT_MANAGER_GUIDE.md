# Content manager quick guide

Use [`review/wildlife-content-manager.xlsx`](review/wildlife-content-manager.xlsx) to make the
final editorial decisions for the three pilot regions: their catalogues, encounter rarity, 10
Essentials, 5 Icons, XP values and level thresholds.

## Everyday catalogue or XP change

1. Open the workbook and read **Start here**.
2. Edit the green-input sheets:
   - **Catalogue** — add, remove or reorder a regional species; edit encounter rarity,
     seasonality and inclusion provenance. A taxon ID must already exist in `taxa.yaml`.
     The sheet's **Prestige** column is no longer read: the Legendary tier it fed always
     named exactly the region's Icons, so Icon membership now carries that meaning alone.
   - **Checklists** — set exactly 10 `essential` and 5 `icon` rows per region.
   - **XP and levels** — change rewards, enabled events, repeat-observation rewards or level
     thresholds. The pacing rows update as you change values.
3. Save the workbook.
4. Validate it before changing source files:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_content_workbook.py
```

5. If validation is clean, apply it. Then increment `generation_sequence` and assign a new
   `generation_id` in `content_manifest.yaml` before rebuilding the local content pack:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_content_workbook.py --apply
```

Edit the publication identity, then rebuild:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py
```

The importer updates the regional catalogue sources, achievements, `progression.yaml` and the
generated Android progression configuration. Review all resulting changes together before
committing.

## Importing rejected reference images

Species Detail records a source only when the user explicitly requests another reference image.
The decision stays on that device unless the user creates and supplies a structured local-data
export. Import an owner-supplied export with:

```powershell
.\catalogue.ps1 import-rejections -InputFile "C:\path\to\wildlife-local-data.json"
```

This merges new decisions into `media_rejections.yaml` without duplicating earlier imports. Review
the taxon IDs and source pages, then run the normal catalogue pipeline. Rejected Commons and
iNaturalist sources are removed from existing assignments, excluded from replacement searches and
blocked by publication validation.

## Packaging a reviewed catalogue for a future runtime install

> **Pipeline transition — 24 August 2026:** generated packs use content schema v4. The current
> three-region source has complete class/order/family/genus ancestry, 2,193 sourced English
> descriptions, 355 sourced conservation records and 381 validated reference photos. All 2,419
> taxa retain the bundled group silhouette; exact silhouettes remain incremental curation.
> Missing optional coverage is not itself a release blocker. Do not
> publish until the catalogue/checklist decisions and the remaining representative-device acceptance
> gates in [`../docs/species_content_pipeline_plan.md`](../docs/species_content_pipeline_plan.md)
> are complete.

Every catalogue generation also writes `generated/wildlife-content-pack.zip`. It contains the
SQLite catalogue, its report and a manifest with SHA-256 checksums. The app can validate and
atomically install this file through its internal content-pack API without touching observation
history or earned XP.

Publication ordering is controlled only by the positive `generation_sequence`. Every changed
source/tool/schema/release revision requires both a higher sequence and a new `generation_id`;
`generated_at` cannot promote or downgrade a pack.

This is deliberately only a local install contract today: Wildlife does not download packs,
accept arbitrary user files through the UI, or treat a checksum as publisher authorization. A
signed release channel and download policy are separate future work.

After any schema, indexing or broad media change, run `tools/benchmark_content_pipeline.py` and
review `review/content-pipeline-benchmark.md`. Published image URLs belong in the generated
manifest/database; image bytes remain in the bounded runtime cache and are prefetched only for the
current region. The app's local-data inventory reports installed generation, reference-media bytes
and queue completed/failed counts for operational diagnosis.

## Refreshing iNaturalist evidence

Wildlife uses the public iNaturalist API read-only. It does not use OAuth and never writes to
iNaturalist accounts.

Refresh all pilot evidence with one command (or pass one or more region keys to refresh only
those regions):

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\refresh_inaturalist_evidence.py --pages 3 --refresh
```

This keeps the raw candidate list and writes `review/<region>_inaturalist_evidence.csv`. The
evidence file puts candidate identity, observation count and its frequency-relative rarity
proposal beside the current editorial catalogue rarity. Increase `--pages` to sample
more than the top 200 species per animal group. To regenerate those comparison files from already
stored candidate data without network access, add `--offline`.

Treat the proposed frequency tier as evidence only: copy the values you accept into the workbook
before running the workbook importer again. This refresh never edits a catalogue, so frequency is
not biological rarity, conservation status, or a substitute for editorial judgement.

## Reviewing species descriptions, conservation and reference media

This is a separate authoring flow from regional frequency evidence:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\refresh_catalogue_content.py --refresh
```

Review the three CSV files under `review/content_refresh/`. Record approvals, rejections or waivers
in `review/content_refresh/review-decisions.yaml`, preserving the candidate snapshot digest. Every
decision requires `reviewed_by`, `reviewed_at` and `reason`; a waiver must state why compatible
content is unavailable or inappropriate.

Validate first, then promote reviewed decisions:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\apply_content_review.py
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\apply_content_review.py --apply
```

The refresh never publishes text automatically. Media promotion covers reviewed Commons pages and
compatible exact-taxon iNaturalist defaults and is atomic: any licence drift, broken URL, invalid
image or checksum failure rejects the entire apply operation. Taxonomy may be updated atomically
with `--apply-taxonomy`; inactive, renamed or rank-changed taxa stop for review. Android/Gradle
never invokes this networked tool.

The owner-directed alpha bulk source-policy path is explicit rather than silent:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\apply_content_review.py --prepare-sourced-approvals --reviewer "Product owner - bulk source-policy approval" --apply
```

It binds decisions to the frozen snapshot and records that descriptions were not individually
edited. Normal release curation should continue to use explicit per-record decisions.

## Bringing new iNaturalist candidates into a draft catalogue

When a candidate should become an editable catalogue row, the current promotion step is:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_broad_catalogues.py
```

It adds selected candidate taxa to the global taxon source and pilot drafts with `unknown`
rarity. Add the taxa you retain as rows in the workbook's **Catalogue** sheet, then make the
final catalogue and rarity decisions there. A frozen release is an immutable snapshot, not a
locked worksheet: copy/edit the draft freely, then use a reviewed version bump for promotion.

## Guardrails

- iNaturalist is the biological source of truth; the workbook controls Wildlife’s collection and
  game projection only.
- The importer refuses unknown taxon IDs, duplicate entries, invalid rarity, incomplete
  checklists and non-increasing level thresholds.
- Add media provenance separately. A strict release needs licence-verified media for all
  achievement taxa. The replacement pipeline additionally requires stable provider identity and
  validated direct thumbnail/detail URLs; a Commons/iNaturalist source page alone is insufficient.
- Expand licensed reference-photo and specific-silhouette coverage whenever practical. Coverage
  is reported, not faked with waivers; the app retains a bundled group silhouette where no more
  specific reusable asset is available.
- Catalogue updates can change projected completion but never remove historical XP or a previously
  earned, versioned achievement.
