# Wildlife — Product Requirements Document v4 (Permanent read-only)

**Status:** Draft for design & development
**Date:** August 2026
**Platform:** Native Android (Kotlin + Jetpack Compose)
**Backend:** Not required for core features; optional later for social features
**API posture:** **Permanently read-only. No OAuth dependency and no writes to iNaturalist.**

> Wildlife must remain useful if OAuth access is never granted. All observation creation happens in the official iNaturalist app via Android handoff; Wildlife reads public records and owns only the companion game experience.

---

## 0. Scope decisions

| Decision | Choice |
|---|---|
| API access | **Unauthenticated read only.** OAuth is not part of the product plan |
| Capture | **Handoff only** — photo taken in Wildlife, submitted through the official iNaturalist app |
| Account linking | **Username + bio-code verification.** No password, no OAuth |
| Geographic scope v1 | Catalonia |
| Taxonomic scope v1 | Birds, mammals, reptiles, amphibians, butterflies and odonates (development cap: 580); reviewed conspicuous fish may be added later |

---

## 1. Product vision

Turn nature observation into a collection game, using iNaturalist as the biological backbone.

**Positioning:** Wildlife is a *recruitment funnel for iNaturalist*. It adds progression, regional completion, rarity and a visual collection — the things iNaturalist deliberately does not do — while sending every actual observation into iNaturalist itself, where real identifiers verify it and it contributes to real biodiversity science.

**What Wildlife never builds:** taxonomy, identification, community verification, or the observation record. In this version, not even the write path.

---

## 2. Architecture principles

1. **iNaturalist is the source of truth.** Wildlife's database holds only the game layer plus a read cache keyed by `inat_uuid`.
2. **Read-only API posture.** Every endpoint used is public and unauthenticated. No iNaturalist tokens are stored. Wildlife still processes personal data and must provide normal privacy, retention and deletion controls.
3. **Core traffic is direct from the device.** The on-device adapter uses a custom User-Agent, conservative request pacing and durable caches. It never writes to iNaturalist.
4. **Regional data is cached once per device and refreshed conservatively.**
5. **Derived state is recomputed, rewards are ledgered.** Collection state, badges and percentages recalculate on sync. XP is recorded once in an idempotent event ledger and is never duplicated or removed.
6. **Offline browsing, online sync.** Capture no longer needs offline support — the iNaturalist app owns that — but the catalogue and collection must be fully browsable without coverage.

---

## 3. Account linking

No password is ever requested, and no OAuth screen is shown.

1. User enters their iNaturalist username.
2. Wildlife generates a short code (e.g. `WILDLIFE-7F3A2B`).
3. User pastes it into their iNaturalist profile bio.
4. Wildlife reads the public profile and matches the code.
5. Wildlife stores the immutable iNaturalist user ID; the username is display metadata and may change.
6. Verified. The user removes the code; the link persists.

**Validated read-only contract:** resolve the exact username through v1, then read only `id`, `login` and `description` from `GET /v2/users/{id}`. The v1 user response does not expose the profile description. Verification codes use the `WILDLIFE-XXXXXX` format, expire after 24 hours and are cleared once verified.

The verified link is stored locally. Unlinking deletes the local association; a new device must repeat verification. If social or cross-device features are added later, Wildlife authentication belongs to that optional service and remains separate from iNaturalist.

This is required before any XP is awarded, because leaderboards without identity verification are trivially gamed — anyone could claim any username.

**Onboarding for users without an iNaturalist account:** the regional catalogue and "what can I see here" are browsable immediately, unlinked. Signing up happens on iNaturalist's own site, then linking as above. Present this as a two-minute step, not a barrier.

---

## 4. Capture — handoff

Wildlife still owns the moment of capture; it just does not own the submission.

