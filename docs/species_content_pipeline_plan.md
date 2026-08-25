# Wildlife — Species Content and Media Pipeline Plan

**Status:** Post-cutover engineering remediation completed 24 August 2026; broad UI redesign may resume. Pilot curation and representative-device release checks remain open.
**Date:** 24 August 2026
**Scope:** Replace the provisional species metadata, reference-media and silhouette pipeline with a deterministic system that scales to all 24 adopted regions and at least one future region.
**Authority:** Product behavior remains in [`Wildlife_prd.md`](Wildlife_prd.md); regional content rules remain in [`regional_catalogues.md`](regional_catalogues.md).

## 1. Decision

Reimplement the species-content subsystem before continuing broad UI redesign work.

The observation database, account link, region assignments, confirmation state, XP ledger and progression history remain intact. The legacy Catalonia catalogue cache, overlapping catalogue/detail tables, page-triggered Wikimedia discovery and session-only silhouette enrichment are replaced rather than migrated forward as permanent architecture.

The target must remain efficient when the 24 adopted regions are published and must allow a later twenty-fifth region without an Android schema redesign. Global taxon identity, descriptive metadata and reusable media are stored once; regional membership and game metadata reference the global taxon ID.

## 2. Required outcomes

- A released catalogue is fully browsable offline for names, taxonomy, regional rarity, standing and seasonality. Sourced description and conservation are local when available and otherwise render explicitly as unavailable.
- Opening Species Detail uses local published content immediately. It performs no discovery when both media stages are published; otherwise it repairs only the missing photo/silhouette stage in the background and caches the result.
- Direct image URLs and complete provenance are resolved during content authoring, validated before publication and stored in the generated catalogue.
- If a published image is not local, the app needs at most its direct image download. Provider search is reserved for an absent manifest stage, never for grids, startup, prefetch or complete catalogue entries.
- Current-region thumbnails are downloaded predictably in persistent, resumable background work; browsing another guide does not activate it.
- Catalogue browsing never launches one request per species.
- Shared taxa and media are deduplicated across regions.
- Content updates are versioned, checksum-validated, atomic and recoverable after interruption.
- Media storage is bounded, observable and self-cleaning; it cannot become an append-only 384 MiB dead end.
- Species outside published catalogues can still use a clearly separate, long-lived read cache populated from public iNaturalist data.

## 3. Content ownership

### Immutable published content

One generated SQLite content database contains all currently published regions and their deduplicated global taxa. It is replaced atomically as one content generation. Expected growth is dominated by text and regional membership, not image bytes, so a single database remains simpler and safer than duplicating global taxa across per-region databases.

It owns:

- stable iNaturalist taxon ID and rank;
- accepted scientific name and versioned taxon-change aliases;
- localised common names;
- taxonomy required by filtering and silhouette selection;
- short sourced description and source URL;
- global conservation snapshot, authority, source URL and retrieval date;
- region definitions and catalogue versions;
- regional membership, rarity, standing, seasonality and sort order;
- media selection records, direct variants and full provenance;
- content schema, source digest, generation ID, monotonic generation sequence and release status.

The Android/Gradle build must remain network-independent. A separate content-authoring refresh command contacts approved public sources, freezes reviewable inputs and media selections, then the normal deterministic generator validates those local inputs and produces the database.

### Mutable application data

A separate mutable database owns:

- user observations and region assignments;
- confirmation and progression ledgers;
- location-derived current region, temporary browsed region and installed-media preferences;
- media download/cache state;
- a remote taxon cache only for observations outside published content;
- retry, repair and last-access state.

Published biological content is never silently rewritten by ordinary startup or browsing.

## 4. Target data model

### Published content database

