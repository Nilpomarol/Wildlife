# Wildlife UI Architecture

**Status:** Adopted design and implementation contract; post-cutover content/media remediation is complete and broad UI redesign may resume
**Visual system:** Field Guide Classic
**Platform:** Android, Kotlin, Jetpack Compose, Material 3
**Source of visual truth:** [`style.md`](style.md)

## 1. Decision

Wildlife will not wait for a final whole-app redesign. The final design system is established now and applied progressively:

- New product-facing screens are built in Compose using Field Guide Classic.
- A materially changed product screen is migrated to the shared system as part of that slice.
- Existing feasibility, diagnostic and account-management screens may remain utilitarian until touched.
- A big-bang rewrite is explicitly avoided.

This keeps new work production-shaped without delaying catalogue and matching validation.

**Implementation gate closed 24 August 2026:** runtime/device evidence exposed defects outside the original synthetic benchmark; the audited remediation and production-path 25-region gate now pass. Broad UI work may resume. Pilot curation and the representative-device matrix remain release gates.

## 2. Authority and references

Use this order when resolving ambiguity:

1. Product behavior and data truth in `Wildlife_prd.md`.
2. Visual tokens and principles in `style.md`.
3. Component and migration rules in this document.
4. Reference screenshots in `docs/assets/`.

The screenshots are AI-generated mood references. They establish atmosphere, density, hierarchy, photography treatment and restraint. They do **not** define exact navigation, screen content, biological facts, feature scope or production image assets.

| Reference | What to learn from it | What not to copy literally |
|---|---|---|
| [Collection](assets/field-guide-classic-collection-reference.png) | Three-column density, image-led cards, stable silhouettes, muted chrome, compact filters | Species list, counts, rarity markers, bottom-nav behavior |
| [Species detail](assets/field-guide-classic-species-detail-reference.png) | Hero-first hierarchy, serif identity, compact facts, observation strip, calm dark surfaces | Facts, dates, images, actions or exact section order |

The AI-generated reference animals must never be shipped. Production images require an approved licence and attribution record.

## 3. Architecture boundary

UI state flows in one direction:

```text
Frozen iNaturalist/provider source inputs
          ↓
Offline deterministic global content generation
          ↓
Versioned published catalogue + direct media manifest
          ↓
Read-only content repository + bounded durable media store
          ↓
Domain projection
          ↓
Screen state
          ↓
Compose screen + reusable components
```

Composables do not make network requests, query SQLite directly or derive taxonomy rules. Activities do not own collection logic. Existing repositories and projection objects remain reusable while Activities are replaced.

### Target package structure

```text
com.wildlife.feasibility
├── data
│   ├── local
│   ├── remote
│   └── repository
├── domain
│   ├── model
│   └── projection
├── ui
│   ├── theme
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   ├── Shape.kt
│   │   ├── Spacing.kt
│   │   └── WildlifeTheme.kt
│   ├── components
│   ├── navigation
│   └── screens
│       ├── collection
│       ├── catalogue
│       ├── speciesdetail
│       ├── capture
│       └── profile
└── MainActivity.kt
```

This is a migration target, not permission for an unrelated repository-wide move. Move code when its slice is converted.

## 4. Theme contract

`WildlifeTheme` is dark-first and owns all visual primitives. Raw hexadecimal colours belong only in theme definitions.

Expose two layers:

- A Material 3 `ColorScheme` for standard controls and accessibility behavior.
- `WildlifeColors` for parchment, olive, gold, silhouette, rarity and confirmation semantics.

Likewise, centralize:

- Lora heading and Roboto body typography.
- The 4dp spacing grid.
- 8–16dp shape scale.
- Thin outline treatment and minimal elevation.
- Motion durations of 150–250ms, with reduced-motion-safe behavior.

Gold is not the general action colour. Olive communicates selection/progress; gold communicates rarity or exceptional discovery.

## 5. Small component system

Build only components that recur or carry core identity:

