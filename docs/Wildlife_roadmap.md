# Wildlife — Development Roadmap

**Companion to:** PRD v5 (Permanent read-only, regional expansion)
**Date:** 21 August 2026
**Planning basis:** Solo developer, part-time (~12–15 h/week). Effort days are more reliable than calendar dates.

## 1. Product decision and current position

Gate 1 is **passed**. After one week of field use, the product owner accepted the handoff, one/multiple-photo flow, delayed/offline recovery, public matching, catalogue/media viability, discovery quality and request posture as sufficient to continue.

This is a go decision, not a statistical reliability claim. Closed beta still measures handoff completion, ambiguity, false matches, request volume and retention.

The project has also changed scope:

- from a Catalonia-only MVP to an engine for 24 owner-defined world regions;
- from rarity-only value to separate encounter rarity and regional Legendary prestige;
- from a global species unlock projection to region-bound catalogue completion;
- from generic later badges to two core curated achievements per region: 10 Regional Essentials and 5 Regional Icons;
- from live catalogue construction toward deterministic, precomputed local content.

The adopted region definitions and content/data contract are in [`regional_catalogues.md`](regional_catalogues.md). The owner-supplied workbook's animal suggestions are not approved inputs and are ignored.

## 2. Current implementation snapshot

### Complete

- Permanent read-only iNaturalist posture with official-app handoff.
- Public bio-code account linking to immutable iNaturalist user ID.
- On-device observation sync, SQLite caches and idempotent XP ledger.
- Field Guide Classic Compose shell: Home, Collection, Capture, Explore and Profile.
- Compose Collection, Explore/Near Me, Species Detail, Capture/reward and account flow.
- One observation draft can contain multiple same-sighting photos.
- EXIF inspection/repair, candidate matching, explicit confirmation and delayed retry.
- Provisional 580-entry Catalonia catalogue and durable attributed media/silhouettes.
- Foreground observation lifecycle sync including Research Grade transition rewards.
- Coarse personal observation map with local visibility overrides.
- Retained-data inventory, privacy-safe test report and deletion of Wildlife-owned local data.
- Unit tests and debug build green as of the Gate 1 review.
- Generated and bundled pilot catalogues for Mediterranean Europe, East Africa and the Caribbean, each with 10 Essentials and 5 Icons.
- Local regional boundary assignment, region-bound collection/XP/achievements and active-region selection.
- Regional Home progress, Explore guide/Near Me filtering and Species Detail context.
- Focused Observations route with grouped multi-photo handoffs, candidate review, retry and iNaturalist recovery guidance.

### Implemented but provisional

- The legacy Catalonia snapshot remains only a detail/media migration cache; product browsing uses the active bundled regional catalogue.
- Pilot encounter rarity is generated from iNaturalist occurrence evidence and is explicitly draft/editorially revisable before release.
- Level thresholds and global first-species XP are placeholder v0.1 rules.
- Field Atlas completion effects and broad device/accessibility validation remain provisional.
- XP values and level thresholds are experimental internal-test tuning, not a release balance commitment.
- Regional content packs can be generated with a deterministic checksum manifest and atomically
  installed locally; remote delivery and publisher signing are intentionally not implemented yet.

### Open product/engineering work

- The remaining 21 regional catalogues, their reviewed evidence snapshots and final media curation are not yet available.
- The supplied region groupings do not yet assign every country/territory or marine area unambiguously.
- Rarity/prestige and Essentials/Icons are draft pilot curation; their values need continuing editorial review before release.
- Structured export, localisation and broad accessibility/device validation remain before beta.

## 3. Critical path

```text
Gate 1 GO
   ↓
Finish the in-progress regional/personal map logic
   ↓
Core beta logic and data integrity
   ↓
Content-pack lifecycle and migration resilience
   ↓
Pilot editorial freeze and beta operations
   ↓
Shared-component UI consolidation
   ↓
Three-region closed beta
   ↓
Content scale-up to all 24 regions
```

Large-scale animal/media curation starts only after the generator produces deterministic output and useful diffs. The runtime targets 24 regions from the start; the first beta proves three curated regions.

### Replanned delivery slices — logic first

The work below is ordered by dependency, not calendar dates. At the current part-time pace, use each slice as a decision point: do not begin the next slice until its exit criteria hold. Only the minimum standard Material UI needed to exercise a new logic path belongs in Slices 1–4; the final shared-component redesign is deliberately deferred to Slice 5.

