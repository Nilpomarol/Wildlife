"""Repeatable host-side scale gate for the immutable species content database."""

from __future__ import annotations

import json
import hashlib
import sqlite3
import statistics
import sys
import tempfile
import time
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

from catalogue_content import CoverageRows, create_schema  # noqa: E402
from generate_catalogues import insert_content, write_content_pack  # noqa: E402


TAXA = 30_000
REGIONS = 25
MEMBERSHIPS_PER_REGION = 2_000
THUMBNAIL_WIDTH = 256
DETAIL_WIDTH = 1_280


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(len(ordered) * fraction))]


def measure(
    database: sqlite3.Connection,
    statement: str,
    parameters: tuple[object, ...],
    iterations: int,
) -> dict[str, float | int]:
    database.execute(statement, parameters).fetchall()
    durations = []
    rows = 0
    for _ in range(iterations):
        started = time.perf_counter_ns()
        result = database.execute(statement, parameters).fetchall()
        durations.append((time.perf_counter_ns() - started) / 1_000_000)
        rows = len(result)
    return {
        "rows": rows,
        "iterations": iterations,
        "p50_ms": round(statistics.median(durations), 3),
        "p95_ms": round(percentile(durations, 0.95), 3),
        "max_ms": round(max(durations), 3),
    }


def measure_cold_open(path: Path, iterations: int = 12) -> dict[str, float | int]:
    durations = []
    for _ in range(iterations):
        started = time.perf_counter_ns()
        database = sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True)
        try:
            self_check = database.execute("PRAGMA quick_check").fetchone()[0]
            generation = database.execute(
                "SELECT generation_id,generation_sequence,schema_version FROM content_generation"
            ).fetchone()
            if self_check != "ok" or generation != ("synthetic-scale-v3", 1, 3):
                raise RuntimeError("cold-open validation failed")
        finally:
            database.close()
        durations.append((time.perf_counter_ns() - started) / 1_000_000)
    return {
        "iterations": iterations,
        "p50_ms": round(statistics.median(durations), 3),
        "p95_ms": round(percentile(durations, 0.95), 3),
        "max_ms": round(max(durations), 3),
    }


def measure_all_region_projections(database: sqlite3.Connection, iterations: int = 5) -> dict[str, float | int]:
    durations = []
    projected_taxa = 0
    projected_media_variants = 0
    for _ in range(iterations):
        started = time.perf_counter_ns()
        taxa = 0
        variants = 0
        for number in range(1, REGIONS + 1):
            region = f"synthetic_{number:02d}"
            taxa += len(database.execute(
                """SELECT t.taxon_id,t.scientific_name,t.rank,t.taxonomy_json,
                           COALESCE(
                             (SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale=?),
                             (SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale='en'),
                             t.scientific_name)
                    FROM regional_taxon rt JOIN taxon t ON t.taxon_id=rt.taxon_id
                    WHERE rt.region_key=? ORDER BY rt.sort_order""",
                ("en", region),
            ).fetchall())
            database.execute(
                """SELECT td.taxon_id,td.locale,td.summary FROM regional_taxon rt
                    JOIN taxon_description td ON td.taxon_id=rt.taxon_id WHERE rt.region_key=?""",
                (region,),
            ).fetchall()
            database.execute(
                """SELECT tc.taxon_id,tc.status FROM regional_taxon rt
                    JOIN taxon_conservation tc ON tc.taxon_id=rt.taxon_id WHERE rt.region_key=?""",
                (region,),
            ).fetchall()
            variants += len(database.execute(
                """SELECT ma.asset_id,mv.variant,mv.direct_url,mv.expected_bytes
                    FROM regional_taxon rt JOIN media_asset ma ON ma.taxon_id=rt.taxon_id
                    LEFT JOIN media_variant mv ON mv.asset_id=ma.asset_id
                    WHERE rt.region_key=? ORDER BY rt.sort_order,ma.media_type,ma.asset_id,mv.variant""",
                (region,),
            ).fetchall())
        durations.append((time.perf_counter_ns() - started) / 1_000_000)
        projected_taxa = taxa
        projected_media_variants = variants
    return {
        "regions": REGIONS,
        "taxa_rows": projected_taxa,
        "media_variant_rows": projected_media_variants,
        "iterations": iterations,
        "p50_ms": round(statistics.median(durations), 3),
        "p95_ms": round(percentile(durations, 0.95), 3),
        "max_ms": round(max(durations), 3),
    }


