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

---

## Phase 0 — Feasibility
**Effort: 7 days · Weeks 1–3 · Throwaway prototypes only**

Unauthenticated API checks plus a throwaway Android handoff prototype. No OAuth permission needed.

**Handoff prototype status (12 August 2026):** one-observation transfer and later public-record retrieval are proven. Propagation delay is handled as a pending state with retries. See [Android handoff feasibility](Handoff_feasibility.md).

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
| Request budget | Sustainable with reserve for retries and shared traffic | Reduce background sync and precompute/cache more data |

**Do not proceed past this gate without completing the checks and prototype.** Seven focused days can de-risk months of work.

---

## Phase 1 — Foundations
**Effort: 24 days · Weeks 4–12**

Three tracks run in parallel after Gate 1.

### Track A — Backend and vertical slice (15 days, critical path)

Build in this order; each depends on the previous.

| # | Component | Effort |
|---|---|---|
| 1 | Versioned public API adapter, global request budget and cache | Prototype implemented: SQLite cache, UUID keys and conservative request budget |
| 2 | Wildlife session model and bio-code linking to immutable iNaturalist user ID | Prototype implemented and verified on device; production session model remains |
| 3 | Safe incremental sync, overlap cursor and repeated full reconciliation | Prototype implemented: `updated_since` overlap plus 24-hour full reconciliation |
| 4 | Idempotent XP event ledger and collection projections | Prototype implemented for confirmed observation and first-species XP |
| 5 | Thin end-to-end slice: link → handoff → sync → match/confirm → reward | Prototype implemented and ready for device validation |
| 6 | Monitoring, stale-data policy and regional cache | 1 d |

The shared request budget is item 1 and applies to development, validation and production. Redis is optional until deployment measurements justify it.

### Track B — Catalogue curation (9 days, parallelisable, blocks launch)

Build the versioned ~800-species dataset: taxon identity and change mapping, scientific and vernacular names (ca/es/en), group, seasonal rarity tier and licence-verified reference image with attribution. Output: a baseline SQLite catalogue plus an update strategy.

It is dependency-light and blocks launch. Start it in week 4, after Gate 1.

### Track C — Community review (ongoing, starts in Phase 0)

Use iNaturalist normally and request feedback on data quality, gamification and API load. This is product-risk reduction, not preparation for OAuth.

---

## Phase 2 — Android MVP
**Effort: 32 days · Weeks 13–25**

Ordered riskiest-first, so that failure surfaces early.

| # | Feature | Effort | Why this order |
|---|---|---|---|
| 1 | **Capture + one-observation handoff + matching/confirmation** | 7 d | Permanent read-only core; validation evidence defines the UX |
| 2 | Account linking flow | 2 d | Gates everything else |
| 3 | Sync + local Room database + offline/stale states | 5 d | |
| 4 | Pokédex and species detail | 6 d | Taxonomy projection corrected and device-verified (61 exact taxa → 57 collection entries); imagery, catalogue detail and filters remain |
| 5 | XP, levels, progression UI | 4 d | |
| 6 | "What can I see here" / "what am I missing" | 4 d | Strongest API fit; high perceived value per unit of effort |
| 7 | Onboarding, privacy controls, accessibility and polish | 4 d | |

Stack: Kotlin, Compose, Room, WorkManager, Material 3 dark-first.

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
| Upstream API requests per user per day | Measured against reserved daily budget | Determines the supported active-user ceiling |
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

**Capacity ceiling:** calculate continuously as `(reserved daily upstream budget - shared traffic) / measured upstream requests per active user`. Apply backpressure and serve stale cached data before exceeding the budget.

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

1. **This week:** run the catalogue, licensing, API-contract and request-budget validation.
2. **In progress:** the throwaway Android handoff prototype works; complete the manual upload-to-public-record correlation matrix.
3. **This week:** draft the retained-data and deletion boundary and request early community feedback.
4. **After Gate 1:** build the thin vertical slice, catalogue and backend in parallel.