| Slice | Scope | Main exit criteria | Indicative effort |
|---|---|---|---:|
| 1. Finish map logic | Complete the local Field Atlas integration: regional completion projection, independent privacy-safe personal layer, local assets, state handling and focused automated/device checks. | The map projects real regional state correctly, does not depend on personal coordinates, and remains useful offline. | Remaining 2–5 focused days |
| 2. Core beta integrity | Implement conservative due-observation background batching, retry/backoff and idempotency; add structured local-data export; simulate and freeze beta progression thresholds. | Sync never creates duplicate rewards or unsafe request bursts; exported data is complete for Wildlife-owned data; progression rules are frozen for beta. | 8–15 focused days |
| 3. Content-pack resilience | Build pack version/install/remove/storage/migration behavior; test catalogue, taxonomy and boundary-version migrations plus offline recovery. | A content update or pack removal never loses observation history or earned progression. | 8–15 focused days |
| 4. Pilot release logic and editorial freeze | Finish pilot catalogue, rarity/prestige, achievement and media-provenance reviews; add beta instrumentation and exercise operational failure paths. | All three pilot packs are reproducible, attributable and measurable against Gate 2 metrics. | 8–12 engineering days + curation |
| 5. Shared UI consolidation | Apply the final reusable Field Guide components and perform localisation, accessibility and representative-device validation. | The tested beta logic is exposed consistently, accessibly and without duplicate screen implementations. | 10–18 focused days |
| 6. Closed beta, then scale | Run Gate 2, correct evidence-backed issues, then curate and release the remaining 21 regions through the same pipeline. | Beta gates are met before each additional region is published. | Ongoing; scale-up is 5–10 engineering days + curation |

Pilot editorial review can proceed alongside Slices 2 and 3, but it must not bypass their pack-validation and migration exit criteria. Do not begin broad UI polish or manual curation of all 24 catalogues ahead of those logic foundations.

## 4. Phase A — Observation UX separation — Complete (21 August 2026)

**Effort: 5–8 focused days**

1. Add a focused Observations route, reached from Home and Species Detail.
2. Move draft history, pending public confirmation, candidates, lifecycle status, retry, map visibility and local deletion out of Capture.
3. Keep Capture limited to creating exactly one new observation.
4. Present multiple photos as one observation container with a horizontal strip and “1 observation · N photos” copy.
5. Add explicit “Open iNaturalist to finish uploading” recovery when a submitted handoff is still not public.
6. Preserve the permanent read-only boundary: no edit/delete action for iNaturalist records.

**Exit criteria:** Capture has one job; every persisted observation state is discoverable from Observations; single/multiple-photo, pending, ambiguous, offline and error states pass tests and device checks.

## 5. Phase B — Regional foundation and authoring contract — Complete (21 August 2026)

**Effort: 5–8 engineering days, plus owner review of region membership**

1. Convert the 24 supplied region rows into stable keys and localised display names.
2. Replace vague “related areas” wording with a complete ISO country/territory assignment manifest.
3. Decide and encode transcontinental, dependent-territory, offshore/marine and uncovered-location policy.
4. Acquire/review compatible local boundary data and record its source/licence/version.
5. Freeze the catalogue inclusion rules: photographable vertebrates plus restrained conspicuous fish/invertebrates.
6. Freeze the semantic separation:
   - encounter rarity: Common / Uncommon / Rare / Very Rare;
   - prestige: Standard / Legendary;
   - verification: observed / Research Grade;
   - conservation: sourced status only.

**Exit criteria:** every supported land location resolves to exactly one region; uncertain/offshore handling is explicit; the same inputs can be reviewed without reading Android code.

**Approved policy — 21 August 2026:** Turkey → Middle East and Arabia; Russia → Siberia and
Boreal Asia; Egypt → Maghreb and Sahara; Kazakhstan → Central Asia. Marine points use only
reviewed local offshore buffers; unmatched points are retained as `marine_worldwide` without
land-region XP/completion until a curated marine catalogue exists. Antarctica and other
uncovered land are retained personally but unsupported for regional progression. The Caribbean
is the third pilot.

## 6. Phase C — Catalogue toolchain — Complete (21 August 2026)

**Effort: 7–12 focused days**

Create developer-facing source manifests and a deterministic generator:

