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
3. Add a taxon to a pilot's `regions/<key>/catalogue.yaml`, with encounter rarity, prestige and
   inclusion provenance. Raw iNaturalist frequency is never a valid rarity value by itself.
4. Curate 10 Essentials and 5 Icons in `achievements.yaml`; Icons normally have `legendary`
   prestige in the corresponding regional entry.
5. Add only licence-verified reusable media to `media_manifest.yaml` with source URL, creator,
   licence code and taxon ID.

## Generate and validate

Use the bundled Python runtime:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py
```

This creates `generated/catalogue.sqlite`, `generated/catalogue-report.json` and
`generated/catalogue-diff.md`. The output is deterministic for identical source files and is
always marked `draft` until strict validation passes.

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py --strict
```

Strict mode requires complete territory coverage, frozen pilot catalogues, exactly 10 Essentials
and 5 Icons per pilot, valid regional taxon fields and approved media provenance. It must pass
before an Android content pack is built.

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

## Owner-managed Regional Essentials and Icons

Each pilot can keep its 10 Essentials and 5 Icons in a small spreadsheet-friendly review CSV.
For Mediterranean Europe, edit `review/mediterranean_europe_checklists.csv`: reorder entries,
change the curated encounter rarity, or replace a taxon with one already present in `taxa.yaml`.
Keep exactly ten `essential` rows and five `icon` rows. Icons are always `legendary`; Essentials
are always `standard`.

Import the reviewed CSV into the source manifests with:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_regional_checklist.py mediterranean_europe --csv .\catalogues\review\mediterranean_europe_checklists.csv
```