def build_fixture(path: Path) -> sqlite3.Connection:
    database = sqlite3.connect(path)
    create_schema(database)
    regions = [
        {"key": f"synthetic_{number:02d}", "order": number, "names": {"en": f"Synthetic {number}"}}
        for number in range(1, REGIONS + 1)
    ]
    catalogues = []
    for number in range(1, REGIONS + 1):
        region = f"synthetic_{number:02d}"
        ids = [((number * 997 + position) % TAXA) + 1 for position in range(1, MEMBERSHIPS_PER_REGION + 1)]
        catalogues.append(
            {
                "region": region,
                "version": "v1",
                "status": "frozen",
                "content_rules_version": "synthetic-v1",
                "taxa": [
                    {
                        "taxon_id": taxon_id,
                        "encounter_rarity": "common",
                        "seasonality": {"en": "year-round"},
                        "inclusion_provenance": "synthetic scale fixture",
                    }
                    for taxon_id in ids
                ],
                "achievements": {"essentials": ids[:10], "icons": ids[10:15]},
            }
        )
    content = {
        "generation_id": "synthetic-scale-v3",
        "generation_sequence": 1,
        "source_digest": "c" * 64,
        "generated_at": "2026-08-24T00:00:00Z",
        "minimum_app_version": 1,
        "taxa": [
            {
                "taxon_id": taxon_id,
                "rank": "species",
                "scientific_name": f"Synthetic taxon {taxon_id}",
                "accepted_taxon_id": taxon_id,
                "common_names": {"en": f"Synthetic species {taxon_id}"},
                "taxonomy": {"class": "Aves"},
                "source_url": f"https://www.inaturalist.org/taxa/{taxon_id}",
                "source_revision": "synthetic-v1",
            }
            for taxon_id in range(1, TAXA + 1)
        ],
        "descriptions": CoverageRows([], frozenset(), frozenset()),
        "conservation": CoverageRows([], frozenset(), frozenset()),
        "taxon_changes": [],
        "regions": regions,
        "catalogues": catalogues,
        "media_assets": [
            {
                "asset_id": f"commons:synthetic:{taxon_id}",
                "taxon_id": taxon_id,
                "media_type": "photo",
                "provider": "wikimedia_commons",
                "provider_asset_id": f"File:Synthetic_{taxon_id}.jpg@1",
                "source_url": f"https://commons.wikimedia.org/wiki/File:Synthetic_{taxon_id}.jpg",
                "creator": "Synthetic benchmark author",
                "licence_code": "cc-by-sa",
                "licence_url": "https://creativecommons.org/licenses/by-sa/4.0/",
                "assessment": "quality",
                "matched_taxon_id": taxon_id,
                "matched_taxon_name": f"Synthetic species {taxon_id}",
                "match_rank": "species",
                "source_revision": "synthetic-v1",
                "variants": [
                    {"variant": "thumbnail", "direct_url": f"https://upload.wikimedia.org/synthetic/{taxon_id}-{THUMBNAIL_WIDTH}.jpg", "mime_type": "image/jpeg", "width": THUMBNAIL_WIDTH, "height": 192, "expected_bytes": 56_710, "content_sha256": "a" * 64},
                    {"variant": "detail", "direct_url": f"https://upload.wikimedia.org/synthetic/{taxon_id}-{DETAIL_WIDTH}.jpg", "mime_type": "image/jpeg", "width": DETAIL_WIDTH, "height": 960, "expected_bytes": 638_459, "content_sha256": "b" * 64},
                ],
            }
            for taxon_id in range(1, TAXA + 1)
        ],
    }
    insert_content(database, content, release=True)
    database.commit()
    if database.execute("PRAGMA foreign_key_check").fetchall():
        raise RuntimeError("production-path scale fixture has foreign-key errors")
    database.execute("VACUUM")
    return database