```text
ContentGeneration
  generation_id, generation_sequence, schema_version, source_digest, release_status,
  generated_at, minimum_app_version

Taxon
  taxon_id, rank, scientific_name, accepted_taxon_id,
  taxonomy_json, source_revision

TaxonName
  taxon_id, locale, common_name, is_primary

TaxonDescription
  taxon_id, locale, summary, source_url, attribution, licence,
  retrieved_at and source revision

TaxonConservation
  taxon_id, status, authority, source_url, retrieved_at

TaxonChange
  previous_taxon_id, accepted_taxon_id, change_type, source_revision

Region / CatalogueVersion / RegionalTaxon / RegionalAchievement / RegionalAchievementTaxon
  existing regional contract, keyed by region and frozen catalogue version

MediaAsset
  asset_id, taxon_id, media_type, provider, provider_asset_id,
  source_url, creator, licence_code, licence_url,
  assessment, matched_taxon_id, match_rank, source_revision

MediaVariant
  asset_id, variant, direct_url, mime_type, width, height,
  expected_bytes, content_sha256
```

`variant` initially supports `thumbnail` and `detail`. Direct URLs must be HTTPS, non-expiring and from an approved provider host. Source-page URLs are retained separately for credits and repair. A remote URL without creator/licence metadata never becomes a published media asset.

### Mutable media state

```text
MediaCacheEntry
  asset_id, variant, local_path, state, bytes, content_sha256,
  etag, last_modified, last_accessed_at, downloaded_at,
  attempt_count, next_retry_at, failure_code, pinned_scope,
  content_generation_id

MediaWorkState
  region_key, content_generation_id, stage, cursor,
  queued_count, completed_count, last_error, updated_at
```

Database rows never treat a missing file path as proof that a file exists. Every local asset is verified against the owned media root, non-zero length, decodable format and checksum when the manifest provides one.

## 5. Media selection and authoring

For each published taxon, the content toolchain selects a preferred reference photo in this order:

1. Reviewed Wikimedia Commons Featured/Quality image linked to the taxon.
2. Compatible licensed iNaturalist default photo.
3. Compatible licensed research-grade iNaturalist observation photo.
4. No reference photo.

The build-time result stores the selected provider asset ID, direct thumbnail/detail URLs, source page, creator, licence, dimensions and selection evidence. It may store one reviewed fallback asset when the preferred provider is known to be replaceable. Runtime does not repeat this selection.

Silhouettes use three explicit, stable roles:

- bundle a compact approved broad-group fallback set;
- freeze at most one specific assignment (species, then genus) for every published taxon where a
  licensed asset exists;
- independently freeze at most one family assignment (family, then order), even when a specific
  assignment exists, so it remains a real fallback rather than discarded authoring evidence;
- keep the role, actual match rank and matched taxon name in the manifest, runtime projection,
  accessibility text and credits;
- resolve validated local files instantly as specific → family → bundled group. A role that is not
  downloaded yet is skipped without blocking the next usable role.

The generator fails release output when:

- a direct URL is missing, temporary or on an unapproved host;
- the direct response is not a decodable image of the declared type;
- creator, licence or source URL is missing;
- an Essentials/Icon taxon lacks an approved photo or explicit reviewed waiver;
- a media asset points to a taxon absent from global content;
- two assets claim the same provider identity with conflicting provenance;
- a content digest or generated output is nondeterministic.

## 6. Packaging and download policy

### Bundled with the app/content database

- all published textual metadata and regional game data;
- the five broad-group silhouette vectors;
- metadata and validated remote variants for frozen specific and family silhouette assignments;
- manifests and checksums for every remote variant.

### Downloaded to durable storage

- current-region thumbnails;
- detail variants for Essentials/Icons, observed taxa and recently opened taxa;
- explicitly installed additional regional media packs.

The remaining regions do not download full media merely because the app opened. Their text remains locally browsable; Explore prepares only the visible rows of another browsed guide, while detail media is fetched when the user opens a species.

The final default storage budget must be selected from measured pilot image sizes and device tests, not inherited from the current 384 MiB constant. The cache manager must:

- reserve space before a download;
- download to a temporary file and atomically rename after validation;
- keep only one file for a shared asset/variant;
- pin bundled files and the current region's Essentials/Icons;
- evict least-recently-used unpinned detail images before thumbnails;
- remove superseded-generation or orphaned files;
- expose bytes by category, queued work and failures to local diagnostics;
- leave a usable thumbnail/silhouette when a detail image is evicted.

## 7. Runtime loading flow

### App/content start

