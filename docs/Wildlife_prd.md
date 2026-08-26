# Wildlife — Product Requirements Document v5 (Permanent read-only, regional expansion)

**Status:** Adopted direction for design and development
**Date:** 21 August 2026
**Content-pipeline amendment:** 24 August 2026
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
| Geographic scope | 24 owner-defined world regions with staged catalogue rollout; architecture must support later additions/splits |
| Initial catalogue rollout | Three contrasting pilot regions, beginning with Mediterranean Europe as the migration path from Catalonia |
| Taxonomic scope | Photographable mammals, birds, reptiles, amphibians, conspicuous fish and a restrained selection of distinctive invertebrates |
| Regional game layer | Frozen catalogue completion, 10 Regional Essentials and 5 Regional Icons |

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
4. **Regional data is built and versioned before release.** Catalogue membership, boundaries, rarity, achievement definitions, names, sourced summaries, conservation snapshots and media manifests are local and refreshed only through explicit versioned content updates. Direct thumbnail/detail URLs and provenance are resolved during content authoring; ordinary browsing never performs media discovery.
5. **Derived state is recomputed, rewards are ledgered.** Collection state, badges and percentages recalculate on sync. XP is recorded once in an idempotent event ledger and is never duplicated or removed.
6. **Offline browsing, online sync.** Capture no longer needs offline support — the iNaturalist app owns that — but the catalogue and collection must be fully browsable without coverage.
7. **Global taxa, regional membership.** Taxon identity/media are deduplicated globally; catalogue membership, encounter rarity, standing and completion are regional.

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

### 4.1 Observation management

Capture creates exactly one new observation draft and does not act as the long-term observation-management destination.

- One draft is presented as one container with a horizontal photo strip and an explicit “1 observation · N photos” label.
- Drafts, submitted/pending handoffs, ambiguous candidates, synced observations and lifecycle status live in a focused **Observations** screen.
- Home exposes recent/pending observations and a “See all” route. Species Detail opens the same screen filtered to that taxon.
- The Observations screen may retry Wildlife sync, confirm a candidate, hide/restore an observation on Wildlife's map, delete Wildlife-owned local state and open the official public record.
- It never edits or deletes an iNaturalist observation.
- Wildlife cannot force the official app to upload in the background. If a submitted handoff is still unavailable publicly, Wildlife explains that the user may need to reopen iNaturalist and provides an explicit action to do so.

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
| Background (future WorkManager slice) | Refresh only observations whose individual check time is due, for recently active users |
| Returning from handoff | Sync attempt after a short delay, then retry |

**Incremental cursor.** Sync a fixed update-time window, paginate it idempotently, retain an overlap window for concurrent edits and advance the watermark only after every page succeeds. Observation ID is a pagination key, not the update watermark.

**Per-observation refresh priority.** Wildlife stores a local `next_check_at` and refresh priority for each observation. New, pending and Needs ID observations are checked most often; observations with recent community activity are checked less often. After Wildlife observes Research Grade and completes one confirmation pass, that observation leaves routine refreshes. It remains eligible for a manual refresh and a rare full reconciliation because public quality and taxonomy can still change. Due observations should be batched into as few upstream requests as the validated API permits; this policy must not become one request per observation. Failures use increasing retry delays and never cause continuous polling.

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
- The current region comes only from a one-shot device location assigned through the bundled boundaries. It is not manually editable. Every fresh app launch starts regional surfaces from that current region. Explore may browse a different guide for the remainder of the app session, but that never changes Home Near me, regional progress or current-region cache priority. Without a usable fix, show unavailable/last-known state rather than silently choosing a region.
- A species observed in another region does not unlock this region's catalogue entry. Region assignment is persisted against the observation UUID and boundary version.
- **Taxon-counting rule:** collection entries are keyed by the species-level ancestor taxon ID, not the observation's lowest exact taxon ID. Subspecies and varieties unlock and appear under their parent species; their exact identification remains visible in species detail. Genus-only or higher identifications remain as separate "awaiting species identification" entries. Show the overall number as **collection entries**, with identified-species and awaiting-identification subtotals, because iNaturalist's `species_counts` result can include coarser taxa.
- Cache the observation's exact taxon ID and rank plus its collection taxon ID and rank. Recompute this projection whenever iNaturalist changes an identification so Wildlife follows the semantics of iNaturalist's `species_counts` endpoint.
- **Verification state is part of the collection UI:** an entry enters as *unverified* and becomes *confirmed* when iNaturalist reaches research grade. Half of all iNat observations are identified within two days, average around 18 days — a naturally paced delayed reward.
- Species detail: scientific data, conservation status, Wikipedia summary, global distribution map versus the user's own points.

### 6.1 Catalogue asset curation (hidden work — plan for it)

Names and taxonomy come from a versioned iNaturalist export. Photo reuse is decided by explicit licence metadata, not by hosting domain. Store author, source URL, licence code and required attribution for every reference image.

