# Wildlife — Development Roadmap

**Companion to:** PRD v4 (Permanent read-only)
**Date:** August 2026

---

## Planning assumptions

| Assumption | Value | If wrong |
|---|---|---|
| Team | Solo developer | Halve the calendar estimates for two people; the catalogue track parallelises best |
| Availability | Part-time (~12–15 h/week) | One focused day means ~6 hours; calendar weeks remain illustrative |
| Start | September 2026 | — |
| Target public launch | Spring 2027 | Deliberately after winter — spring is when people go outside, and a nature app launching in November wastes its first impression |
| iNaturalist access | Public, unauthenticated, read-only | The plan never assumes future OAuth approval |

Effort figures are the reliable ones. Calendar dates are illustrative.

---

## Critical path at a glance

```
Feasibility ──► GATE 1 ──► Thin vertical slice ──► MVP ──► GATE 2 ──► Launch
                                          ▲
Catalogue curation ───────────────────────┘  (parallel, blocks launch not dev)
```

Catalogue curation starts only after Gate 1 confirms that the catalogue and licensing model are viable. Community feedback starts during feasibility and continues throughout development.

## Current implementation snapshot — 13 August 2026

The repository has advanced beyond the original phase boundaries, but Gate 1 remains open because implementation is not the same as field validation.

- The Android app is read-only with respect to iNaturalist and no longer depends on a Wildlife backend for its core loop.
- A shared Field Guide Classic Compose shell provides Home, Collection, Capture, Explore and Profile.
- Collection, Explore, Species Detail and Capture are implemented in Compose with explicit offline/error states, responsive grids and accessible image/state descriptions.
- Capture supports camera and gallery drafts, EXIF inspection and repair, same-sighting validation, official-app handoff, submitted/not-submitted return handling, public-API retries, candidate inspection and explicit confirmation.
- A confirmed public match updates the on-device collection and records confirmation/first-species XP idempotently before showing the reward moment.
- Public observation, catalogue and taxon data are stored in SQLite. Catalogue replacement is transactional and reusable photo/silhouette files are stored durably with source, creator, licence and taxonomic match metadata.
- The provisional Catalonia guide remains capped at 580 birds, mammals, reptiles, amphibians, butterflies and odonates. It is not the frozen launch catalogue and does not provide a trustworthy completion denominator or reviewed rarity model.
- Account linking is implemented in themed Material 3 Compose with username entry, immutable user-ID resolution, bio-code instructions, clipboard/profile handoff, loading/error/expiry states, verification, public-profile access and unlink confirmation. Remaining diagnostic utilities may still use the older programmatic View UI until touched.
- Placeholder levels and the first Profile progression surface are implemented. Delayed Research Grade rewards, reviewed final thresholds, location-based “what can I see here,” the curated multilingual release catalogue, privacy/export/deletion controls and the Gate 1 evidence pack remain incomplete.

The product-critical task remains the manual Gate 1 handoff/correlation matrix when field observations are practical. Account linking and the placeholder progression foundation are implemented; the next active slice is observation lifecycle synchronization and durable pending-handoff recovery.

---

## Phase 0 — Feasibility
**Effort: 7 days · Weeks 1–3 · Validation work; the resulting core has since been retained on device**

Unauthenticated API checks plus an Android handoff prototype. No OAuth permission is needed. The successful prototype has since been developed into the current on-device app, but its remaining real-world edge cases still require manual validation.

**Handoff implementation status (13 August 2026):** the full Compose capture/return/confirmation/reward flow is implemented. One-observation transfer and later public-record retrieval are proven; propagation delay is handled as a pending state with retries. Real camera EXIF, offline recovery and ambiguous nearby matches remain field-validation gaps. See [Android handoff feasibility](Handoff_feasibility.md).