| Component | Contract |
|---|---|
| `WildlifeScaffold` | Background, safe system insets, top/bottom structure and snackbar host |
| `WildlifeTopBar` | Serif screen title with restrained Material actions |
| `WildlifeBottomBar` | Home, Collection, Capture, Explore and Profile; labelled 48dp targets. The four navigating destinations are tabs; Capture is a larger unfilled camera mark that launches an Activity and therefore carries a button role, never an unselectable tab role |
| `JournalChapterRail` | Shared full-width numbered section selector for Collection and Explore: natural-width chapters arranged space-between, flat page rule, mono chapter numbers, non-colour active marker, 48dp tab targets and large-text ellipsis without horizontal scrolling or swipe ownership |
| `CollectionProgress` | Regional-guide observed/total value and thin olive progress; only after a curated denominator exists |
| `RegionSelector` | Browsed-guide selection in Explore only. Collection may later filter personal history by region, but never uses this control. It never changes the location-derived current region, regional progress or full-region prefetch. `RegionPill` is the read-only current-region counterpart |
| `SpeciesGrid` | Shared responsive grid used by Collection, Explore/Near Me and achievement checklists; its optional viewport callback reports only visible entries and owns no repository or scheduling policy |
| `CollectionSearchBar` | Primary collection discovery control with compact inline collection/XP stats below it |
| `TaxonFilterRow` | Horizontally scrolling index-tab filter chips |
| `SpeciesCard` | One stable layout for observed, confirmed, pending and silhouette states |
| `SpeciesGrid` | Three columns normally; two on compact/large-text layouts; one at very large text or very narrow widths |
| `ObservationStateBadge` | Verification state only; never rarity |
| `EncounterRarityIndicator` | Common/Uncommon/Rare/Very Rare only; independent from regional standing and verification |
| `RegionalStandingIndicator` | Olive Essential and gold Icon treatments, with accessible copy that also preserves encounter rarity |
| `RegionalAchievementCard` | Progress for 10 Essentials or 5 Icons, keyed by frozen catalogue version |
| `SpeciesHero` | Licensed hero image, scrim and navigation actions |
| `SpeciesFactsGrid` | Compact reusable facts panel; hides unavailable facts rather than inventing them |
| `ObservationTile` | Image and date for the horizontal personal-history strip |
| `SectionHeader` | Consistent title and optional trailing action |
| `WildlifeLoadingState` | Shared accessible initial-loading treatment; later refreshes retain existing screen content where available |
| `CaptureButton` | Olive circular primary capture action with parchment icon |

Prefer parameters and slots over visually similar duplicate components.

## 6. Screen contracts

### Collection

Collection owns the user's personal wildlife history through three explicit sections:

- **Species** projects unique recorded collection taxa across every stored region. It never pads the personal collection with missing regional-guide entries. Region-specific rarity and standing do not decorate this all-regions projection.
- **Observations** owns the individual handoff/public-observation ledger, sync retry, candidate confirmation, public-record links and local-only deletion.
- **Map** owns the privacy-safe personal observation cells and the independently toggleable regional-progress layer.
- The screen uses one shared section selector and keeps Collection selected in the bottom index strip across all three sections.
- Search/filtering remains available for recorded species, with photograph or silhouette dominant, lazy rendering and offline behavior.
- Without a linked account, Collection keeps its structure and shows an inline link prompt or truthful empty state rather than an account wall.

A future region control in Collection is a personal-history scope filter (defaulting to all regions), not the location-derived current-region or browsed-guide selector.

### Catalogue / Explore

- Opens on the location-derived current regional catalogue at every fresh app launch. The user may
  browse another installed guide for the rest of that app session; this temporary Explore choice
  remains independent from current-region progress and background preparation.
- Shows licence-approved imagery only.
- Does not label raw observation frequency as biological rarity.
- Works unlinked and offline after the first snapshot is stored.
- Provides two explicit sections: the stored Species guide first and one-shot Near me discovery second. The guide opens by default because it works offline and without a permission prompt.
- The Species guide owns the regional identity/completion header, observed and missing plates, encounter rarity, Essentials and Icons. Its region selector changes only the guide being browsed.
- Home carries a preview of Near me: the same one-shot request and a short ranked extract, with a
  "See all" entry that opens Explore's Near me section. Near me is always scoped and decorated from
  the location-derived current catalogue, even while Explore is browsing another guide. The preview
  and section share one discovery call, so the reporting-frequency caveat is stated identically.
- Near me requests device location only after the user acts **for its first search**, intersects returned taxa with the provisional guide, labels species-count order as reporting frequency rather than rarity and includes loading, permission/location, empty and network-error states.
- **Near me results are cached** (`NearbyDiscoveryStore`), superseding the earlier rule that the search coordinate and results were never persisted. That rule was written to stop the app becoming a location tracker; re-running the search on every visit to Home turned out to sample location *more* often, not less. The replacement keeps the intent through four constraints, all of which are load-bearing:
  - The stored coordinate is **coarsened to 0.01° (~1 km)** at the store boundary, so nothing finer than a cell ever reaches disk. It exists only to answer "have I moved far enough to re-search?".
  - The automatic re-check **never prompts for permission**. With permission not already granted it does nothing, and the explicit button remains the only way in.
  - It reads a **single last-known fix**, never a location stream, and never wakes the GPS with `getCurrentLocation`. With no recent fix the cache is judged on age and month alone.
  - It runs **only when a cached answer already exists**, i.e. only after the user has explicitly asked for a nearby search at least once.