**Regional source contract:** [`regional_catalogues.md`](regional_catalogues.md) defines the 24 owner-supplied regions, boundary-completion requirements, catalogue schema, content policy, achievement lists and authoring validation. The attached source workbook's animal proposals are not approved content and are ignored.

The existing Catalonia `place_id=12997` / 580-entry snapshot remains a provisional development input while Mediterranean Europe becomes the first pilot catalogue. Raw regional occurrence pools are never launch denominators. Each release catalogue is curated, frozen and bundled from deterministic source manifests. “Frozen” means an immutable versioned release snapshot, not permanently locked authoring data: Essentials, Icons and membership stay easy to edit, and approved changes publish as a new catalogue version without revoking earned history. Ordinary startup/browsing never regenerates or silently replaces a catalogue from live API frequency.

Build the reference image set from licences compatible with the intended distribution and business model. The networked content-authoring refresh resolves the preferred Wikimedia Commons or iNaturalist asset once, records the stable provider identity, direct thumbnail/detail URLs, source page, creator, licence, dimensions and selection evidence, and freezes those inputs for the offline deterministic Android content build. This manifest remains the primary source. When a published taxon has no photo or taxon-specific silhouette, opening Species Detail may run one targeted background repair through Wikimedia/iNaturalist and PhyloPic. A validated result is persisted as a media-only overlay and its file is cached; negative results are also cached for 30 days. Runtime repair never changes published identity, taxonomy, prose, conservation or regional membership.

Species Detail reads identity, available sourced summary, conservation and media selection locally. Missing description or conservation coverage renders explicitly as unavailable and does not require a waiver. Its bundled broad-group silhouette is always the bottom hero layer, so photo/silhouette decoding and recovery never produce an empty hero. When a manifest variant exists it performs only that direct validated download; when a media stage is absent it runs the targeted repair above. Photo and silhouette stages update independently. Collection and Explore are silhouette-first: only a user's own sighting photo may replace catalogue/grid silhouettes. Home's Near me carousel is the sole compact exception and may show a validated local catalogue photo, or the nearby response's licence-compatible provider default when no local reference is stored, provided creator and licence are visible on the plate. Incompatible provider defaults remain blocked. Stored attributed reference photos otherwise remain reserved for Species Detail.

Global taxon/media records are deduplicated across regions. Bundle compact broad-group silhouettes, curate the most specific reusable silhouettes and reference photos available, and report coverage without inventing assets. Download current-region thumbnails through bounded, resumable background work; a different guide being browsed receives only bounded visible-row preparation. Larger photo variants remain targeted to Species Detail. Optional regional media packs are versioned independently from user data. Durable media storage uses atomic validated writes, checksums, storage accounting, pinning and LRU eviction. Missing-stage repair is detail-triggered, background-only, licence-gated and persisted; it never blocks local catalogue content or becomes catalogue-wide provider crawling. The adopted implementation contract is [`species_content_pipeline_plan.md`](species_content_pipeline_plan.md).

Bird songs require **Xeno-canto** (open API, CC-licensed). Birds only.

---

## 7. Gamification

### 7.1 XP

| Event | XP |
|---|---|
| Confirmed observation repeats in one ISO week | 10, 5, 5, then 0 |
| First species globally | +500 |
| First valid unlock in the containing regional catalogue | +100 |
| Regional encounter rarity on first unlock | Common +0; Uncommon +50; Rare +150; Very Rare +300 |
| Regional Icon on first regional unlock | +1,000 |
| Complete 10 Regional Essentials | +1,500 |
| Complete 5 Regional Icons | +3,000 |
| Reaching research grade | +50 |
| Identification given to another user on iNaturalist | +25 |
| **Out-of-range sighting confirmed after review delay** | **+250, "Anomaly" badge** |

**Encounter rarity** is regional and versioned: Common, Uncommon, Rare and Very Rare. It estimates encounter/photograph difficulty from reviewed evidence; raw observation count, conservation status and verification are not rarity.

**Regional standing** is orthogonal to rarity. A region's fixed five-species Icons list is the highest standing a species can hold, and it carries game value independently of how hard the animal is to find. A species may be `Common · Regional Icon`: an elephant can be locally attainable but still carry more game value than a common warthog because it defines the regional collection fantasy. Standing is not a claim that the species is scarce or threatened.

A separate `Legendary` prestige tier was specified alongside this and shipped in the pilot catalogues. In practice it named exactly the five Icons of each region and nothing else, so it was removed on 23 August 2026; its +1,000 reward moved onto Icon discovery.


**Out-of-range rewards.** Define the signal explicitly using validated public API fields or a versioned range dataset. Research Grade alone is not fraud-proof. Award only after a delay, exclude the bonus from competitive ranking until confirmed, and flag ambiguous cases for review.

### 7.2 Progression

- Levels: Tourist → Legendary Ranger. Unlocks strictly cosmetic.
- Streaks: consecutive days and consecutive weekends with at least one observation.
- Contextual badges: *Night Owl* (5 nocturnal sightings), *Biome Master* (50% of a region), seasonal badges.