| # | Question | Method |
|---|---|---|
| 1 | How many species does the Catalonia catalogue actually contain? | `species_counts?place_id=<CAT>&iconic_taxa=...` |
| 2 | Is the rarity distribution playable, or a long tail nobody ever sees? | Histogram of observation counts |
| 3 | Does "what am I missing here" return useful results? | `species_counts?unobserved_by_user_id=&lat=&lng=&radius=20` |
| 4 | **What share of species have a compatible reference photo?** | Validate explicit licence, author, source URL and attribution metadata per asset |
| 5 | Can one-observation handoffs with one/multiple photos be correlated reliably? | Android prototype: original metadata, cancellation, delayed/offline upload, obscured coordinates and ambiguous candidates |
| 6 | Which public API version and fields support every feature? | Endpoint contract matrix with pagination, cacheability and failure tests |
| 7 | What is the real upstream request cost per active user? | Replay representative small, medium and large accounts through a request-budget model |
| 8 | What personal data is retained? | Data inventory, retention/deletion rules and location-minimisation review |

Questions 4–7 can change the product or its positioning; none may be treated as implementation detail.

### 🚦 Gate 1 — Go / adjust / rethink

| Signal | Threshold | Action if failed |
|---|---|---|
| Compatible photo coverage with complete attribution | **>70%** | 50–70%: manual Wikimedia curation for the gap, +2 weeks. Below 50%: redesign the visual collection |
| Catalogue size | 400–1,200 species | Too small: widen taxonomic scope. Too large: narrow it |
| "What am I missing" quality | Returns plausible, findable species | If dominated by obscure taxa, filter by minimum regional observation count |
| Handoff correlation | Most cases auto-match; ambiguous cases can be confirmed safely | If not, make capture secondary and position the MVP as a companion dashboard |
| Public API contract | Every MVP field has a tested read-only source | Remove or redesign unsupported features |
| Request load | Sustainable per device with conservative pacing | Reduce background sync and ship more catalogue data preloaded |

**Do not proceed past this gate without completing the checks and prototype.** Seven focused days can de-risk months of work.

---

## Phase 1 — Foundations
**Effort: 24 days · Weeks 4–12**

Three tracks run in parallel after Gate 1.

### Track A — On-device data layer and vertical slice (15 days, critical path)

Build in this order; each depends on the previous.

| # | Component | Effort |
|---|---|---|
| 1 | Versioned public API adapter, request pacing and durable cache | Implemented on-device: SQLite cache, UUID keys and conservative per-device pacing |
| 2 | Bio-code linking to immutable iNaturalist user ID | Implemented and verified on device; no Wildlife account is required for core use |
| 3 | Safe paginated sync and full local reconciliation | Implemented on-device with `id_above` pagination and replacement only after a complete fetch |
| 4 | Idempotent XP event ledger and collection projections | Implemented for confirmed observations and first-species XP; level/badge presentation remains |
| 5 | Thin end-to-end slice: link → handoff → sync → match/confirm → reward | Implemented in Compose and ready for the manual Gate 1 field matrix |
| 6 | Local diagnostics, stale-data policy and regional cache | Partially implemented: durable regional/taxon caches and recoverable errors exist; dedicated diagnostics and policy documentation remain |

The app deliberately avoids frequent background polling. A separate service is deferred unless social, cross-device or competitive features require one.

### Track B — Catalogue curation (9 days, parallelisable, blocks launch)

Build the versioned, curated catalogue dataset from the provisional scope-limited 580-entry snapshot: taxon identity and change mapping, scientific and vernacular names (ca/es/en), group, seasonal rarity tier and licence-verified reference image or silhouette with attribution. Output: a baseline SQLite catalogue plus an update strategy.

**Foundation implemented:** canonical Catalonia place ID `12997`; on-device Android SQLite snapshots; weekly refresh; offline name search; collection cross-reference; and explicit photo licence/attribution fields. The current 580-entry scope-limited research-grade occurrence snapshot is provisional. Remaining work is human-reviewed scope, multilingual completeness, seasonal rarity rules, conservation metadata, image licence approval and a preloaded release database.

It is dependency-light and blocks launch. Start it in week 4, after Gate 1.