def media_baseline() -> dict[str, int]:
    database = sqlite3.connect(ROOT / "catalogues" / "generated" / "catalogue.sqlite")
    try:
        rows = database.execute(
            "SELECT variant, AVG(expected_bytes) FROM media_variant WHERE expected_bytes IS NOT NULL GROUP BY variant"
        ).fetchall()
        averages = {variant: round(value) for variant, value in rows}
    finally:
        database.close()
    thumbnail = averages.get("thumbnail", 0)
    detail = averages.get("detail", 0)
    return {
        "observed_average_thumbnail_bytes": thumbnail,
        "observed_average_detail_bytes": detail,
        "active_region_2000_thumbnails_bytes": thumbnail * MEMBERSHIPS_PER_REGION,
        "all_30000_thumbnails_bytes": thumbnail * TAXA,
        "cache_budget_bytes": 192 * 1024 * 1024,
    }


def run() -> dict[str, object]:
    with tempfile.TemporaryDirectory(prefix="wildlife-content-benchmark-") as temporary:
        path = Path(temporary) / "catalogue.sqlite"
        database = build_fixture(path)
        try:
            queries = {
                "regions": measure(database, "SELECT region_key, names_json FROM region ORDER BY display_order", (), 100),
                "active_region_taxa": measure(
                    database,
                    """SELECT rt.taxon_id,t.scientific_name,
                              COALESCE((SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale='en'),t.scientific_name),
                              rt.encounter_rarity,t.taxonomy_json
                       FROM regional_taxon rt JOIN taxon t ON t.taxon_id=rt.taxon_id
                       WHERE rt.region_key=? ORDER BY rt.sort_order""",
                    ("synthetic_25",), 40,
                ),
                "taxon_detail_and_variants": measure(
                    database,
                    """SELECT t.scientific_name,
                              COALESCE((SELECT tn.common_name FROM taxon_name tn WHERE tn.taxon_id=t.taxon_id AND tn.locale='en'),t.scientific_name),
                              ma.asset_id,mv.variant,mv.direct_url
                       FROM taxon t LEFT JOIN media_asset ma ON ma.taxon_id=t.taxon_id
                       LEFT JOIN media_variant mv ON mv.asset_id=ma.asset_id WHERE t.taxon_id=?""",
                    (25_000,), 250,
                ),
                "active_region_media_manifest": measure(
                    database,
                    """SELECT ma.asset_id, mv.variant, mv.direct_url, mv.expected_bytes
                       FROM regional_taxon rt JOIN media_asset ma ON ma.taxon_id=rt.taxon_id
                       JOIN media_variant mv ON mv.asset_id=ma.asset_id
                       WHERE rt.region_key=? ORDER BY rt.sort_order, ma.asset_id, mv.variant""",
                    ("synthetic_25",), 30,
                ),
            }
            all_regions = measure_all_region_projections(database)
            database_size = path.stat().st_size
        finally:
            database.close()
        cold_open = measure_cold_open(path)
        database_sha256 = hashlib.sha256(path.read_bytes()).hexdigest()
        report_path = Path(temporary) / "catalogue-report.json"
        report = {
            "schema_version": 4,
            "generation_id": "synthetic-scale-v3",
            "generation_sequence": 1,
            "generated_at": "2026-08-24T00:00:00Z",
            "minimum_app_version": 1,
            "source_digest": "c" * 64,
            "database_sha256": database_sha256,
            "release_status": "release",
        }
        report_path.write_text(json.dumps(report, sort_keys=True), encoding="utf-8")
        archive = write_content_pack(path, report_path, report, Path(temporary))
        with zipfile.ZipFile(archive) as packaged:
            manifest = json.loads(packaged.read("manifest.json"))
            if set(packaged.namelist()) != {"catalogue.sqlite", "catalogue-report.json", "manifest.json"}:
                raise RuntimeError("production scale pack has unexpected entries")
            if manifest["generation_sequence"] != 1 or manifest["content_schema_version"] != 3:
                raise RuntimeError("production scale pack metadata is inconsistent")
            if hashlib.sha256(packaged.read("catalogue.sqlite")).hexdigest() != database_sha256:
                raise RuntimeError("production scale pack database checksum is inconsistent")
        archive_size = archive.stat().st_size
    gates = {
        "production_pack_round_trip": True,
        "sqlite_under_45_mib": database_size <= 45 * 1024 * 1024,
        "archive_under_50_mib": archive_size <= 50 * 1024 * 1024,
        "cold_open_p95_under_1000_ms": cold_open["p95_ms"] <= 1_000,
        "active_region_taxa_p95_under_100_ms": queries["active_region_taxa"]["p95_ms"] <= 100,
        "active_region_media_p95_under_150_ms": queries["active_region_media_manifest"]["p95_ms"] <= 150,
        "all_25_regions_p95_under_5000_ms": all_regions["p95_ms"] <= 5_000,
    }
    return {
        "fixture": {
            "global_taxa": TAXA,
            "regions": REGIONS,
            "memberships_per_region": MEMBERSHIPS_PER_REGION,
            "regional_memberships": REGIONS * MEMBERSHIPS_PER_REGION,
            "media_assets": TAXA,
            "media_variants": TAXA * 2,
        },
        "storage": {"sqlite_bytes": database_size, "deflated_sqlite_bytes": archive_size},
        "queries": queries,
        "cold_open_validation": cold_open,
        "all_region_runtime_projection": all_regions,
        "media_budget": media_baseline(),
        "gates": gates,
        "passed": all(gates.values()),
        "measurement_scope": "Production schema/insertion and Android runtime query-shape host gate; representative-device startup remains a separate release gate.",
    }