1. Verify the installed content generation and checksum.
2. Atomically install the bundled generation only when no valid generation exists or the bundled generation is newer.
3. Open published content read-only.
4. Render catalogue text and bundled silhouettes without network access.
5. Wake the generation-scoped media queue for the location-derived current region, if available.

### Region context and browsing

1. Assign a one-shot device fix through the bundled regional boundaries; only that result may update the current region.
2. Persist whether the result is current, last-known or unavailable. Never silently choose the first guide as current.
3. Let Explore persist an independent browsed region. It never changes progress or full-region background work.
4. Enqueue full thumbnail preparation only for the current region; enqueue only visible-row thumbnails for another browsed region.
5. Continue from persisted work after process death or reboot.

### Species Detail

1. Read identity, description, conservation, regional context and selected media manifest locally.
2. Render local text and the best local thumbnail/silhouette immediately.
3. If the preferred detail image is absent, request its direct stored URL and persist the validated result.
4. Update the hero independently of all other stages.
   The open hero has absolute queue priority and is projected as soon as its validated file is
   available; silhouette preparation and missing-stage discovery continue only afterward.
5. On direct-download failure, retain local content and schedule targeted media repair; do not start provider discovery in the foreground.
6. A user-requested replacement is distinct from retry: search Wikimedia for a different
   licence-compatible source, persist a validated media-only overlay, and retain the existing
   image when no suitable alternative is found.

Taxa outside published content may use a stale-while-revalidate remote cache. That exceptional path is explicit in the repository API and never modifies published catalogue content.

## 8. Background work

Use WorkManager coordination keyed by content generation. A persisted queue, not WorkManager backoff or an in-memory ViewModel counter, owns progress and retry due times. An immediate lane drains ready batches; a separate replaceable delayed lane wakes at the queue's earliest retry/lease due time. Current-region activation changes priorities without conflating it with Explore browsing.

Priority order:

1. missing/corrupt bundled or pinned assets;
2. current-region Essentials and Icons;
3. observed taxa in the current region;
4. visible browsed-row thumbnails;
5. remaining current-region thumbnails;
6. explicitly requested detail variants or optional media packs.

Constraints and behavior:

- network required; large optional packs may require unmetered network;
- bounded concurrency and provider-aware request pacing;
- exponential retry with persisted `next_retry_at`;
- no retry loop for permanent licence/404/validation failures;
- idempotent queue insertion and downloads;
- one failed asset cannot starve later queue entries;
- shared asset completion satisfies every referencing region;
- content-generation changes invalidate queue entries, not user observations or progression.

## 9. Repository/API cutover

Introduce explicit repositories:

```text
PublishedContentRepository
  regions, regionalTaxa, taxon, description, conservation, mediaManifest

MediaRepository
  bestLocalVariant, enqueue, downloadNow, repair, inventory, evict

RemoteTaxonRepository
  off-catalogue stale-while-revalidate records + published media-only repair overlay
```

Remove from the product path:

- `syncCataloniaCatalogue` and the legacy Catalonia catalogue tables;
- duplicated global taxon fields in regional catalogue rows;
- catalogue/grid-triggered Wikimedia/Wikidata/PhyloPic selection;
- `enrichCatalogueSilhouettes` and session-only `enrichRegionalSilhouettes` batching;
- a single `updatedAtMs`/pipeline version standing in for unrelated stages;
- direct SQLite access from ViewModels;
- local inventory counts sourced from the legacy catalogue.

The app may delete the legacy species-detail/media cache during the alpha cutover after the new bundled generation has been verified. It must not delete observation history, account state, region assignments, confirmation state or XP.

## 10. Implementation slices

### Slice 1 — Schema and deterministic generator

**Implemented 24 August 2026.**

- Add the target content tables and schema/version manifest.
- Normalise global taxon identity and localised names.
- Add descriptions, conservation snapshots and taxon-change mappings.
- Add media assets and variants with direct URLs and provenance.
- Generate a deterministic database, report and human-readable diff.
- Add a synthetic scale fixture covering 25 regions, at least 30,000 global taxa and overlapping membership.

**Exit:** identical frozen inputs produce identical logical output; all schema and referential-integrity tests pass at synthetic scale.

### Slice 2 — Content refresh and media validation tooling