**Flow:**
1. Create exactly **one Wildlife observation draft** and attach one or more photos from the same sighting.
2. Store the original observed time and coordinates in Wildlife independently of iNaturalist, and preserve them in EXIF where possible. Imported photos use their original EXIF; missing values require user confirmation and are never replaced by the current upload time/location.
3. Android share intent to the official app:
```kotlin
Intent(Intent.ACTION_SEND).apply {
    type = "image/*"
    putExtra(Intent.EXTRA_STREAM, photoUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    setPackage("org.inaturalist.android")
}
```
4. iNaturalist opens one new-observation form with the selected photo(s). The user gets **iNaturalist's AI suggestion**, which Wildlife cannot offer directly.
5. On return, Wildlife asks whether the user submitted it. "No" returns it to draft; "Yes" changes `handed_off` to `pending_public_confirmation`.
6. Wildlife retries the public API with backoff, fetching only observations owned by the linked immutable iNaturalist user ID. It compares original observed time/location and requires the candidate to have been created after the handoff.
7. Ambiguous matches require user confirmation. Only a confirmed public observation updates the collection or awards XP.

**Fallbacks:** check `packageManager` for the app; if absent, offer the web new-observation page or a Play Store link.

**Constraints to design around:**
- There is no documented observation-creation deep-link contract or returned observation ID. Preserve EXIF date/location where possible, but do not depend on undocumented intent extras.
- Use a `content://` URI from `FileProvider` or `MediaStore`, temporary read permission, `ActivityNotFoundException` handling and Android package-visibility declarations where required.
- The user leaves the app. Accept this and make the return worthwhile: on next open, show the new sighting animating into the collection with its XP. The reward lives in Wildlife even though the work happened elsewhere.
- One handoff always represents one observation. Multiple selected images are allowed only for the same sighting; photos far apart in time or location are rejected and must become separate drafts.

**What this version gains:** iNaturalist's computer vision identification, which is unavailable to third parties through the API. The v2 in-app path had no AI at all. This is a genuine product improvement, not only a compliance workaround.

---

## 5. Sync engine

No webhooks. Poll-based and unauthenticated through an Android data-layer adapter, keeping API parsing outside Activities and composables.

```
GET observations for immutable user ID, filtered by updated time
GET public user profile for linking and display metadata
GET public identification activity, only if the validated API contract supports the required fields
```

| Trigger | Action |
|---|---|
| App opened | Immediate sync for the linked user |
| Background (future WorkManager slice) | Conservative periodic refresh for recently active users |
| Returning from handoff | Sync attempt after a short delay, then retry |

**Incremental cursor.** Sync a fixed update-time window, paginate it idempotently, retain an overlap window for concurrent edits and advance the watermark only after every page succeeds. Observation ID is a pagination key, not the update watermark.

**Deletion handling.** Authenticated deletion feeds are unavailable. Run periodic full reconciliation and mark missing records as unavailable only after repeated confirmation, since deletion, privacy changes and temporary API failures can look similar.

**Pagination:** `id_above`, never `page` — the offset path caps at 10,000 results.

**Identification drift.** A species identified today may be reclassified later, or demoted to genus. iNaturalist also performs periodic taxon splits and swaps.
- Key observations on `inat_uuid`. Also store the current taxon ID and taxon-change mappings; an observation UUID and a taxon ID represent different entities.
- Recalculate collection state and badges on every sync.
- **Never subtract XP.** Present changes as information ("The community reclassified your sighting"), never as a penalty.

**Known read-only limitations:**
| Limitation | Impact | Handling |
|---|---|---|
| Obscured coordinates for threatened species | Heatmap slightly imprecise | Use a coarse grid, not exact pins |
| Private/hidden observations invisible | Some records never appear | Explain once during onboarding |
| No access to user's own true coordinates | Same as above | Accepted |

---

## 6. Collection (the Pokédex)