- Staleness lives in `NearbyCachePolicy`, not in the ViewModel, so the thresholds are unit-tested: re-search past **5 km** of movement (a fifth of the 25 km radius), on a **calendar-month change** (the month is part of the query), on a **radius change**, or after **7 days**. A failed or stale re-check leaves the previous answer on screen rather than replacing it with an error.
- Home renders the extract as a **carousel** of plates and Explore keeps the full ranked table.
  They share the reporting-frequency caveat, but intentionally not their top artwork source:
  Home's compact carousel is the only discovery shelf allowed to present a licence-compatible
  catalogue/provider reference photo directly, with creator and licence printed on the plate.
  The validated local catalogue asset takes precedence over the nearby API's taxon default, whose
  licence is often incompatible with distribution. Explore's
  full Near me rows retain the collection-style personal-photo/silhouette treatment.
- Nearby results use reporting counts and identity from `species_counts`, but their artwork follows
  two explicit surface contracts. Home uses a validated local catalogue photo, an eligible
  attributed provider default, a user observation assigned to the browsed region, the most
  specific validated local catalogue silhouette, its validated family assignment, then the bundled
  group mark. Explore Near me starts at the user observation and never renders either reference
  source. Reference photography does not become collection artwork.
  `iconic_taxon_name` is normalized to a group key (`taxonGroupForClass`) at parse time so the
  final offline fallback cannot silently render empty.
- Map is Collection's third section rather than an Explore section or separate bottom route. Home links to it by selecting that Collection section. It is a regional-progress and personal-history surface. It renders all bundled local regional boundaries and exposes installed-catalogue completion plus Essentials/Icons states with an accessible textual legend. Personal history remains a separately toggleable 0.1°-cell layer; it never renders exact pins and retains hidden/unavailable-location disclosure and separate Research Grade meaning.
- Debug builds provide a clearly labelled in-memory map appearance preview with representative coarse cells and pilot-region states. It must never persist, sync or mutate a user's observations, progress or map settings, and is not shown in release builds.
- My Map embeds MapLibre Native in Compose but uses a Wildlife-owned local style and display-only atlas: warm water, neutral land, opaque olive completion, regional outlines, selection and owner-supplied Essential/Icon sprites. It makes no basemap tile request. The 13.7 MB Natural Earth-derived boundary asset remains the assignment source of truth; a separately generated, dissolved and topology-preserving simplified atlas is used only for display. Region selection and all observation-cell projection remain on device.

### Observations

- This is Collection's second section for general long-term observation management; Capture owns only creation of a new draft. A focused route remains for species-filtered/deep-linked entry and keeps the bottom bar.
- Shows Draft, Pending public confirmation, Candidate ready, Needs ID, Research Grade, unavailable and recoverable-error states.
- Supports retrying Wildlife sync, explicit candidate confirmation, opening the public iNaturalist record, local map visibility and deletion of Wildlife-owned local state.
- One observation with several photos is rendered as one observation container with a horizontal photo strip.
- Home exposes recent/pending observations and a “See all” entry. Profile links to it from the sync-status card. Species Detail opens the route filtered to that species.
- Sync is an action on this screen, where its results are visible. Profile reports sync status read-only and hands the user here; it does not own the retry.
- It contains no iNaturalist edit/delete action.

### Species detail

- Leads with approved wildlife photography.
- Reads the published taxon's identity, short sourced description, conservation snapshot, regional context and selected media manifest locally. Those immutable fields never wait for or accept a runtime overlay.
- Uses the authoring-selected, licence-verified reference image manifest first. It shows the bundled group silhouette and any validated local thumbnail immediately, then issues the direct stored detail-variant request when that file is absent.
- Keeps the shared bundled `TaxonSilhouette` mounted underneath the hero media layers. A published
  taxon uses its frozen specific and family roles in specific → family → bundled-group order on
  Collection, Explore, Near Me and Detail. Each locally validated role becomes usable immediately;
  an unavailable or not-yet-downloaded specific role never withholds the family fallback.
  Device-side name matching must not invent a catalogue assignment. PhyloPic resolution is an
  authoring operation for catalogue taxa and remains a runtime repair only for off-catalogue
  records. Missing published photos may still use the bounded licence-verified repair overlay.
  Retry represents a failed/incomplete downloadable stage, not provider discovery for a catalogue
  silhouette.