```text
catalogues/regions.yaml
catalogues/boundaries/
catalogues/taxa.yaml
catalogues/regions/<region_key>/catalogue.yaml
catalogues/regions/<region_key>/overrides.yaml
catalogues/achievements.yaml
catalogues/media_manifest.yaml
```

The toolchain must:

- add/remove a regional species without an app-code edit;
- set encounter rarity and a documented manual override;
- mark Regional Legends;
- curate exactly 10 Essentials and 5 Icons;
- validate that achievement taxa belong to the catalogue;
- deduplicate global taxon identity and reusable media;
- require source/creator/licence metadata for reusable imagery;
- generate a versioned SQLite catalogue and local boundary assets;
- emit a human-readable diff and validation report;
- fail deterministically on invalid IDs, duplicate regional ownership or incomplete manifests.

**Exit criteria:** two runs from identical sources are logically deterministic; a one-species edit produces a small understandable diff; invalid achievements/licences fail the build.

## 7. Phase D — Multi-region runtime migration — Complete (21 August 2026)

**Effort: 10–18 focused days**

1. Introduce `Region`, `CatalogueVersion`, `RegionalTaxon`, `RegionalAchievement` and `ObservationRegion` domain models.
2. Migrate `CatalogueStore` from a default Catalonia load path to explicit region/version access.
3. Keep global `TaxonDetails` and media deduplicated across catalogues.
4. Add selected/active catalogue state with manual selection and optional explicit location suggestion.
5. Implement local point-in-polygon observation assignment with boundary version and confidence.
6. Never count an observation outside the assigned region toward that catalogue.
7. Mark obscured/boundary-uncertain observations as `region_uncertain`; do not award progress/XP.
8. Migrate current Catalonia development data into the Mediterranean Europe pilot without inventing completion.

**Exit criteria:** a species present in two catalogues unlocks only the observation's region; region switching works offline; boundary/version migrations preserve observations and ledger history.

## 8. Phase E — Regional progression and achievements — Complete for the pilot v0.1 contract (21 August 2026)

**Effort: 7–12 focused days**

Implement [`progression_rules.md`](progression_rules.md) v0.2:

- retain all existing `progression-0.1-placeholder` ledger events unchanged;
- keep global first-species reward at +500;
- add first regional discovery (+100);
- add additive rarity bonuses: +0 / +50 / +150 / +300;
- add Regional Legend (+1,000) independently from rarity;
- add Essentials completion (+1,500) and Icons completion (+3,000);
- key every regional event by region, frozen catalogue version and taxon/checklist;
- never revoke earned XP/achievements after catalogue or taxonomy changes;
- resimulate levels against small, medium and highly active collections before freezing beta thresholds.

**Exit criteria:** a Common Legendary elephant is worth more than a Common Standard warthog without being labelled rare; retries never duplicate awards; an observation made in another region earns no progress here.

## 9. Phase F — Regional map and personal observations — In progress

**Effort: 5–9 focused days**

1. Add local 24-region polygons to the map.
2. Use restrained olive intensity for completion.
3. Use explicit gold marks/labels for Essentials and Icons completion.
4. Keep the current coarse personal observation cells as a separately toggleable layer.
5. Provide textual region status and an accessible legend; colour/effect is never the only state cue.
6. Avoid continuous glow/animation; use a one-time completion transition only.
7. Validate the local Field Atlas palette, selection, marks and completion motion across target devices.

**Exit criteria:** the map remains useful without personal coordinates, remains privacy-safe with obscured data and exposes every state non-visually.

## 10. Phase G — Shared-component UI and navigation improvement — Deferred until logic slices complete

**Effort: 10–18 focused days**

Build shared foundations first:

- one reusable responsive `SpeciesGrid` for Collection, Explore and achievement lists;
- `RegionSelector`;
- `RegionalProgress` and map legend;
- independent encounter-rarity and Legendary indicators;
- observation row/tile and achievement card;
- shared section headers, loading/empty/offline/error treatments.

Then improve vertical slices in this order:

1. Collection — real selected region, completion and achievements.
2. Home — current-region progress, Near Me, recent/pending observations, achievement progress and map summary.
3. Observations — focused management experience.
4. Species Detail — family/current facts, regional rarity/prestige, achievement membership, personal history and optional cached public distribution panel.
5. Explore/Near Me — selected-region catalogue and missing-nearby filter.
6. Map — region and observation layers.
7. Profile/settings — progression, installed content, privacy/export and diagnostics.