- Unobserved species appear as grey silhouettes; observed species unlock in full colour using the user's own photo as the card image.
- Browsable by taxonomic group and by region.
- **Taxon-counting rule:** collection entries are keyed by the species-level ancestor taxon ID, not the observation's lowest exact taxon ID. Subspecies and varieties unlock and appear under their parent species; their exact identification remains visible in species detail. Genus-only or higher identifications remain as separate "awaiting species identification" entries. Show the overall number as **collection entries**, with identified-species and awaiting-identification subtotals, because iNaturalist's `species_counts` result can include coarser taxa.
- Cache the observation's exact taxon ID and rank plus its collection taxon ID and rank. Recompute this projection whenever iNaturalist changes an identification so Wildlife follows the semantics of iNaturalist's `species_counts` endpoint.
- **Verification state is part of the collection UI:** an entry enters as *unverified* and becomes *confirmed* when iNaturalist reaches research grade. Half of all iNat observations are identified within two days, average around 18 days — a naturally paced delayed reward.
- Species detail: scientific data, conservation status, Wikipedia summary, global distribution map versus the user's own points.

### 6.1 Catalogue asset curation (hidden work — plan for it)

Names and taxonomy come from a versioned iNaturalist export. Photo reuse is decided by explicit licence metadata, not by hosting domain. Store author, source URL, licence code and required attribution for every reference image.

**Regional source contract:** canonical Catalonia uses iNaturalist place ID `12997` (`Cataluña`, administrative level 10). The raw research-grade species pool is approximately 12,806 at validation time and is not the launch denominator. During development, Wildlife keeps a versioned scope-limited snapshot capped at 580 entries: birds 250, mammals 80, reptiles 50, amphibians 30, butterflies 120 and odonates 50. After the first download it remains stored until the user explicitly confirms a refresh; startup and ordinary browsing never poll or replace it. The revision identifier is derived from the stable taxon denominator, not the download date or changing observation counts. The quotas are ordered by observation count for practical prototyping; they are not rarity or conservation classifications. Fish require a manually reviewed conspicuous-species allowlist and are empty by default. The snapshot is clearly labelled provisional. A curated, frozen seasonal catalogue bundled with a release replaces it before launch.

Build the launch reference image set from licences compatible with the intended distribution and business model. Provide in-app attribution and a machine-readable provenance manifest. Species Detail prioritises a Wikimedia Commons image only when it is explicitly assessed as Featured or Quality, maps to the taxon through its linked Wikipedia article or a Wikidata record verified against the iNaturalist taxon ID, and has a compatible Public Domain, CC0, CC BY or CC BY-SA licence. It then falls back to the licensed iNaturalist taxon default, another explicitly compatible research-grade iNaturalist observation photo, and finally a silhouette. Explore keeps undiscovered species hidden behind silhouettes. An observed species uses the user's own sighting photo when available, then an attributed stored reference image. Collection cards remain personal and do not substitute curated catalogue photography for a missing sighting. Wikimedia lookups are lazy rather than catalogue-wide. Temporary failures retain previous references and remain retryable. Missing imagery uses a licence-verified PhyloPic silhouette resolved through species, genus, family and order before the broad catalogue group fallback. Catalogue loading resolves and persists one family-level silhouette for related species in the background; opening Species Detail may later improve that species to an exact species or genus match. Representative match rank remains available in source credits and accessibility descriptions without adding redundant status text over image cards. Store reusable media in app-owned durable files, not the disposable image cache; keep creator, source, licence, assessment and taxonomic match metadata in SQLite. Pipeline versions mark which resolver policy produced a record and trigger targeted repair, but do not require downloading a separately versioned media pack or discarding valid local files.

Bird songs require **Xeno-canto** (open API, CC-licensed). Birds only.

---

## 7. Gamification

### 7.1 XP

| Event | XP |
|---|---|
| Common species | 10 |
| Uncommon (×5) / Rare (×20) / Legendary (×50) | scaled |
| First catch of a species | +500 |
| Reaching research grade | +50 |
| Identification given to another user on iNaturalist | +25 |
| **Out-of-range sighting confirmed after review delay** | **+250, "Anomaly" badge** |

**Rarity tiers** use a documented seasonal snapshot derived from Catalonia observations and reviewed catalogue rules. Raw observation count is a proxy affected by observer effort and detectability, so it must be tested and capped rather than treated as biological abundance.

