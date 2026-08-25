# Pilot release checklist

**Pipeline gate added:** 24 August 2026

This is the operational gate for Mediterranean Europe, East Africa and the Caribbean. It does not
approve biological or balance decisions automatically; it makes their outstanding evidence visible.

## Before a pilot pack is frozen

1. Refresh iNaturalist evidence and review its proposals in the content workbook.
2. Confirm the catalogue, encounter rarity, 10 Essentials and 5 Icons for the
   target regional version.
3. Review description/conservation candidates where useful. Missing sourced values are reported and render as unavailable; do not create placeholder waivers merely for completeness.
4. Confirm each Essential/Icon has licence-compatible media provenance plus validated direct thumbnail/detail variants; a source-page URL alone is insufficient.
5. Pass Slices 1–7 and the completion gate in [`species_content_pipeline_plan.md`](species_content_pipeline_plan.md), including the production-path 25-region fixture.
6. Change the approved catalogue status to `frozen` and preserve its versioned review evidence.
7. Validate/apply the workbook, generate content and run the audit:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_content_workbook.py
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py --strict
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\audit_pilot_release.py --strict
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\benchmark_content_pipeline.py
```

The audit writes `catalogues/review/pilot-release-audit.md` and `.json`. Its strict mode requires
at least one frozen region and checks each frozen region independently, so regions can be added
gradually while other drafts remain editable. It blocks incomplete checklists, missing required
achievement media/evidence, unknown rarity and an invalid generated pack. Description,
conservation, photo and silhouette coverage remain visible but non-blocking.
Passing it remains necessary but not sufficient: provider-response validation, bounded runtime
cache behavior, device verification and the production-path 25-region suite must also pass.

## Gate 2 operational checks

Run these with the frozen pilot packs on target devices:

- New observation, ambiguous observation, later-confirmed observation and retry paths must not
  duplicate XP.
- Offline capture, app restart, background retry and delayed connectivity recovery must preserve
  the observation state.
- Installing a valid new pack and rejecting a corrupt pack must preserve local observations,
  achievements and earned XP.
- Published Species Detail must work offline for text and broad-group silhouettes, make zero
  provider-discovery requests for complete entries, independently upgrade a missing direct variant,
  and persist a licence-verified targeted repair when the published photo/specific silhouette stage
  is absent.
- Current-region thumbnail prefetch must resume after process death, avoid duplicate/starved work
  and recover from corruption and full-storage eviction.
- Structured local export and full local-data deletion must be checked on-device.
- Review beta metrics against the Gate 2 targets in the roadmap before broadening distribution.

The audit is a readiness signal, not a replacement for owner approval or device testing.
