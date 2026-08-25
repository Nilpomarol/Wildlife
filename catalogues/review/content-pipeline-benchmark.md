# Species content pipeline scale benchmark

Production schema/insertion and Android runtime query-shape host gate; representative-device startup remains a separate release gate.

- Fixture: 30,000 global taxa, 25 regions, 2,000 taxa per region.
- SQLite: 35.4 MiB; deflated: 3.8 MiB.

| Query | Rows | p50 | p95 |
|---|---:|---:|---:|
| regions | 25 | 0.048 ms | 0.090 ms |
| active_region_taxa | 2,000 | 3.425 ms | 4.233 ms |
| taxon_detail_and_variants | 2 | 0.040 ms | 0.070 ms |
| active_region_media_manifest | 4,000 | 5.684 ms | 7.060 ms |

## Media budget

- Current validated mean thumbnail: 35.8 KiB.
- A 2,000-species active region at that mean: 70.0 MiB.
- All 30,000 thumbnails at that mean: 1050.1 MiB; these must remain evictable and must never be prefetched globally.
- Runtime cache budget: 192 MiB.

## Gates

- PASS — production_pack_round_trip
- PASS — sqlite_under_45_mib
- PASS — archive_under_50_mib
- PASS — cold_open_p95_under_1000_ms
- PASS — active_region_taxa_p95_under_100_ms
- PASS — active_region_media_p95_under_150_ms
- PASS — all_25_regions_p95_under_5000_ms
