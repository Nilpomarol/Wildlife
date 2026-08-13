# Android handoff feasibility

**Status:** Product flow implemented; Gate 1 field matrix remains open, 13 August 2026

## Current app state

The original feasibility prototype has been retained and developed into the current Android app. Capture and the post-handoff return/reward flow now use Field Guide Classic Compose. The implementation supports camera and gallery drafts, private camera media, EXIF inspection and repair, same-sighting validation, official-app handoff, explicit submitted/not-submitted return state, public-API retries, candidate inspection, explicit confirmation, local-only deletion, no-app fallback and a reward moment gated by a confirmed public observation.

This implementation progress does not close Gate 1. The remaining question is whether matching and recovery are reliable in real field conditions, not whether the screens and state machine exist.

## Proven

- The app builds and runs on Android without iNaturalist OAuth.
- Camera capture launches with a private `FileProvider` URI and temporary read/write grants.
- Pending markers persist locally and remain distinct from confirmed observations.
- Two location-free disposable JPEGs were handed to one official iNaturalist observation form.
- iNaturalist opened its observation editor and displayed both images.
- The app records draft, `handed_off`, explicit user-reported submission, `pending` and public `confirmed` as separate states.
- Candidate matching tests cover unique, ambiguous, obscured, distant and stale observations: 5/5 passing.
- Public-user retrieval was validated live without OAuth: an exact username resolved to its immutable user ID and all 89/89 public observations were returned with taxon, date, quality grade, observation ID and URL.
- The tester confirmed the real handoff-to-published-observation flow and later public retrieval both work. Public API propagation is delayed, so Wildlife keeps the marker pending and retries with backoff.

The automated disposable-photo validation did not upload an observation. A separate tester-controlled real observation confirmed the end-to-end public retrieval path. The Compose implementation and automated candidate tests are stronger than the available field evidence, so the unproven cases below remain release decisions rather than polish items.

## Not yet proven

- A completed real camera capture preserves usable timestamp and location EXIF.
- Single-photo handoff behaves correctly end to end.
- Offline handoff and later upload recovery work.
- Real-world matching is reliable for obscured coordinates and nearby observations.

## Decision

Keep Gate 1 open. One-observation Android handoff is viable and its product flow is implemented; reliable real-world correlation and offline recovery remain the main risks. Do not treat the completed reward UI as evidence that the handoff itself is reliable.

## Next validation

Run a small manual field matrix using a consenting test account:

1. Capture and publish one real observation through iNaturalist, then fetch it using the resolved immutable user ID.
2. Repeat with multiple photos of the same sighting; verify that obviously different times/locations are rejected as separate drafts.
3. Repeat once offline, uploading later.
4. Test an obscured location and two observations close in time.
5. Record handoff completion, public-API delay, automatic match rate, ambiguity and false matches.

Never auto-confirm an ambiguous result. OAuth or an unofficial write path is not a fallback.
# Persistent on-device sync

The Android handoff reads public observations directly through Wildlife's on-device iNaturalist adapter. It fetches only the verified immutable user ID, stores observations by UUID in SQLite, preserves locally confirmed matches across refreshes and records confirmation XP idempotently on the device.

No local server, USB port forwarding or hosted Wildlife service is required for this core flow. A future online service may support social features or cross-device backup, but observation matching and the personal collection do not depend on it.

The collection projection includes every cached observation from the verified account and groups identified observations by iNaturalist taxon ID. Historical observations unlock collection entries but do not currently receive retroactive XP. Unidentified observations remain separate until iNaturalist supplies a taxon.

**Taxonomy projection verified:** the tested cache contains 89 observations and originally produced 61 distinct exact taxon IDs: 40 species, 19 subspecies and 2 genus-level identifications. Wildlife now stores exact and collection taxon IDs/ranks, collapses subspecies into their parent species, and preserves the two genus entries as awaiting species identification. The resulting 57 collection entries match iNaturalist's `species_counts` response: 55 species plus 2 genus-level pending identifications. Exact subspecies identification remains cached as detail metadata.