- Updates text, thumbnail, detail image and silhouette independently; one incomplete stage never withholds another completed stage.
- Gives the open hero absolute media-queue priority. Its photo is downloaded and projected before
  any specific-silhouette download or missing-stage repair, while the bundled group silhouette
  remains visible underneath. Whole-region thumbnail preparation must never delay this request.
- Offers a separate **Find another reference image** action when a reference photo is shown. It
  records the currently shown source in a durable per-taxon rejection history, searches for a
  different licence-compatible Wikimedia asset excluding every prior rejection, validates and
  stores the file before display, and persists the choice as a media-only overlay. A no-result or
  temporary failure leaves the current photo unchanged and is stated explicitly. Rejection records
  are included in the structured local-data export with taxon, provider, catalogue generation,
  reason and timestamp so they can feed a reviewed authoring denylist without telemetry.
- Keeps the user's sighting photos in the personal observation strip even when a curated reference image leads the page.
- Shows the reference creator/licence directly on the hero image as a tappable source credit; do not defer image attribution to the bottom of the page.
- Common name is the primary serif identity; scientific name is italic and secondary.
- Only sourced facts appear. Missing facts are omitted or explicitly unavailable.
- Personal observations and verification status are derived from the linked account cache.
- Image attribution remains reachable from the detail screen.
- Adds regional encounter rarity, catalogue/achievement membership, seasonality when sourced and the observation's valid regional unlock state.
- May show a cached public iNaturalist distribution/observation panel as an online enhancement; the local identity, collection state and personal observation strip remain available offline.
- Uses the stale-while-revalidate remote taxon store for complete off-catalogue records and for missing-photo repair on published taxa. Published projections never merge an overlay silhouette; the frozen silhouette assignment or bundled group fallback is authoritative on every catalogue surface.

### Capture and handoff

- Keeps the single-observation rule obvious.
- One draft may contain multiple photos only when they represent the same sighting; time and location validation runs before handoff.
- Preserves original photo time/location locally and lets the user repair missing metadata before handoff.
- Transfers photos only to the official iNaturalist Android app. If it is unavailable, Wildlife offers installation or the manual web uploader; it never writes through the API.
- On return, asks whether the user submitted. A submitted draft remains pending while Wildlife retries the public API for the verified immutable user ID.
- Candidate matches use account, handoff creation time, observation time and location where available. The user must explicitly inspect and confirm a candidate.
- Deletion removes only Wildlife's local record and private camera copy; it never deletes an imported original or an iNaturalist observation.
- Reward animation occurs only after a confirmed public match.

### Account, settings and diagnostics

Use standard Material 3 forms, dialogs and lists within the theme. These screens do not need custom field-guide compositions.

## 7. Photography and attribution

Every reusable published image record must contain:

```text
provider + stable provider asset ID
direct thumbnail/detail URL
source page URL
creator / attribution when the provider publishes one (never fabricate it)
licence code + licence URL
taxon ID
matched taxon name + match rank; matched catalogue taxon ID only for exact species assignments
dimensions / MIME type / expected bytes
checksum where the published variant is frozen
selection evidence and content generation
```

The content-authoring refresh resolves reference photos in this order: reviewed Wikimedia Commons Featured/Quality image associated with the taxon, licensed iNaturalist taxon default, compatible research-grade iNaturalist observation photo, then no reference photo. It resolves PhyloPic into two independent frozen roles for every published taxon: species→genus for the specific role and family→order for the family role. It records each assignment's real matched name even when one provider image is reused by several catalogue taxa. It records selected direct variants and provenance in frozen inputs before the network-independent Android content build. All authored and stored licence codes use exactly `pdm`, `cc0`, `cc-by` or `cc-by-sa`; the licence URL carries the version. Species Detail may repeat only a missing photo/download stage for its one opened taxon; grids and background catalogue loading never run provider discovery.

Explore always shows a licensed broad-group/family silhouette for an unobserved species and favours
the user's own sighting when observed. Collection remains personal. Home Near me is the sole compact
exception: its carousel may render the nearby response's licence-compatible provider photo when a
creator and licence are displayed with it. A failed direct image retains the personal-photo or local
silhouette fallback. Active-region media prefetch is resumable WorkManager work backed by a durable
priority queue; it is not an in-memory ViewModel pass. Files use atomic validated writes,
deduplication, pinning, storage accounting, LRU eviction and orphan cleanup. Media metadata lives in
one application-scoped WAL SQLite index; downloads reserve capacity transactionally but run outside
storage locks, while workers use persisted queue due times rather than generic WorkManager retry for
normal batching. Do not silently fall back to an unlicensed URL, an AI-generated animal or an
unrelated generic animal image. Full ownership and loading rules are in
[`species_content_pipeline_plan.md`](species_content_pipeline_plan.md).