The bottom destinations remain Home, Collection, Capture, Explore and Profile. Observations, Species Detail and Map are focused secondary routes.

## 11. Phase H — Offline/local packaging

**Effort: 5–10 focused days**

- Bundle the global taxon core, all region definitions, boundaries, membership, rarity, prestige, achievements, localisation and compact thumbnails locally where size permits.
- Deduplicate species/media shared by regions.
- Keep user observation/cache/progression data local.
- Package larger regional image sets as install-time, fast-follow or user-selected local packs rather than rebuilding catalogues from live APIs.
- Keep high-resolution public distribution data and replaceable basemap tiles as online/cached enhancements.
- Add storage inventory, pack version and removal controls without deleting user progression.

## 12. Phase I — Three-region closed beta

**Effort: 8–12 focused days plus curation**

Recommended pilots:

1. Mediterranean Europe — migration/current-user path.
2. East Africa — validates easy-to-see Legendary fauna.
3. Insular Southeast Asia or Caribbean — validates island/territory boundaries and endemism.

Before beta:

- curate every pilot catalogue and its 10 Essentials/5 Icons;
- complete media licence/provenance review;
- provide structured local-data export;
- localise Catalan, Spanish and English;
- validate TalkBack, large text, offline states and representative devices;
- implement conservative batched WorkManager sync for due observations;
- test migrations and catalogue pack updates.

### Gate 2 metrics

| Metric | Target |
|---|---:|
| Handoff completion | >70% |
| Incorrect automatic matches | <1% |
| Ambiguous matches | <10%; always explicit confirmation |
| Research Grade rate at day 30 | >60% |
| Week-4 retention | >30% |
| Sightings per active user/week | >2 |
| Upstream request load | No unresolved rate-limit incidents; within conservative per-device pacing |
| Region assignment | No known false regional unlocks; uncertain cases remain unawarded |

## 13. Phase J — Scale to all 24 regions and launch

**Engineering effort: 5–10 focused days after the pipeline is proven**
**Curation effort: approximately 15–50 focused days, depending on catalogue size/media coverage**

- Complete the remaining country/territory/marine assignments.
- Curate the remaining 21 regional catalogues and checklists.
- Validate rarity/prestige and licence manifests.
- Generate/install/update packs through the same deterministic pipeline.
- Run catalogue diffs and regression checks before every release.
- Publish only regions that meet the same content/attribution quality floor as the pilots.

## 14. Deferred work

- Generic badges and streaks beyond Regional Essentials/Icons.
- Leaderboards and friend/social systems.
- Collaborator-identification XP.
- Filter-based raids.
- Bird audio.
- Weather enrichment.
- iOS / Compose Multiplatform.

These remain valuable, but none should interrupt the regional data/content critical path.

## 15. Effort summary

| Work | Engineering effort |
|---|---:|
| Observation UX | 5–8 d |
| Regional/boundary contract | 5–8 d |
| Catalogue generator | 7–12 d |
| Multi-region runtime | 10–18 d |
| Regional progression | 7–12 d |
| Map layers | 5–9 d |
| Shared UI redesign | 10–18 d |
| Offline packaging | 5–10 d |
| Beta readiness | 8–12 d |
| All-region scale-up engineering | 5–10 d |
| **Total engineering** | **~67–117 focused days** |

Catalogue, achievement, localisation and media curation add approximately **15–50 focused days**. At 12–15 hours/week, the expanded scope is roughly **7–14 months**, with a useful three-region beta substantially earlier than complete 24-region content.

## 16. Immediate next actions

1. Complete the regional/personal map logic and its focused state/device validation; keep map polish limited to what is needed to validate the behavior.
2. Implement and test conservative batched background sync, retry/backoff and idempotent reward handling.
3. Deliver structured export of Wildlife-owned local data and freeze beta progression thresholds through collection simulations.
4. Build and test the regional content-pack lifecycle: versioning, storage accounting, install/remove and safe catalogue/taxonomy/boundary migrations.
5. Continue pilot catalogue, rarity/prestige, achievement and media-provenance review in parallel with the logic slices.
6. Add beta instrumentation and operational checks, then begin the shared-component UI, localisation and broad accessibility/device pass.

Do not begin broad UI polish or manual curation of all 24 catalogues until the pilot pipeline, sync, pack migrations, assignment and map projections remain reviewable, reproducible and testable.