### Track C — Community review (ongoing, starts in Phase 0)

Use iNaturalist normally and request feedback on data quality, gamification and API load. This is product-risk reduction, not preparation for OAuth.

---

## Phase 2 — Android MVP
**Effort: 35 days · Weeks 13–25**

Ordered riskiest-first, so that failure surfaces early.

| # | Feature | Current status | Remaining work |
|---|---|---|---|
| 1 | **Capture + one-observation handoff + matching/confirmation** | Implemented in Compose, including confirmed reward | Complete the manual Gate 1 field matrix and weekend-outing checkpoint |
| 2 | Account linking flow | Implemented in themed Material 3 Compose with bio-code verification against the immutable public user ID, loading/error/expiry/unlink states, state previews and projection tests | Device/TalkBack validation and eventual localisation remain |
| 3 | Sync + local database + offline/stale states | Implemented directly on device with SQLite, safe reconciliation and durable caches | Formalise stale-data/diagnostic policy; WorkManager remains deferred |
| 4 | Field Guide Classic Compose foundation | Implemented | Continue reuse; do not create screen-specific design systems |
| 5 | Pokédex and species detail | Implemented for Collection, provisional Explore and Species Detail | Curated release content, localisation and final device/accessibility validation remain |
| 6 | XP, levels, progression UI | Placeholder v0.1 is implemented through centralized rules, lifetime-XP levels, weekly repeat diminishing returns, migrated ledger history, Profile progress/recent rewards, selectable earned titles and idempotent `+50 XP` when a known observation first reaches Research Grade | Product review and pacing simulation remain; rarity/badges/streaks stay disabled |
| 7 | "What can I see here" / "what am I missing" | Not implemented as a location-based feature; Explore currently browses the stored Catalonia catalogue | Validate result quality and add the location-based projection without conflating observation frequency with rarity |
| 8 | Onboarding, privacy controls, accessibility and polish | Core product screens include responsive grids, large-text previews and semantic state descriptions; Profile states the read-only boundary | Finish account/onboarding utilities, retained-data/export/deletion controls, localisation and broader device/TalkBack checks |

Current stack: Kotlin, Jetpack Compose, Material 3 and direct on-device SQLite. WorkManager is deferred; Room is not currently used.

**Navigation checkpoint:** the shared Navigation Compose shell is implemented with Home, Collection, a central Capture handoff action, Explore and Profile. Capture return, confirmed reward, account linking and the first ledger-backed Profile progression surface are complete in Compose. Observation lifecycle status and delayed rewards are next.

UI delivery follows `docs/style.md` and `docs/ui_architecture.md`. New and materially changed product screens use the final Field Guide Classic system; untouched feasibility/diagnostic screens may remain utilitarian until their slice is migrated.

**Internal checkpoint after item 1:** use it for a full weekend outing. If switching or matching is still unreliable, capture becomes secondary and the MVP is positioned as a discovery/collection companion.

---

## Phase 3 — Closed beta
**Effort: 12 days · Weeks 26–34 · 20–30 users in Catalonia**

Recruit through Catalan naturalist groups, ICHN, local birding communities. These people will be blunt with you, which is exactly what you need.

### Metrics that decide the launch

| Metric | Target | Meaning |
|---|---|---|
| **Handoff completion rate** | >70% | The product-critical number. Sightings started in Wildlife that actually get posted. Below 50%, the core loop is broken |
| **Research grade rate at day 30** | >60% | Fixed observation-age window avoids penalising recent records |
| Week-4 retention | >30% | Does the collection mechanic hold? |
| Sightings per active user per week | >2 | Engagement depth |
| Upstream API requests per active device per day | Measured against conservative pacing targets | Determines whether refresh intervals or preloaded data need adjustment |
| Ambiguous/incorrect handoff matches | <10% / <1% | Automatic matching may be uncertain; false matches must be exceptional |

### 🚦 Gate 2 — Launch readiness