**Out-of-range rewards.** Define the signal explicitly using validated public API fields or a versioned range dataset. Research Grade alone is not fraud-proof. Award only after a delay, exclude the bonus from competitive ranking until confirmed, and flag ambiguous cases for review.

### 7.2 Progression

- Levels: Tourist → Legendary Ranger. Unlocks strictly cosmetic.
- Streaks: consecutive days and consecutive weekends with at least one observation.
- Contextual badges: *Night Owl* (5 nocturnal sightings), *Biome Master* (50% of a region), seasonal badges.

**Denominator stability.** Regional species lists grow over time, which would silently erode users' completion percentages. **Freeze the catalogue per season** ("Fauna of the Ebre Delta 2027") and refresh annually, announcing it as new content.

### 7.3 Anti-spam

Read-only status reduces but does not remove this obligation — Wildlife still influences what its users post through the official app.

- XP weighted toward **quality**: research grade, useful identifications and responsible photo licensing.
- Diminishing returns on repeated observations of the same species within a week.
- Identification XP applies only to qualifying activity after joining Wildlife, excludes self-identifications, has daily caps and is awarded idempotently.
- Leaderboards rank **Wildlife users only**, never the global iNaturalist community.

---

## 8. Regional discovery

The strongest fit with the API, and entirely unauthenticated.

| Feature | Source |
|---|---|
| Hierarchy: World > Country > Community > Comarca / Natural park | `/v1/places/nearby` (`admin_level`); Catalan comarques and protected areas already exist as iNat places |
| "What can I see here?" | `species_counts?lat=&lng=&radius=&month=` |
| "What am I missing?" | `species_counts?unobserved_by_user_id=&lat=&lng=` — works with a public user ID, no auth needed |
| Completion bars per region | Frozen seasonal catalogue vs. user's species list |
| Map | App-owned observation overlays on a separately licensed basemap; iNaturalist tiles only if their use and caching contract is validated |

Resolve Catalonia and comarca `place_id` values once at build time via `/v1/places/autocomplete` and store them.

---

## 9. Dashboard

- **Personal heatmap** — dark map, fog-of-war revealed by exploration, coarse grid to absorb coordinate obscuring.
- **Timeline** — chronological feed with thumbnail maps.
- **Habit charts** — taxonomic distribution; 24-hour radial activity chart.
- **Regional progress** — completion bars ranked by percentage.

---

## 10. Social

| Feature | Implementation |
|---|---|
| Community verification | **Not built.** Surface iNaturalist's verification state and identification thread |
| Collaborator points | XP for identifications given *on iNaturalist*, read from `/v1/identifications?user_id=` |
| Weekly leaderboards | Global (Wildlife users) and friends. Requires verified account linking |
| Collaborative raids | Read-only variant: define a challenge by filter (taxon + place + date range) and track aggregate progress by querying `species_counts` / observation counts. **Note:** the v2 design used iNaturalist projects, which requires writes. Not available here |

---

## 11. Non-functional

- **Offline browsing** — catalogue, collection and species pages fully available without coverage. Photo capture works offline; handoff waits for the user to reopen.
- **Battery** — GPS sampled at capture and at "what can I see here" only, never continuously.
- **Visual system** — product-facing UI follows the dark-first **Field Guide Classic** contract in `style.md` and `ui_architecture.md`: wildlife imagery first, serif identity typography, compact information density, restrained olive/parchment/gold semantics and progressive Compose migration.
- **Dark mode** — native and the primary visual mode, essential for dusk and night observation. A future light theme must preserve semantic tokens rather than introduce a second screen-specific style.
- **Preloaded database** — species names, taxonomy and thumbnails for the Catalonia catalogue shipped with the app.
- **Localisation** — Catalan first; Spanish and English at launch.
- **Custom User-Agent** on every direct upstream request, identifying the app.
- **Privacy controls** — clear consent and disclosure, data minimisation, retention limits, export, unlink and deletion.
- **Accessibility** — scalable text, screen-reader labels, sufficient contrast and non-colour status indicators.
- **Image integrity** — AI concept imagery is never shipped. Every catalogue/reference photo requires source, creator, licence code and attribution; otherwise the UI uses an accessible species silhouette.
- **Diagnostics** — local request pacing, stale-data age, sync failures and handoff ambiguity are measurable during beta without collecting them remotely by default.