User observation photos can represent observed cards subject to privacy and local caching rules. Reference catalogue photos and user photos are different sources and must remain distinguishable in data.

## 8. State model

Every product screen must design explicit states rather than treating them as exceptions:

```text
Loading
Content
Empty
Offline with cached content
Offline without cached content
Recoverable error
Unlinked account
Stale data
```

Every species card must support:

```text
Not observed
Observed, not research grade
Observed, research grade
Awaiting species-level identification
Image unavailable / silhouette
```

Rarity is orthogonal to these states.

Regional projections additionally support:

```text
Not in selected catalogue
In catalogue, not unlocked in this region
Unlocked in this region
Region assignment uncertain
Regional Essential
Regional Essential / Icon
```

Encounter rarity, regional standing, conservation, observed state and verification are independent fields.

## 9. Navigation strategy

The target product shell uses one Compose `Scaffold` and a bottom bar with Home, Collection, Capture, Explore and Profile. The centre Capture destination is visually distinct but remains a standard accessible action.

Navigation Compose owns the stable `home`, `collection`, `explore`, `observations` and `profile` routes from `MainActivity`. Every route renders the shared labelled `WildlifeBottomBar`. Capture is deliberately an action rather than a retained tab: the centre button launches the focused Compose `CaptureActivity` and returns to the previously selected shell destination. Because it can never become the selected tab, it carries a button role. Species Detail remains a focused secondary Activity outside the bottom destinations.

General Observations and Map are retained sections inside Collection rather than separate bottom destinations. Species Detail and post-handoff deep links may still open the focused observation route with a taxon filter. `ObservationsViewModel` continues to own the ledger state rather than an Activity.

Each destination owns one concept — Observations included, though it is not a bottom destination:

| Destination | Owns |
|---|---|
| Home | What is happening now: latest discovery, a Near me preview, map and observation-queue entries |
| Collection | My history: recorded Species, individual Observations and the privacy-safe Map |
| Explore | The world: regional Species guide and Near me, plus an independent browsed-guide choice |
| Observations focused route | Species-filtered or post-handoff observation management |
| Profile | Identity, progression/XP, and data & diagnostics |

A number, a control or a status line belongs to exactly one of these. Home may carry a teaser that links to the owner, but not a second copy of it.

The `observations` route is the Compose surface for Wildlife-owned handoff management: grouped
draft/handoff photos, submission acknowledgement, public-match review, retry, local-only removal,
public-record links and per-observation map visibility. Capture returns there after the
official-app handoff and remains limited to creating a new single-sighting draft. It is not an
iNaturalist editor or deletion surface.

During migration, existing Activities may still host focused workflows. Capture and Species Detail are Compose Activities outside the stable bottom destinations; account verification remains a standard themed utility flow. Do not build a custom navigation engine.

## 10. Delivery sequence

**Gate note — 24 August 2026:** the historical sequence below is retained as design-system history. The species content/media gate now passes, so continue it under the current shared-component rules.

1. Add Compose dependencies, theme tokens, fonts and baseline components.
2. Convert Collection to the real grid using licensed images and silhouettes.
3. Convert Catalogue into Explore using the same `SpeciesCard` and filters.
4. Add Species Detail and observation strip.
5. Introduce the shared bottom navigation shell.
6. Convert Home/capture return and reward moment.
7. Theme Profile/account screens with standard Material components.

Each step must leave a usable, testable app. Do not block data work on converting unrelated screens.

### Implementation status