**Implemented and expanded 24 August 2026.** The authoring refresh froze exact records for all
2,419 published taxa and populated complete class/order/family/genus ancestry. An explicit
owner-directed alpha source-policy promotion published 2,193 sanitized, attributed English
summaries and 355 conservation records; the evidence states that prose was not individually
edited. The media resolver publishes curated Commons images first, then only redistribution-
compatible exact-taxon iNaturalist defaults, producing 381 byte-validated thumbnail/detail photo
assets with zero validation failures. All taxa retain the bundled group silhouette while exact
silhouettes remain incremental curation.

- Separate networked source refresh from the offline Android build.
- Resolve preferred and fallback assets once during authoring.
- Validate provider IDs, URLs, licences, image headers/dimensions and hashes.
- Produce review queues for missing descriptions, conservation and media.
- Add explicit waivers with reviewer/reason rather than silent gaps.

**Exit:** Android builds need no network; every published media record is renderable and attributable or carries an approved waiver.

### Slice 3 — Runtime content store and alpha migration

**Implemented 24 August 2026.** The app validates schema, integrity, generation metadata and
checksums before activation; uses a staged/backup swap with startup recovery; refuses draft packs
outside debug builds, incompatible app/schema versions, generation conflicts and downgrades; and
keeps the prior readable generation after failure. Collection, Explore, Species Detail and
diagnostics now query the typed published-content repository. Published Species Detail keeps static
content immutable but may attach a validated, persisted media-only repair when its manifest lacks a
photo or taxon-specific silhouette. The legacy species cache is deleted once only after the
replacement generation is verified, without touching observations, assignments, confirmation or
progression stores.

- Install/read the immutable content generation atomically.
- Add typed published-content queries and remove ViewModel SQLite access.
- Preserve user/game databases while deleting the legacy species cache after verification.
- Fix catalogue inventory/diagnostics to use published content.
- Reject draft/corrupt/incompatible packs in release builds and roll back interrupted swaps.

**Exit:** runtime implementation and clean-install tests pass. Release-content completeness and
device-level upgrade/interruption testing remain part of the final Slice 7 gate.

### Slice 4 — Media store and download manager

**Implemented 24 August 2026.** Published variants are stored by stable asset/variant identity in a
generation-aware private store. Downloads use manifest direct URLs and become visible only after
atomic write, exact-byte, SHA-256, MIME and image-signature validation. Repeated/shared requests
deduplicate; corrupt files self-remove; an atomic index tracks access, pinning and storage; unpinned
files use LRU eviction under the measured 192 MiB alpha budget; and generation reconciliation keeps
still-valid shared and runtime-repaired assets while deleting orphans. Species Detail independently
downloads its direct detail variant and uses the same validated store for a missing-stage repair.

- Implement atomic validated writes, checksums and deduplication.
- Add LRU eviction, pinning, orphan cleanup and storage accounting.
- Add direct thumbnail/detail downloads from manifest URLs.
- Preserve provider source pages and credit metadata independently from local-file state.

**Exit:** repeated/shared downloads are idempotent; full-storage, corruption, provider failure and generation replacement recover without blanking local catalogue content.

### Slice 5 — Persistent regional prefetch

**Implemented and hardened 24 August 2026.** A SQLite queue owns generation, region, asset/variant identity,
priority, state, attempts, retry due time and stable failure code. Insertions are idempotent; claims
are transactional; cancelled claims are released immediately and abandoned claims return after a bounded lease; generation
changes remove obsolete jobs; and a region switch deprioritises rather than deletes other-region
work or valid shared files. Generation-scoped, network-constrained immediate and delayed WorkManager
lanes process bounded sequential batches without turning ordinary batching into exponential retry. Achievement, observed, visible,
remaining-thumbnail and requested-detail priorities are explicit. Retryable failures use persisted
exponential delay and an exact next wake, permanent failures stop, and a failed prefix cannot starve later ready assets.
Startup, region selection and Species Detail repair all feed the same queue; aggregate queue state is
available in local diagnostics.

- Add the persisted priority queue and unique WorkManager orchestration.
- Resume after process death/reboot and prevent failed-prefix starvation.
- Reprioritise safely on region changes.
- Add unmetered constraints for optional large media packs.