def main() -> None:
    result = run()
    review = ROOT / "catalogues" / "review"
    review.mkdir(parents=True, exist_ok=True)
    (review / "content-pipeline-benchmark.json").write_text(
        json.dumps(result, indent=2) + "\n", encoding="utf-8"
    )
    lines = [
        "# Species content pipeline scale benchmark",
        "",
        result["measurement_scope"],
        "",
        f"- Fixture: {TAXA:,} global taxa, {REGIONS} regions, {MEMBERSHIPS_PER_REGION:,} taxa per region.",
        f"- SQLite: {result['storage']['sqlite_bytes'] / 1024 / 1024:.1f} MiB; deflated: {result['storage']['deflated_sqlite_bytes'] / 1024 / 1024:.1f} MiB.",
        "",
        "| Query | Rows | p50 | p95 |",
        "|---|---:|---:|---:|",
    ]
    for name, value in result["queries"].items():
        lines.append(f"| {name} | {value['rows']:,} | {value['p50_ms']:.3f} ms | {value['p95_ms']:.3f} ms |")
    media = result["media_budget"]
    lines += [
        "",
        "## Media budget",
        "",
        f"- Current validated mean thumbnail: {media['observed_average_thumbnail_bytes'] / 1024:.1f} KiB.",
        f"- A 2,000-species active region at that mean: {media['active_region_2000_thumbnails_bytes'] / 1024 / 1024:.1f} MiB.",
        f"- All 30,000 thumbnails at that mean: {media['all_30000_thumbnails_bytes'] / 1024 / 1024:.1f} MiB; these must remain evictable and must never be prefetched globally.",
        f"- Runtime cache budget: {media['cache_budget_bytes'] / 1024 / 1024:.0f} MiB.",
        "",
        "## Gates",
        "",
        *[f"- {'PASS' if passed else 'FAIL'} — {name}" for name, passed in result["gates"].items()],
        "",
    ]
    (review / "content-pipeline-benchmark.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(result, indent=2))
    if not result["passed"]:
        raise SystemExit("content pipeline performance gate failed")


if __name__ == "__main__":
    main()