- **Gate 1 passed — 21 August 2026:** the product owner accepted the core field loop after one week of testing. Closed beta still measures reliability; implementation work may now proceed past feasibility.
- **Content media P0 corrected — 24 August 2026:** schema v4 uses the same canonical licence codes in authoring manifests, SQLite and runtime policy; a production-path test reads every generated photo through that runtime policy. Media assignments store the real matched taxon name, permit a provider silhouette to be assigned to several catalogue taxa without conflicting provenance, and preserve genuinely absent PhyloPic creator metadata as absent. Catalogue Detail no longer merges device-discovered silhouettes, so Collection and Detail share the frozen assignment or the same bundled group fallback. The authoring resolver freezes species→genus→family→order PhyloPic candidates; provider requests never run in the Android catalogue path.
- **Content remediation complete — 24 August 2026:** production content readers use one application-scoped, generation-bound read-only repository. Regional content is loaded with bounded batch queries and a small immutable projection cache; database integrity is not rechecked for ordinary reads. Catalogue projections are side-effect free: WorkManager transitions refresh only media progress, while Explore sends bounded, debounced demand for the grid's actual visible taxa. Home/Profile, Collection, Explore, Observations and Species Detail projections are cancellable background jobs guarded against stale completion; startup maintenance is also off the UI thread and resume refresh is route-scoped. The media index and worker lifecycle are transactional and due-time driven. Schema v4 and immutable sequence-based publication validation pass the production-path region gate.
- **Complete — content pipeline Slice 3:** schema-v4 published content is checksum/integrity validated and atomically installed with rollback recovery. Collection, Explore, Species Detail and diagnostics read it through `PublishedContentRepository`; missing Detail photos may be filled only by the bounded media-repair overlay. Draft packs are debug-only.
- **Complete — content pipeline Slice 4:** published variants use direct manifest URLs and a generation-aware, checksum-validated atomic store with deduplication, pinning, storage accounting, LRU eviction, corruption repair and orphan cleanup. Species Detail upgrades its hero independently without provider discovery.
- **Complete — content pipeline Slice 5:** current-region media uses a persisted generation-scoped priority queue and unique network-constrained WorkManager chain. Work survives restart, isolates failures, applies persisted retry due times and reports aggregate state in diagnostics.
- **Complete — media operations diagnostics:** Profile reports bounded cache use/capacity and pinned count, queued versus active work, privacy-safe open-detail priority state, the next persisted retry time and aggregate failure codes. It deliberately excludes species identity, provider IDs, URLs and local paths from both the screen and copied test report.
- **Species observation density:** after user-facing detail media work, Species Detail may fetch an unauthenticated capped sample of publicly mappable research-grade iNaturalist observations. Raw observation records are reduced immediately to anonymous 1° cells; no observation IDs, users, dates or raw coordinates are stored. Snapshots expire after 30 days, retain stale data on network failure, are bounded to the 40 most recently used taxa and are labelled as observation activity rather than biological range.
- **Complete — content pipeline Slice 6:** Collection and Explore use silhouettes plus personal observation photos; published reference photography is reserved for Species Detail and comes only from validated local files. Species Detail is local-first, keeps the group silhouette beneath loading media, independently upgrades manifest assets through the direct-download queue and runs persistent targeted repair for missing media stages. `MediaPrefetchStatus` is the shared compact preparing/repair contract.
- **Implemented — content pipeline Slice 7 engineering cutover:** the mutable regional catalogue
  schema, refresh clients and legacy projection adapters are deleted. `RemoteTaxonRepository` is
  isolated to off-catalogue stale-while-revalidate content plus published media-only repairs. Automated preservation/rollback and
  production-path 25-region performance gates pass, as does one device cold-start smoke check. The
  current-region/browsed-region correction is also complete. The later post-cutover audit supersedes
  its UI-resume declaration; strict pilot promotion and the full representative-device matrix remain release gates.
- **Historical-status note:** legacy taxon/media bullets below are retained only as implementation history. Where they conflict with the content-pipeline plan or the Slice 7 status above, the newer pipeline is authoritative.
- **Complete:** shared `SpeciesGrid` reads the regional encounter-rarity field and achievement membership, which are independent of each other. Its shared Field Marks use encounter traces plus Essential and Icon stamps; rarity, standing and verification remain independent.

