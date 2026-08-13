# Archived Wildlife sync backend prototype

The Android app no longer uses this service. Core observation sync, matching, catalogue refresh, taxon enrichment, caching and XP confirmation now run on-device. These files are retained only as validation history and a reference implementation for the earlier feasibility work.

Do not start this service or configure `adb reverse` for normal app development.

## Historical operation

This local service is the single observation-sync adapter. It caches public iNaturalist observations by immutable user ID and observation UUID, uses an overlapping `updated_since` cursor, performs a full reconciliation every 24 hours, and records XP idempotently only after Wildlife confirms a match.

Run from the repository root:

```powershell
python backend/server.py
```

For a USB-connected Android device, expose the local port once:

```powershell
adb reverse tcp:8765 tcp:8765
```

The prototype listens only on `127.0.0.1:8765`. This transport is obsolete and is not part of the production direction.

## Historical regional catalogue prototype

`POST /v1/regions/catalonia/catalogue/sync` refreshes a shared, versioned provisional snapshot for canonical iNaturalist place `12997`. `GET /v1/regions/catalonia/catalogue` reads the backend copy without contacting iNaturalist. The current development snapshot is capped at 580 research-grade species: birds (250), mammals (80), reptiles (50), amphibians (30), butterflies (120) and dragonflies/damselflies (50). It refreshes at most once every seven days. These are scope quotas ordered by regional observation count, not rarity tiers.

This is input to catalogue curation, not the final product catalogue. Raw observation frequency is not biological rarity. Fish are excluded until a reviewed allowlist can select conspicuous species without pulling in thousands of small or difficult taxa. Plants, fungi, moths and miscellaneous insects are outside v1 scope.

Catalogue sync also caches family, Wikipedia summary/source and the global IUCN Red List status returned by the public iNaturalist taxon endpoint. `POST /v1/taxa/{id}/sync` lazily refreshes one taxon and `GET /v1/taxa/{id}` reads its cached copy. This lets Species Detail enrich observations outside the Catalonia catalogue without OAuth.

Photo URLs are retained only when iNaturalist supplies an explicitly compatible licence and required attribution. Explore cards render existing user-observation photos and CC0 catalogue photos only. Species Detail may additionally render attributed CC BY and CC BY-SA photos. If the default taxon photo is unusable, detail sync searches research-grade observations of the same taxon for a compatible alternative and stores its direct iNaturalist source plus a recovery status.

PhyloPic silhouettes use a hybrid cache: one licensed broad-group fallback is resolved during catalogue sync, while detail sync tries species, genus, family and order in sequence. Wildlife accepts PDM, CC0 and CC BY assets, stores their image/node IDs and source/creator/licence metadata, labels non-species matches as representative, and preserves closer cached matches across catalogue refreshes.
