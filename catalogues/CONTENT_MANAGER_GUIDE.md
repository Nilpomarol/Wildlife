# Content manager quick guide

Use [`review/wildlife-content-manager.xlsx`](review/wildlife-content-manager.xlsx) to make the
final editorial decisions for the three pilot regions: their catalogues, encounter rarity, 10
Essentials, 5 Icons, XP values and level thresholds.

## Everyday catalogue or XP change

1. Open the workbook and read **Start here**.
2. Edit the green-input sheets:
   - **Catalogue** — add, remove or reorder a regional species; edit encounter rarity, prestige,
     seasonality and inclusion provenance. A taxon ID must already exist in `taxa.yaml`.
   - **Checklists** — set exactly 10 `essential` and 5 `icon` rows per region. Icons are imported
     as Legendary; Essentials as Standard.
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

## Refreshing iNaturalist evidence

Wildlife uses the public iNaturalist API read-only. It does not use OAuth and never writes to
iNaturalist accounts.

Run this for each region you want to refresh (`mediterranean_europe`, `east_africa`, or
`caribbean`):

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_inaturalist_candidates.py mediterranean_europe --pages 3 --refresh
```

This writes a reviewable candidate list at `review/<region>_inaturalist_candidates.csv`, with
taxon identity, common/scientific names, observation counts and a recommended candidate flag.
Increase `--pages` to sample more than the top 200 species per animal group.

The current automatic rarity proposal can be produced from refreshed candidates:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\assign_regional_rarity.py
```

It assigns regional, animal-group-relative frequency bands and records the model version in the
catalogue source. Treat these as evidence only: copy the proposed values you accept into the
workbook before running the workbook importer again. Frequency is not biological rarity,
conservation status, or a substitute for editorial judgement.

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
- The importer refuses unknown taxon IDs, duplicate entries, invalid rarity/prestige, incomplete
  checklists and non-increasing level thresholds.
- Add media provenance separately. A strict release needs licence-verified media for all
  achievement taxa.
- Catalogue updates can change projected completion but never remove historical XP or a previously
  earned, versioned achievement.
