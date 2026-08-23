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
   inclusion provenance. Raw iNaturalist frequency is never a valid rarity value by itself.
4. Curate 10 Essentials and 5 Icons in `achievements.yaml`. Icon membership is the whole of a
   species' standing; there is no separate prestige field to keep in step with it.
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
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py
```

The importer never changes global taxon identity or media provenance. Add those separately and
keep the workbook in git with the generated source changes so every content/balance decision is
reviewable.