**Exit:** an active pilot region converges to complete thumbnails without keeping the app open, request bursts or work duplication.

### Slice 6 — Product integration

**Implemented 24 August 2026.** Collection and Explore project immutable regional content and only
hand validated local reference-media URIs to UI components; manifest HTTPS URLs remain private to
the downloader. Personal observation photos remain a separate candidate and attribution contract.
The initial visible Explore set is promoted in the persistent queue, while a lifecycle-safe
WorkManager observer reprojects open grids after each work-state transition without polling.
Species Detail renders local identity/text immediately, uses an existing validated detail or
thumbnail, and independently requests the direct detail variant. The bundled group silhouette stays
underneath both asynchronous hero layers. Missing manifest stages trigger a one-taxon background
repair whose licensed metadata/file or negative outcome is persisted for 30 days without replacing
offline catalogue content. The remote store accepts that media-only overlay while published identity,
taxonomy, descriptions, conservation and regional fields remain immutable.

- Project Collection and Explore from `PublishedContentRepository`.
- Make Species Detail local-first and independently upgrade its hero.
- Keep user photos separate from reference media.
- Add the loading/offline/repair states required to validate the pipeline.

**Exit:** opening a complete published species makes zero discovery calls; an incomplete species repairs only its missing media stages and never waits for that work before showing local text and the bundled silhouette.

### Slice 7 — Cutover verification and deletion of legacy paths

**Engineering cutover completed 24 August 2026.** The mutable
`CatalogueStore`, its regional refresh tables, page-triggered catalogue/silhouette enrichment and
legacy projection models were deleted. Off-catalogue taxa now use a separate
`RemoteTaxonRepository` cache and published IDs are rejected at its network entry point. Automated
coverage now exercises real observation/progression preservation, corrupt-pack rejection,
interrupted-swap restoration, off-catalogue cache restart, queue restart/retry/region switching and
validated local-only published media. The debug suite passed 181 Android tests and 18 authoring
tests; the APK built, installed and cold-started on one connected device without a crash in 189 ms.

The repeatable 25-region benchmark uses the production schema, insertion, packaging and Android
runtime query shapes with 30,000 global taxa, 50,000 memberships, 30,000 media assets and 60,000
direct variants. It produced a 35.4 MiB SQLite database; warm host p95 was 4.233 ms for a
2,000-row regional catalogue and 7.060 ms for its 4,000 media variants. Cold open/integrity/
generation validation was 79.183 ms p95 and all 25 regional projections completed in 316.570 ms
p95. The right-sized validated thumbnail mean is 35.8 KiB: one fully photographed 2,000-species
current region projects to 70.0 MiB within the 192 MiB cache, while all 30,000 thumbnails would
still exceed 1 GiB and therefore remain evictable and never globally prefetched.
Full results are in `catalogues/review/content-pipeline-benchmark.md`.

- Run unit, integration, database, worker and device tests.
- Measure pack/query/startup and media storage behavior on representative devices.
- Remove unused schemas, clients, migration adapters and dead provenance paths.
- Update operational diagnostics and content-manager instructions.

**Exit:** the acceptance gates below pass and no product path references the legacy catalogue/media pipeline.

The pipeline exit is declared for implementation/UI sequencing. Frozen pilot promotion, low- and
mid-range device upgrade/offline/network-interruption/storage-pressure/decode checks, TalkBack and
large-font checks remain release-readiness work and must still pass before shipping.

## 11. Verification matrix

Required automated coverage:

- deterministic generation and content diffs;
- 25-region/global-taxon deduplication and query performance fixture;
- missing/duplicate taxon, invalid region/version and taxon-change cycles;
- media licence, provider-host, URL, MIME, dimensions and checksum validation;
- atomic content install, corrupt pack, interrupted swap and rollback;
- cache hit, shared asset, partial file, corrupt file, full storage and LRU eviction;
- WorkManager idempotency, retry, process restart, region switch and starvation prevention;
- offline clean launch and offline Species Detail;
- off-catalogue taxon fallback without mutation of published content;
- preservation of observations, assignments, confirmation and XP across the alpha cutover.

Required device/performance validation:

