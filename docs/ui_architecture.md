# Wildlife UI Architecture

**Status:** Adopted design and implementation contract  
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
Public iNaturalist + PhyloPic APIs
          ↓
On-device repository + local database
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
| `WildlifeBottomBar` | Home, Collection, Capture, Explore and Profile; labelled 48dp targets |
| `CollectionProgress` | Region, observed/total value and thin olive progress; only after a curated denominator exists |
| `CollectionSearchBar` | Primary collection discovery control with compact inline collection/XP stats below it |
| `TaxonFilterRow` | Horizontally scrolling index-tab filter chips |
| `SpeciesCard` | One stable layout for observed, confirmed, pending and silhouette states |
| `SpeciesGrid` | Three columns normally; two on compact/large-text layouts; one at very large text or very narrow widths |
| `ObservationStateBadge` | Verification state only; never rarity |
| `RarityIndicator` | Small icon plus accessible label |
| `SpeciesHero` | Licensed hero image, scrim and navigation actions |
| `SpeciesFactsGrid` | Compact reusable facts panel; hides unavailable facts rather than inventing them |
| `ObservationTile` | Image and date for the horizontal personal-history strip |
| `SectionHeader` | Consistent title and optional trailing action |
| `CaptureButton` | Olive circular primary capture action with parchment icon |

Prefer parameters and slots over visually similar duplicate components.

## 6. Screen contracts

### Collection

The first production-style migration target.

- Displays a frozen catalogue denominator, not the raw regional occurrence pool.
- Uses the same card geometry for observed and missing species.
- Makes the species photograph or silhouette dominant.
- Separates observed, research-grade and rarity semantics.
- Supports search/filtering without turning the screen into a database table.
- Uses lazy rendering and remains usable offline.

Until the curated catalogue is frozen, the UI must say **Provisional catalogue** and must not show a misleading completion percentage.

### Catalogue / Explore

- Uses the current top-800 Catalonia snapshot as provisional occurrence data.
- Shows licence-approved imagery only.
- Does not label raw observation frequency as biological rarity.
- Works unlinked and offline after the first snapshot is stored.

### Species detail

- Leads with approved wildlife photography.
- Prefers a cached, licence-verified Wikimedia Commons Featured/Quality reference image for the hero; otherwise uses the iNaturalist taxon default, a compatible research-grade observation photo, the user's own photo, or a silhouette in that order.
- Keeps the user's sighting photos in the personal observation strip even when a curated reference image leads the page.
- Shows the reference creator/licence directly on the hero image as a tappable source credit; do not defer image attribution to the bottom of the page.
- Common name is the primary serif identity; scientific name is italic and secondary.
- Only sourced facts appear. Missing facts are omitted or explicitly unavailable.
- Personal observations and verification status are derived from the linked account cache.
- Image attribution remains reachable from the detail screen.

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

Every reusable image record must contain:

```text
source URL
creator / attribution
licence code
taxon ID
retrieval or catalogue version
```

Species Detail resolves reference media in this order: Wikimedia Commons Featured/Quality image associated with the linked Wikipedia article, then a scientific-name Wikidata fallback only when it is verified against the iNaturalist taxon ID (or is one unambiguous exact match), licensed iNaturalist taxon default, compatible research-grade iNaturalist observation photo, then the closest licensed silhouette in species, genus, family, order and broad-group order. Explore always shows a silhouette for an unobserved species. Observed Explore cards favour the user's own sighting and use an attributed reference only when that photo is missing. Collection remains personal. A temporary media failure preserves the previous reference, leaves the affected pipeline stage incomplete and retries later. Catalogue load starts a resumable on-device enrichment pass over unique biological families. Successful photos and silhouettes are downloaded to app-owned private files and their provenance is persisted in SQLite; remote source/display URLs remain available for attribution and repair. Local-file failure falls back to the remote display URL. Species Detail can supersede a family silhouette with a more specific genus/species match. Do not silently fall back to an unlicensed URL, an AI-generated animal or an unrelated generic animal image.

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

## 9. Navigation strategy

The target product shell uses one Compose `Scaffold` and a bottom bar with Home, Collection, Capture, Explore and Profile. The centre Capture destination is visually distinct but remains a standard accessible action.

Navigation Compose now owns the stable `home`, `collection`, `explore` and `profile` routes from `MainActivity`. Every route renders the shared labelled `WildlifeBottomBar`. Capture is deliberately an action rather than a retained tab: the centre button launches the focused Compose `CaptureActivity` and returns to the previously selected shell destination. Species Detail remains a focused secondary Activity outside the bottom destinations.

During migration, existing Activities may still host focused workflows. Capture and Species Detail are Compose Activities outside the stable bottom destinations; account verification remains a standard themed utility flow. Do not build a custom navigation engine.

## 10. Delivery sequence

1. Add Compose dependencies, theme tokens, fonts and baseline components.
2. Convert Collection to the real grid using licensed images and silhouettes.
3. Convert Catalogue into Explore using the same `SpeciesCard` and filters.
4. Add Species Detail and observation strip.
5. Introduce the shared bottom navigation shell.
6. Convert Home/capture return and reward moment.
7. Theme Profile/account screens with standard Material components.

Each step must leave a usable, testable app. Do not block data work on converting unrelated screens.

### Implementation status

