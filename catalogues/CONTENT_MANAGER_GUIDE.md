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

5. If validation is clean, apply it and rebuild the local content pack:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_content_workbook.py --apply
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py
```

The importer updates the regional catalogue sources, achievements, `progression.yaml` and the
generated Android progression configuration. Review all resulting changes together before
committing.

## Packaging a reviewed catalogue for a future runtime install

Every catalogue generation also writes `generated/wildlife-content-pack.zip`. It contains the
SQLite catalogue, its report and a manifest with SHA-256 checksums. The app can validate and
atomically install this file through its internal content-pack API without touching observation
history or earned XP.

This is deliberately only a local install contract today: Wildlife does not download packs,
accept arbitrary user files through the UI, or treat a checksum as publisher authorization. A
signed release channel and download policy are separate future work.

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

## Bringing new iNaturalist candidates into a draft catalogue

When a candidate should become an editable catalogue row, the current promotion step is:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_broad_catalogues.py
```

It adds selected candidate taxa to the global taxon source and pilot drafts with `unknown`
rarity. Add the taxa you retain as rows in the workbook's **Catalogue** sheet, then make the
final catalogue and rarity decisions there. Do not use this against a frozen release without a
reviewed version bump.

## Guardrails

- iNaturalist is the biological source of truth; the workbook controls Wildlife’s collection and
  game projection only.
- The importer refuses unknown taxon IDs, duplicate entries, invalid rarity, incomplete
  checklists and non-increasing level thresholds.
- Add media provenance separately. A strict release needs licence-verified media for all
  achievement taxa.
- Catalogue updates can change projected completion but never remove historical XP or a previously
  earned, versioned achievement.
