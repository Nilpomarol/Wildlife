# Pilot release checklist

This is the operational gate for Mediterranean Europe, East Africa and the Caribbean. It does not
approve biological or balance decisions automatically; it makes their outstanding evidence visible.

## Before a pilot pack is frozen

1. Refresh iNaturalist evidence and review its proposals in the content workbook.
2. Confirm the catalogue, encounter rarity, Legendary prestige, 10 Essentials and 5 Icons for the
   target regional version.
3. Confirm each Essential/Icon has licence-compatible media provenance.
4. Change the approved catalogue status to `frozen` and preserve its versioned review evidence.
5. Validate/apply the workbook, generate content and run the audit:

```powershell
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\import_content_workbook.py
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\generate_catalogues.py --strict
& 'C:\Users\nilpo\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\audit_pilot_release.py --strict
```

The audit writes `catalogues/review/pilot-release-audit.md` and `.json`. Its strict mode fails
until all three packs are frozen, checklist-complete, media-covered, evidence-backed, non-unknown
in rarity and represented by the current checksum-validated generated pack.

## Gate 2 operational checks

Run these with the frozen pilot packs on target devices:

- New observation, ambiguous observation, later-confirmed observation and retry paths must not
  duplicate XP.
- Offline capture, app restart, background retry and delayed connectivity recovery must preserve
  the observation state.
- Installing a valid new pack and rejecting a corrupt pack must preserve local observations,
  achievements and earned XP.
- Structured local export and full local-data deletion must be checked on-device.
- Review beta metrics against the Gate 2 targets in the roadmap before broadening distribution.

The audit is a readiness signal, not a replacement for owner approval or device testing.
