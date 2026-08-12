# Android handoff feasibility

**Status:** Core flow passed; matching edge cases remain, 12 August 2026

## Proven

- The prototype builds and runs on Android without iNaturalist OAuth.
- Camera capture launches with a private `FileProvider` URI and temporary read/write grants.
- Pending markers persist locally and remain distinct from confirmed observations.
- Two location-free disposable JPEGs were handed to one official iNaturalist observation form.
- iNaturalist opened its observation editor and displayed both images.
- The prototype records `handed_off`, explicit user-reported submission, `pending` and public `confirmed` as separate states.
- Candidate matching tests cover unique, ambiguous, obscured, distant and stale observations: 5/5 passing.
- Public-user retrieval was validated live without OAuth: an exact username resolved to its immutable user ID and all 89/89 public observations were returned with taxon, date, quality grade, observation ID and URL.
- The tester confirmed the real handoff-to-published-observation flow and later public retrieval both work. Public API propagation is delayed, so Wildlife keeps the marker pending and retries with backoff.

The automated disposable-photo validation did not upload an observation. A separate tester-controlled real observation confirmed the end-to-end public retrieval path.

## Not yet proven

- A completed real camera capture preserves usable timestamp and location EXIF.
- Single-photo handoff behaves correctly end to end.
- Offline handoff and later upload recovery work.
- Real-world matching is reliable for obscured coordinates and nearby observations.

## Decision

Continue Phase 0, but do not pass Gate 1 yet. One-observation Android handoff is viable; reliable correlation in ambiguous cases remains the main risk.

## Next validation

Run a small manual field matrix using a consenting test account:

1. Capture and publish one real observation through iNaturalist, then fetch it using the resolved immutable user ID.
2. Repeat with multiple photos of the same sighting; verify that obviously different times/locations are rejected as separate drafts.
3. Repeat once offline, uploading later.
4. Test an obscured location and two observations close in time.
5. Record handoff completion, public-API delay, automatic match rate, ambiguity and false matches.

Never auto-confirm an ambiguous result. OAuth or an unofficial write path is not a fallback.
# Persistent sync prototype

The Android handoff now reads observations through Wildlife's local backend adapter rather than querying iNaturalist directly for pending matches. The adapter caches public observations under the verified immutable user ID and observation UUID, uses an overlapping update cursor, reconciles fully every 24 hours, and records confirmation XP idempotently. Android keeps an offline SQLite projection of the cached observations and collection summary.

This is a local validation slice, not a deployable service: production still requires HTTPS, Wildlife session authorization, managed storage, deletion/retention jobs and monitoring. See `backend/README.md` for local operation.

The collection projection includes every cached observation from the verified account and groups identified observations by iNaturalist taxon ID. Historical observations unlock collection entries but do not currently receive retroactive XP. Unidentified observations remain separate until iNaturalist supplies a taxon.

**Taxonomy projection verified:** the tested cache contains 89 observations and originally produced 61 distinct exact taxon IDs: 40 species, 19 subspecies and 2 genus-level identifications. Wildlife now stores exact and collection taxon IDs/ranks, collapses subspecies into their parent species, and preserves the two genus entries as awaiting species identification. The resulting 57 collection entries match iNaturalist's `species_counts` response: 55 species plus 2 genus-level pending identifications. Exact subspecies identification remains cached as detail metadata.