- **Complete:** Compose compiler/dependencies, dark Field Guide Classic theme, semantic colours, centralized typography/shapes/spacing, safe-edge scaffold, compact filter tabs, summary panel and reusable image-led species card.
- **Complete:** Collection migrated from programmatic Views to Compose and verified on device with the real 57-entry / 89-observation cache. It uses real user observation photographs, accessible microscope icons for research-grade observations, near-square 3-column cards with 2-column and 1-column large-font fallbacks, search and filters, and actionable unlinked/error plus explicit empty/filter-empty states. The oversized summary panel was removed in favour of compact truthful stats and stored/sync context; no completion percentage appears before the catalogue denominator is curated.
- **Complete:** Lora is bundled under the SIL Open Font License for display headings; sans-serif remains the compact UI/body face.
- **Complete:** Species Detail uses a shared local taxon cache and lazily refreshes public iNaturalist taxon metadata. It can show scientific/common names, group, family, Wikipedia summary and global IUCN status for observations outside the regional catalogue without OAuth. Cached content remains available offline and enrichment failures are non-blocking.
- **Complete:** Explore reads the selected installed regional catalogue with the shared Compose grid, offline/error states, regional search, observed/not-observed discovery filters and responsive taxonomic filters. The observed state is regional: sightings assigned to another region do not unlock the selected guide. Unobserved taxa remain silhouettes. Observed cards lead with the user's sighting and can fall back to an attributed stored reference.
- **Complete:** Species Detail is internally reachable from Collection and Explore and receives the originating current/browsed region explicitly. Its discovery state is resolved from observations assigned to that region and the screen shows encounter rarity and Essentials/Icons membership separately. It also provides an image-led identity, truthful local facts, compatible CC0/CC BY/CC BY-SA reference imagery with attribution, explicit missing-data states and links back to the corresponding iNaturalist records.
- **Superseded implementation:** the earlier device resolver searched PhyloPic by species, genus, family and order for Detail only. Schema v4 moves that policy to authoring and forbids its overlay result from changing a published catalogue projection. If an iNaturalist default photo is unusable, bounded missing-photo repair may still select an explicitly compatible alternative and records its direct source and recovery status.
- **Current catalogue scope:** Explore uses the bundled pilot catalogues for Mediterranean Europe, East Africa and the Caribbean. Their curated contents prioritise mammals, birds, reptiles, fish and amphibians; plants, fungi and invertebrates are outside this pilot scope.
- **Complete:** Shared Navigation Compose shell with Home, Collection, central Capture action, Explore and Profile. Home shows truthful local collection/catalogue/queue state; Profile exposes the existing account flow and permanent read-only boundary. Collection and Explore retain their screen/filter state when switching destinations.
- **Complete:** Capture and the post-handoff return/reward moment migrated to Field Guide Classic Compose. It supports camera/gallery drafts, EXIF metadata repair, one-observation validation, official-app handoff, explicit submitted/not-submitted return state, public-API retry and candidate confirmation, local-only deletion, no-app fallback and a reward moment gated by successful public confirmation.
- **Complete:** Core sync no longer depends on a local or hosted Wildlife backend. Android directly reads public APIs through an on-device repository, stores observation/catalogue/taxon projections in SQLite and records confirmation XP in an idempotent local ledger. The existing regional catalogue is reused until an explicit refresh. Stored screens remain usable offline. A future social service is optional and outside this core data path.
- **Complete:** Species Detail separates curated reference media from personal sightings. Media pipeline v9 inspects eligible images across the linked article, uses a taxon-verified Wikidata scientific-name fallback, requires creator metadata for every reusable photo, paces and retries Wikimedia requests, and stores selected files outside Android's disposable cache while retaining attribution and repair URLs. Off-catalogue silhouette pipeline v3 stores raster files durably and does not let a broad or stale result replace a closer current match; published catalogue silhouettes use only schema-v4 assignments.
- **Complete:** Catalogue storage schema v8 journals refresh attempts independently from the last complete snapshot. Catalogue and taxon-detail replacement is one SQLite transaction, a failed/interrupted refresh leaves the stored guide readable, and Explore requires confirmation before a refresh. Provisional revision IDs hash the stable taxon denominator instead of the fetch date. App-owned reusable media has a 384 MiB ceiling, retains valid files across catalogue refreshes and removes only abandoned temporary downloads older than 24 hours. The curated release catalogue/media pack remains a launch-content deliverable rather than something generated silently on each device.
- **Complete:** Public JSON networking is routed through one injectable read-only client with per-service pacing, bounded 429/5xx and network retries, stable typed failures, shared URL encoding and identifying headers. iNaturalist, Wikimedia and PhyloPic retain separate biological/media parsing policies but no longer maintain separate connection machinery. Photo and silhouette lookup use the same typed resolution outcome, allowing a temporary earlier failure to preserve better stored media without being confused with a clean no-result. Reference-image display and durable downloads share one request identity. Explore and the Species Detail hero no longer overlay redundant “not observed” or “representative silhouette” text; exact/representative rank remains in accessibility and source credits.
- **Complete:** Media presentation distinguishes personal photos, attributed reference photos and exact/representative silhouettes in accessibility text. Explore cards recover in order from a personal photo to stored and remote attributed references before using a silhouette. Species Detail offers an explicit retry when reference media or enrichment fails. Collection, Explore and Species Detail include large-text previews; the shared grid reduces to one column at very large font scales, and compact collection statistics stack instead of clipping.
- **Complete:** Final lifecycle hardening commits catalogue refresh success in the same SQLite transaction as the replacement snapshot, serializes durable media writes against the shared storage ceiling, rejects invalid existing files, and distinguishes retryable transport failures from permanent failures. Only temporary failures preserve an earlier media result and leave its pipeline stage incomplete.
- **Field validation accepted for Gate 1:** real-world testing is sufficient for the product go decision. This does not replace the larger closed-beta reliability sample or permit ambiguous matches to auto-confirm.
- **Complete:** Account linking migrated from programmatic Views to standard themed Material 3 Compose. The Activity coordinates the existing public bio-code verification and immutable user-ID store while the reusable screen covers username entry, pending instructions, selectable/copyable code, external profile actions, loading/status feedback, expiry, verified and unlink-confirmation states. Start, pending and verified previews plus projection tests cover the state contract.
- **Complete:** Placeholder progression v0.1 centralizes enabled XP values and level thresholds, migrates existing ledger events without rewriting points, projects lifetime XP into a level, and adds a Profile surface with progress, recent reward sources and selectable earned titles. Rarity, badges and streaks remain disabled. Research Grade XP is enabled only through the idempotent lifecycle transition described below.
- **Complete:** Foreground observation lifecycle synchronization persists public quality changes, distinguishes Wildlife match confirmation from iNaturalist quality, exposes last checked/stale/syncing/error/retry state, recomputes durable pending handoffs after restart and repairs confirmed ledger writes. A previously known observation that first transitions to Research Grade records one `research_grade:<UUID>` event. The future background policy is per observation: new/Needs ID records remain eligible, Research Grade leaves routine refresh after one confirmation pass, manual and rare reconciliation remain available, and due records are batched rather than fetched one by one. Gate 1 now permits implementing conservative WorkManager batching after the observation-management slice.
- **Complete:** Explore discovery/map foundation adds an explicit one-shot Near me query. My Map is a field atlas with 24 dissolved display regions, restrained completion fills, owner-supplied Essential/Icon marks, selectable regional status and an accessible legend. The regional-progress and privacy-safe coarse observation layers toggle independently. The local atlas works without external map tiles; accurate assignment continues to use the separate full-resolution versioned boundary asset. Linked, empty/unavailable-location, area-list and content states are represented. A local map-visibility override is stored separately from the replaceable iNaturalist cache, so users can exclude or restore individual observations without changing iNaturalist; obscured, unavailable and user-hidden states remain distinct in the projection and UI.
- **Complete — destination ownership revision, 26 August 2026:** Collection now owns the user's all-regions recorded Species, the Observations ledger and My Map. The former regional Collection header/checklist moved to Explore's Species guide, which retains the independent browsed-guide selector, missing silhouettes, completion, rarity and standing. Explore now contains only Species guide and Near me. Home's map and general-observation links select the corresponding Collection section; focused species/post-handoff observation entry remains available. Collection keeps regional rarity/standing off its all-regions species projection because those claims require a selected regional context.
- **Complete:** Profile exposes a retained-data inventory and privacy-safe test report using aggregate counts only. The report contract excludes login/user ID, coordinates, species labels, URLs and local paths. Confirmed local deletion clears Wildlife’s account preferences, handoff state/private capture files, observation/progression/map databases, catalogue/reference media and temporary cache, then reloads all shell projections. It does not call an iNaturalist write or deletion path. Structured user-data export remains a later privacy deliverable.

## 11. UI definition of done

A product-facing screen is complete only when:

- It uses `WildlifeTheme` and shared tokens/components.
- It contains no invented biological or progression data.
- Images have approved source and attribution metadata or use a silhouette.
- Loading, empty, error/offline and relevant observation states are handled.
- It respects system bars and 48dp interactive targets.
- It works with TalkBack labels and large font scaling; species grids can fall back to two or one column.
- Text contrast and non-colour state cues are present.
- Reusable components have representative previews where practical.
- Android unit tests pass and the debug APK builds.
- The screen is checked on a real phone-sized viewport in dark mode.

## 12. Explicit non-goals

- Pixel-perfect reproduction of AI concepts.
- AI-generated wildlife imagery in production.
- A one-shot rewrite of every Activity.
- Decorative custom drawing where Material/Compose primitives suffice.
- Fantasy-game chrome, neon feedback, paper textures or fake notebook skeuomorphism.
- Screen-specific colour palettes or component libraries.