---

## 12. Explicitly out of scope

| Excluded | Reason |
|---|---|
| Writing observations to iNaturalist | Requires OAuth and is not part of the product plan |
| In-app AI identification | No third-party access to iNaturalist's model. Handoff provides it instead |
| Own verification queue | Already exists at iNaturalist with thousands of real experts |
| iNaturalist projects for raids | Write operation |
| Full invertebrate coverage | Thousands of species; curation infeasible for v1 |
| Weather at time of sighting | Defer to a later version (Open-Meteo has free historical data) |
| Non-bird audio | No source with adequate coverage |
| Global scope | Catalogue curation cost |

---

## 13. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Handoff friction kills the core loop** | **Critical** | The main risk of this version. Keep each handoff to one observation, make the return to Wildlife rewarding and measure completion in beta |
| Public API limits or policy changes | High | Pace requests per device, cache catalogue/taxon data for long periods, avoid background polling until measured, and keep the adapter replaceable |
| Handoff cannot be matched confidently | Critical | Validation spike, candidate ranking, manual confirmation and no false completion claims |
| Username claiming / leaderboard gaming | Medium | Bio-code verification, mandatory before XP |
| Community backlash over gamification | Medium | Quality-weighted XP; collaborator rewards |
| Identification drift confusing users | Medium | Never subtract XP; frame as information |
| Catalogue image licensing or curation underestimated | High | Explicit licence metadata, attribution manifest, representative sample and scheduled owner |
| Public data treated as non-personal | High | Privacy design, retention/deletion controls and location minimisation before beta |

---

## 14. Phasing

**Validation (week 1–3, throwaway prototypes only)** — confirm catalogue rules and size, rarity playability, discovery quality and explicitly licensed photo coverage. In parallel, prototype single-observation Android handoff with one/multiple photos, EXIF preservation, offline behavior and observation matching; validate the exact public API fields/version; model upstream request cost; and draft the privacy/data-retention boundary.

**Implementation note — 13 August 2026:** the successful on-device prototype has advanced into a Compose application with account linking, public observation sync, a provisional stored Catalonia catalogue, Collection, Explore, Species Detail, capture/handoff, candidate confirmation and an idempotent confirmation/first-species XP reward. This does not pass the validation gate: real camera EXIF, offline upload recovery, obscured/nearby matching, catalogue/photo coverage, discovery quality and representative request cost still need recorded evidence. Current implementation status and remaining work are tracked in `Wildlife_roadmap.md`; handoff evidence is tracked in `Handoff_feasibility.md`.

**MVP** — On-device account linking, sync engine, Catalonia catalogue, Pokédex, XP and levels, "what am I missing near me", handoff capture.

**v1.1** — Badges, streaks, heatmap and regional completion.

**v1.2** — Leaderboards, collaborator points, filter-based raids, bird audio.

**Later** — Expansion beyond Catalonia, weather capture, iOS via Compose Multiplatform (the sync engine and gamification core are already shareable).

---

## 15. Permanent read-only boundary

The product and data model must not depend on future OAuth approval. Wildlife will not create, edit or delete iNaturalist observations, access private coordinates, consume authenticated deletion feeds or create iNaturalist projects.

If handoff matching remains unreliable after single-observation handoff and manual confirmation, reposition Wildlife as a discovery and collection companion. Do not create an unofficial write path or weaken privacy controls to preserve the capture claim.

---

## 16. Current action item

Complete the expanded Gate 1 validation in Section 14. The app now implements the core loop, but the go/no-go decision still requires evidence that the catalogue is viable, licences are usable, public APIs provide the needed data, the request budget scales, and real handoffs can be matched or explicitly confirmed without OAuth.