The editable internal placeholder for level thresholds, event eligibility, cosmetic rewards, disabled mechanics and migration behavior is maintained in [`progression_rules.md`](progression_rules.md). It is not a final product decision and must be reviewed before closed beta.

**Denominator stability.** Regional species lists grow over time, which would silently erode users' completion percentages. Freeze every regional catalogue/checklist version and announce replacements as new content. Completion may be recalculated for the selected catalogue version; ledgered XP and earned versioned achievements are never revoked.

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
| 24-region definitions and active catalogue | Local versioned polygons and country/territory membership from the regional catalogue pack |
| Hierarchy below a region | `/v1/places/nearby` where useful, resolved and cached conservatively |
| "What can I see here?" | `species_counts?lat=&lng=&radius=&month=` |
| "What am I missing?" | `species_counts?unobserved_by_user_id=&lat=&lng=` — works with a public user ID, no auth needed |
| Completion and achievements per region | Frozen catalogue/checklists vs. observations assigned to that region |
| Map | App-owned local Field Atlas rendered by MapLibre Native from a simplified, dissolved Natural Earth-derived display asset; coarse observation overlays remain on device. No external basemap or iNaturalist tiles are used |

Resolve required iNaturalist place IDs during catalogue authoring and store them. Runtime region assignment uses local versioned boundaries rather than live place lookup. Obscured/boundary-uncertain observations do not earn regional progress until safely assignable.

---

## 9. Dashboard

- **Regional world map** — local region polygons coloured by completion, with separate non-colour marks for Essentials, Icons and mastery.
- **Personal observation layer** — warm field-atlas map with coarse on-device cells to absorb coordinate obscuring; it may be toggled independently from regional progress.
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
- **Precomputed local content** — region definitions, boundaries, catalogue membership, names, taxonomy, rarity, achievements, attribution and thumbnails are generated before release and available locally. Taxon/media records shared across regions are deduplicated. Optional regional media packs may be downloaded and then remain local.
- **Localisation** — Catalan first; Spanish and English at launch.
- **Custom User-Agent** on every direct upstream request, identifying the app.
- **Privacy controls** — clear consent and disclosure, data minimisation, retention limits, export, unlink and deletion.
- **Testing diagnostics** — Profile shows aggregate retained-data counts and can copy a privacy-safe operational report without identity, coordinates, species labels, URLs or local paths. Confirmed local deletion removes all Wildlife-owned durable data and cache while leaving iNaturalist untouched. A structured user-data export remains distinct from this test report.
- **Map privacy** — observation overlays remain on device and use coarse cells. Each observation can be excluded from or restored to the map through a local preference that survives observation-cache refresh and never changes iNaturalist. Obscured, unavailable and user-hidden locations are represented separately. The bundled Field Atlas makes no external tile request; the accurate assignment geometry and simplified display geometry are separate local assets.
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
| Exhaustive global biodiversity catalogue | Conflicts with the photographable, curated 24-region product and cannot be maintained responsibly |

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

**Gate 1 — passed 21 August 2026.** After one week of owner field testing, the product owner accepted the handoff, EXIF/one-and-multiple-photo flow, delayed/offline recovery, matching behavior, public read contract, catalogue/media viability, discovery quality and request posture as sufficient to proceed. This is a product go decision, not a statistical reliability guarantee; closed beta still measures completion, ambiguity, false matches and request cost.

**Implementation note — amended 24 August 2026:** the scalable species content/media cutover and current-region/browsed-region correction are implemented. Broad UI redesign may resume. Draft catalogue curation, media coverage and the representative-device matrix remain pilot-release work, not UI-resume blockers. Current implementation and remaining release work are tracked in `Wildlife_roadmap.md`.

**Regional foundation** — Observation management, multi-photo clarity, catalogue generator, local boundaries, global taxon/regional membership model and three pilot catalogues.

**Expanded MVP** — On-device linking/sync, handoff capture, region-bound collection, encounter rarity, Regional Essentials/Icons, near-me discovery and regional/personal map layers.

**Closed beta** — Three pilot regions, progression simulation, localisation/accessibility, structured export and Gate 2 metrics.

**Content scale-up** — Curate and package the remaining regions after the three-pilot generator, assignment and migration checks pass.

**Later** — Generic badges/streaks, leaderboards, collaborator points, filter-based raids, bird audio, weather capture and iOS via Compose Multiplatform.

---

## 15. Permanent read-only boundary

The product and data model must not depend on future OAuth approval. Wildlife will not create, edit or delete iNaturalist observations, access private coordinates, consume authenticated deletion feeds or create iNaturalist projects.

If handoff matching remains unreliable after single-observation handoff and manual confirmation, reposition Wildlife as a discovery and collection companion. Do not create an unofficial write path or weaken privacy controls to preserve the capture claim.

---

## 16. Current action item

Continue catalogue and media curation region by region, complete the representative-device release matrix, and resume UI consolidation on top of the completed scalable content pipeline in [`species_content_pipeline_plan.md`](species_content_pipeline_plan.md).
