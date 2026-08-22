# Boundary authoring

`regional-boundaries-v1` will contain local, versioned polygon assets for every land region and
reviewed offshore buffer. Boundary data must record its source URL, licence, retrieval date and
source version in the generated provenance manifest.

`sources.yaml` records reviewed source candidates. Natural Earth's 1:10m Admin 0 Map Units
v5.1.1 is the adopted public-domain v1 land-boundary source. The owner-approved territory
assignment manifest and `tools/generate_regional_boundaries.py` deterministically produce the
local `generated/regional-boundaries-v1.geojson` asset and its provenance summary. The source's
de-facto treatment is retained, with disputed geometry explicitly marked in feature properties.

The generated asset deliberately contains land geometry only. No offshore buffers are shipped
until they have a separately reviewed source, licence, version and owner-approved geometry.
The generator also copies the identical GeoJSON into `app/src/main/assets/boundaries/` so the
app can perform local assignment without a live iNaturalist place lookup. During observation
sync, an assignment is written once with its boundary version and is not silently recomputed by
later syncs.

`tools/generate_map_atlas.py` separately dissolves those map units into 24 display regions,
simplifies them with topology preservation and emits representative-point label/achievement
anchors under `app/src/main/assets/atlas/`. This smaller atlas is presentation-only. It must never
be used for observation assignment or replace the full versioned boundary asset. Install its
isolated authoring dependency with `pip install -r tools/requirements-map-atlas.txt`.

Assignment order is fixed by `../region_assignment_policy.yaml`:

1. exactly one matching land polygon assigns the observation to that land region;
2. otherwise exactly one matching reviewed offshore buffer assigns it to that region;
3. otherwise a marine coordinate becomes `marine_worldwide`;
4. ambiguous, obscured or invalid coordinates are `region_uncertain` and earn no regional reward;
5. Antarctica and other unsupported land remain in personal history without regional progression.

No nearest-country or departure-country inference is permitted.