- clean install and upgrade on low/mid-range supported Android devices;
- catalogue open, filtering and region switching through the production paths at 25-region scale;
- current-region prefetch on metered/unmetered, interrupted and restored networks;
- image decode/memory behavior in dense grids and detail heroes;
- storage pressure and cache recovery;
- TalkBack and large-font behavior for all newly introduced loading/repair states.

Performance budgets must be recorded before cutover from representative-device benchmarks. Regardless of timing, these behavioral limits are mandatory:

- zero network requests to browse published catalogue text;
- zero taxon/media-discovery requests when opening a published species;
- no more than one direct request for the selected missing image variant;
- no one-request-per-species catalogue refresh path;
- no user-visible wait for one media stage before another completed stage is shown.

## 12. Completion gate and UI status

The original engineering completion declaration was reopened on 24 August 2026 after device evidence and a full code audit found runtime, media and publication defects that the original synthetic gate did not exercise. The remediation below is complete, so broad UI redesign may resume; editorial promotion and the representative-device matrix remain release work.

Remediation status:

1. **Complete — shared published-content repository:** production readers now share one generation-bound read-only database handle. Installation/integrity validation occurs when the handle is established or replaced, not before every query. Regional identity, description, conservation and media projections use four bounded queries and a small region/locale cache instead of per-taxon query cascades.
2. **Complete — media demand/state separation:** catalogue projection no longer schedules work, and WorkManager transitions update only the lightweight media summary instead of rebuilding Collection or Explore. Explore reports its real grid viewport through the shared `SpeciesGrid`; bounded, debounced visible-taxon demand is scheduled off the main thread. Routine re-enqueueing of completed or permanently failed assets is a true no-op and cannot create another worker chain; an explicit detail repair may still reset a failed job.
3. **Complete — asynchronous screen projections:** Home/Profile, Collection, Explore, Observations and Species Detail load file/SQLite projections through cancellable background jobs. A shared monotonic ownership token prevents a completed stale region/account request from replacing newer state. Startup content migration, media reconciliation and WorkManager scheduling also run off the UI thread; only the active route is refreshed on resume, and every screen has an explicit shared initial-loading state. On the validation device, cold launch fell from 6,003 ms to 970–1,238 ms across three repeated runs.
4. **Complete — transactional media index and due-time orchestration:** the whole-file JSON index is migrated once into an application-scoped WAL SQLite index with indexed lookup/LRU queries, atomic download reservations and bounded lock striping. Network transfers no longer hold the storage lock, validated unchanged files avoid repeated full checksums, and unrelated cache reads remain available during slow downloads. Workers close queue resources, release cancelled claims, recover abandoned leases, append ready batches without WorkManager backoff and schedule future retry work at the queue's persisted due time.
5. **Complete — schema/publication integrity and production-path scale gate:** content schema v3 removes duplicated names, media metadata, achievement membership and generation metadata. Canonical taxon references, regional/catalogue constraints and release/frozen semantics are enforced in generation and install validation. A monotonic `generation_sequence` plus unique generation ID governs upgrades; timestamps cannot select a pack. Release-labelled draft content is rejected. The 30,000-taxon/25-region gate now creates, inserts, packages, reopens and queries content through production code paths, and all storage/query budgets pass.

Original UI-resume conditions, retained as the target gate:

1. Slices 1–7 are complete.
2. The legacy product paths listed in §9 are removed.
3. Automated preservation, rollback, queue and media-path tests pass; remaining representative-device checks are tracked as release gates.
4. Production-path 25-region scale tests pass without schema or queue redesign.
5. The product owner accepts local-first Species Detail behavior, current-region prefetch, independent Explore browsing and storage controls.
6. `Wildlife_prd.md`, `regional_catalogues.md`, `ui_architecture.md` and `Wildlife_roadmap.md` accurately describe the shipped pipeline.

Release readiness still requires promoting each reviewed regional draft as a new frozen version,
maintaining exactly 10 editable Essentials and 5 editable Icons per promoted version, expanding
licensed photo/silhouette coverage where possible, and completing the full device/accessibility
matrix. Missing sourced descriptions or conservation assessments are reported coverage gaps, not
fake waivers or engineering blockers.