- **Complete:** Compose compiler/dependencies, dark Field Guide Classic theme, semantic colours, centralized typography/shapes/spacing, safe-edge scaffold, compact filter tabs, summary panel and reusable image-led species card.
- **Complete:** Collection migrated from programmatic Views to Compose and verified on device with the real 57-entry / 89-observation cache. It uses real user observation photographs, accessible microscope icons for research-grade observations, near-square 3-column cards with 2-column and 1-column large-font fallbacks, search and filters, and actionable unlinked/error plus explicit empty/filter-empty states. The oversized summary panel was removed in favour of compact truthful stats and stored/sync context; no completion percentage appears before the catalogue denominator is curated.
- **Complete:** Lora is bundled under the SIL Open Font License for display headings; sans-serif remains the compact UI/body face.
- **Complete:** Species Detail uses a shared local taxon cache and lazily refreshes public iNaturalist taxon metadata. It can show scientific/common names, group, family, Wikipedia summary and global IUCN status for observations outside the regional catalogue without OAuth. Cached content remains available offline and enrichment failures are non-blocking.
- **Complete:** Catalogue migrated into Explore with the shared Compose grid, offline snapshot/error states, Catalonia search, observed/not-observed discovery filters, taxonomic group filters, responsive columns and explicit provisional/frequency wording. Unobserved taxa remain silhouettes. Observed cards lead with the user's sighting and can fall back to an attributed stored reference.
- **Complete:** Species Detail is internally reachable from Collection and Explore. It provides an image-led identity, discovered/research state, truthful local facts, personal observation strip, compatible CC0/CC BY/CC BY-SA reference imagery with attribution, explicit missing-data states and links back to the corresponding iNaturalist records.
- **Complete:** Missing Explore imagery and Species Detail heroes support attributed PhyloPic silhouettes. Catalogue sync caches broad group fallbacks; detail sync resolves species, genus, family and order in sequence and cached closer matches replace generic Explore silhouettes. If an iNaturalist default photo is unusable, detail sync searches research-grade observations for an explicitly compatible alternative and records its direct source and recovery status. The UI distinguishes exact and representative silhouettes and explains when no reusable photo was found.
- **Current catalogue scope:** The provisional Catalonia Explore snapshot is capped at 580 birds, mammals, reptiles, amphibians, butterflies and odonates. Fish remain disabled until a reviewed conspicuous-species allowlist exists; plants, fungi, moths and miscellaneous insects are outside v1.
- **Complete:** Shared Navigation Compose shell with Home, Collection, central Capture action, Explore and Profile. Home shows truthful local collection/catalogue/queue state; Profile exposes the existing account flow and permanent read-only boundary. Collection and Explore retain their screen/filter state when switching destinations.
- **Complete:** Capture and the post-handoff return/reward moment migrated to Field Guide Classic Compose. It supports camera/gallery drafts, EXIF metadata repair, one-observation validation, official-app handoff, explicit submitted/not-submitted return state, public-API retry and candidate confirmation, local-only deletion, no-app fallback and a reward moment gated by successful public confirmation.
- **Complete:** Core sync no longer depends on a local or hosted Wildlife backend. Android directly reads public APIs through an on-device repository, stores observation/catalogue/taxon projections in SQLite and records confirmation XP in an idempotent local ledger. The existing regional catalogue is reused until an explicit refresh. Stored screens remain usable offline. A future social service is optional and outside this core data path.
- **Complete:** Species Detail separates curated reference media from personal sightings. Media pipeline v9 inspects eligible images across the linked article, uses a taxon-verified Wikidata scientific-name fallback, requires creator metadata for every reusable photo, paces and retries Wikimedia requests, and stores selected files outside Android's disposable cache while retaining attribution and repair URLs. Silhouette pipeline v3 stores raster files durably, resumes family enrichment after partial failure and does not let a broad or stale result replace a closer current match.
- **Complete:** Catalogue storage schema v8 journals refresh attempts independently from the last complete snapshot. Catalogue and taxon-detail replacement is one SQLite transaction, a failed/interrupted refresh leaves the stored guide readable, and Explore requires confirmation before a refresh. Provisional revision IDs hash the stable taxon denominator instead of the fetch date. App-owned reusable media has a 384 MiB ceiling, retains valid files across catalogue refreshes and removes only abandoned temporary downloads older than 24 hours. The curated release catalogue/media pack remains a launch-content deliverable rather than something generated silently on each device.
- **Complete:** Public JSON networking is routed through one injectable read-only client with per-service pacing, bounded 429/5xx and network retries, stable typed failures, shared URL encoding and identifying headers. iNaturalist, Wikimedia and PhyloPic retain separate biological/media parsing policies but no longer maintain separate connection machinery. Photo and silhouette lookup use the same typed resolution outcome, allowing a temporary earlier failure to preserve better stored media without being confused with a clean no-result. Reference-image display and durable downloads share one request identity. Explore and the Species Detail hero no longer overlay redundant “not observed” or “representative silhouette” text; exact/representative rank remains in accessibility and source credits.
- **Complete:** Media presentation distinguishes personal photos, attributed reference photos and exact/representative silhouettes in accessibility text. Explore cards recover in order from a personal photo to stored and remote attributed references before using a silhouette. Species Detail offers an explicit retry when reference media or enrichment fails. Collection, Explore and Species Detail include large-text previews; the shared grid reduces to one column at very large font scales, and compact collection statistics stack instead of clipping.
- **Complete:** Final lifecycle hardening commits catalogue refresh success in the same SQLite transaction as the replacement snapshot, serializes durable media writes against the shared storage ceiling, rejects invalid existing files, and distinguishes retryable transport failures from permanent failures. Only temporary failures preserve an earlier media result and leave its pipeline stage incomplete.
- **Next:** theme the remaining Profile/account utility flows with standard Material 3 components, then tighten progression surfaces without changing the reward ledger.

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