- Handoff completion above 70%; below that, reposition as a companion rather than silently lowering the target.
- Research grade rate at day 30 above 60%, re-measured after any guidance change.
- Incorrect automatic matches below 1%; ambiguous matches use confirmation.
- No unresolved rate-limit incidents.
- Privacy, deletion, licence attribution and Play data-safety requirements complete.

---

## Phase 4 — Public launch v1.0
**Effort: 10 days · After Gate 2 · Target spring–summer 2027**

Play Store listing, Catalan-first store copy, a launch post on the iNaturalist forum framing Wildlife as a recruitment tool for the platform, and outreach to Catalan naturalist communities.

**Capacity policy:** measure requests per active device, prefer stale cached data over unnecessary refreshes and adjust refresh intervals before launch.

---

## Phase 5 — Post-launch
**Weeks 35+**

| Release | Contents | Effort |
|---|---|---|
| **v1.1** | Badges, streaks, personal heatmap and regional completion | 10 d |
| **v1.2** | Leaderboards, collaborator points, filter-based raids, bird audio (Xeno-canto) | 15 d |
| **v1.3** | Expansion beyond Catalonia, weather capture | 10 d |

---

## Summary

| Phase | Effort | Calendar (part-time) |
|---|---|---|
| 0 — Feasibility | 7 d | Weeks 1–3 |
| 1 — Foundations | 24 d | Weeks 4–12 |
| 2 — Android MVP | 32 d | Weeks 13–25 |
| 3 — Closed beta | 12 d | Weeks 26–34 |
| 4 — Launch | 10 d | After Gate 2 |
| **Total to v1.0** | **~85 focused days** | **~8–10 months at 12–15 h/week** |

---

## Pivot and stop criteria

Defined now, while judgement is uncontaminated by sunk cost.

| Trigger | Response |
|---|---|
| Photo coverage below 50% at Gate 1 | Redesign the collection visual concept before building |
| Handoff completion below 50% after the one-observation confirmation UX | Reposition as a discovery and collection companion; OAuth is not a fallback |
| Research grade rate below 40% in beta | Stop recruiting. Fix capture guidance first — damaging the iNaturalist community damages the product's foundation |
| Week-4 retention below 15% | The gamification does not hold. Fix the loop before adding features |
| Catalogue curation past 15 days | Cut scope to vertebrates only |

---

## Immediate next actions

1. **Now — product-critical:** complete the manual handoff/correlation matrix with a consenting test account: real single-photo capture with EXIF, same-sighting multi-photo handoff, offline upload recovery, obscured coordinates and two observations close in time/location. Record propagation delay, automatic matches, ambiguity and false matches; never auto-confirm ambiguity.
2. **In parallel — Gate 1 evidence:** finish catalogue/photo-licence coverage, “what am I missing” quality, endpoint-contract and representative request-budget validation. Record the result against every Gate 1 threshold rather than inferring it from implemented code.
3. **Complete — placeholder progression foundation:** [`progression_rules.md`](progression_rules.md) now maps to centralized versioned rules, SQLite ledger metadata/history, weekly repeat diminishing returns, lifetime-XP levels, Profile progress/recent rewards and selectable earned titles. Disabled rarity, badge and streak mechanics remain out of the UI.
4. **Complete — foreground observation lifecycle:** app resume and manual retry use the centralized on-device sync; Profile exposes syncing/error/stale/last-checked state; schema v5 stores idempotent quality transitions; `needs_id → research` can award `+50 XP` once; confirmed reward writes repair after restart; pending handoffs survive restart and show when a public candidate is ready for explicit review. Already-linked observations are excluded from new match proposals.
5. **After request-budget evidence:** add conservative WorkManager refresh and optional delayed-reward notification without polling aggressively. Until then, foreground refresh remains the documented automatic behavior.
6. **Next active product slice:** resume location-based discovery and the personal observations map against validated API, privacy and basemap evidence.
7. **After Gate 1:** freeze the reviewed multilingual release catalogue.
8. **Before beta:** document retained data, export/unlink/deletion behavior and location minimisation; obtain early community feedback.
