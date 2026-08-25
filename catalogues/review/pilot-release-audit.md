# Pilot release audit

Release ready: **NO**

| Region | Version | Taxa | Unknown rarity | Passed checks |
|---|---|---:|---:|---:|
| caribbean | caribbean-broad-draft-1 | 813 | 0 | 8/9 |
| east_africa | east-africa-broad-draft-1 | 931 | 0 | 8/9 |
| mediterranean_europe | mediterranean-europe-broad-draft-1 | 800 | 0 | 8/9 |

## Non-blocking content coverage

- Missing sourced descriptions: 226 of 2419 published taxa.
- Missing conservation assessments: 2064 of 2419 published taxa.
- Curated reference photos: 381 of 2419 published taxa.
- Curated silhouettes: 0 of 2419 published taxa; 2419 use the bundled group fallback.
- Missing values render as unavailable; every supplied value still must pass provenance validation.

## Blockers

- **regional_catalogues**: at_least_one_frozen_catalogue
- **generated_pack**: valid_pack_matching_current_sources
- **published_content**: content_schema_v3
