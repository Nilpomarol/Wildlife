"""Deterministic draft/release catalogue generator.

JSON-compatible YAML sources remain dependency-free. `regions.yaml` is intentionally parsed as
its narrow reviewed subset instead of adding a runtime YAML dependency. The Android build never
contacts a network; networked source refresh is a separate authoring step.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sqlite3
import zipfile
from datetime import datetime
from pathlib import Path

try:
    from .reference_media_rejections import read_rejections, rejected_media_assignments
except ImportError:
    from reference_media_rejections import read_rejections, rejected_media_assignments

try:
    from .catalogue_content import (
        CONTENT_SCHEMA_VERSION,
        create_schema,
        fail,
        logical_database_digest,
        published_taxon_ids,
        require_schema,
        required_string,
        validate_conservation,
        validate_descriptions,
        validate_media,
        validate_taxa,
        validate_taxon_changes,
    )
except ImportError:  # Preserve direct `python tools/generate_catalogues.py` usage.
    from catalogue_content import (
        CONTENT_SCHEMA_VERSION,
        create_schema,
        fail,
        logical_database_digest,
        published_taxon_ids,
        require_schema,
        required_string,
        validate_conservation,
        validate_descriptions,
        validate_media,
        validate_taxa,
        validate_taxon_changes,
    )


ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
OUTPUT = CATALOGUES / "generated"
ANDROID_ASSETS = ROOT / "app" / "src" / "main" / "assets" / "catalogues"


def read_json_yaml(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing required source: {path.relative_to(ROOT)}")
    except json.JSONDecodeError as error:
        fail(f"{path.relative_to(ROOT)} must use JSON-compatible YAML: {error.msg}")
    if not isinstance(value, dict):
        fail(f"{path.relative_to(ROOT)} must contain an object")
    return value


def read_regions(path: Path) -> list[dict]:
    """Parse the fixed `key/order/names` subset used by the reviewed region source."""
    text = path.read_text(encoding="utf-8")
    if not re.search(r"^schema_version:\s*1\s*$", text, re.MULTILINE):
        fail("regions.yaml requires schema_version 1")
    block_pattern = re.compile(
        r"^  - key: (?P<key>[a-z0-9_]+)\s*$\n"
        r"^    order: (?P<order>[0-9]+)\s*$\n"
        r"^    names: \{ (?P<names>.+) \}\s*$",
        re.MULTILINE,
    )
    regions = []
    for match in block_pattern.finditer(text):
        names: dict[str, str] = {}
        for item in match.group("names").split(", "):
            locale, separator, value = item.partition(": ")
            if not separator or not locale or not value:
                fail(f"region {match.group('key')} has invalid localised names")
            names[locale] = value
        if "en" not in names:
            fail(f"region {match.group('key')} requires an English name")
        regions.append(
            {"key": match.group("key"), "order": int(match.group("order")), "names": names}
        )
    keys = [region["key"] for region in regions]
    orders = [region["order"] for region in regions]
    if len(regions) not in {24, 25} or len(keys) != len(set(keys)) or len(orders) != len(set(orders)):
        fail(f"expected 24 adopted regions or the supported 25th expansion region, found {len(regions)}")
    if sorted(orders) != list(range(1, len(regions) + 1)):
        fail("region display order must be unique and contiguous")
    return regions


def calculate_source_digest(paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(set(paths), key=lambda item: item.as_posix()):
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def validate(strict: bool) -> dict:
    source_paths = [
        CATALOGUES / "content_manifest.yaml",
        CATALOGUES / "regions.yaml",
        CATALOGUES / "taxa.yaml",
        CATALOGUES / "taxon_descriptions.yaml",
        CATALOGUES / "taxon_conservation.yaml",
        CATALOGUES / "taxon_changes.yaml",
        CATALOGUES / "media_manifest.yaml",
        CATALOGUES / "media_rejections.yaml",
        CATALOGUES / "achievements.yaml",
        CATALOGUES / "country_territory_assignments.yaml",
        ROOT / "tools" / "catalogue_content.py",
        ROOT / "tools" / "generate_catalogues.py",
        ROOT / "tools" / "reference_media_rejections.py",
    ]
    source_paths += sorted((ROOT / "tools" / "catalogue").glob("*.py"))
    source_paths += sorted((ROOT / "app" / "src" / "main" / "assets" / "taxon-glyphs").glob("*.svg"))
    source_paths += [
        CATALOGUES / "pipeline.json",
        CATALOGUES / "candidate_sources.yaml",
        CATALOGUES / "candidate_exclusions.yaml",
    ]
    content_manifest = read_json_yaml(CATALOGUES / "content_manifest.yaml")
    require_schema(content_manifest, "content manifest")
    generation_id = required_string(content_manifest, "generation_id", "content manifest")
    generation_sequence = content_manifest.get("generation_sequence")
    if not isinstance(generation_sequence, int) or isinstance(generation_sequence, bool) or generation_sequence <= 0:
        fail("content manifest requires a positive generation_sequence")
    generated_at = required_string(content_manifest, "generated_at", "content manifest")
    try:
        parsed_generated_at = datetime.fromisoformat(generated_at.replace("Z", "+00:00"))
        if parsed_generated_at.tzinfo is None or not generated_at.endswith("Z"):
            raise ValueError
    except ValueError:
        fail("content manifest generated_at must be an RFC 3339 UTC timestamp")
    minimum_app_version = content_manifest.get("minimum_app_version")
    if not isinstance(minimum_app_version, int) or minimum_app_version <= 0:
        fail("content manifest requires a positive minimum_app_version")

    regions = read_regions(CATALOGUES / "regions.yaml")
    known_regions = {region["key"] for region in regions}
    taxa_document = read_json_yaml(CATALOGUES / "taxa.yaml")
    require_schema(taxa_document, "taxa")
    taxa_by_id = validate_taxa(taxa_document.get("taxa", []))
    taxon_ids = set(taxa_by_id)
    descriptions = validate_descriptions(
        read_json_yaml(CATALOGUES / "taxon_descriptions.yaml"), taxon_ids
    )
    conservation = validate_conservation(
        read_json_yaml(CATALOGUES / "taxon_conservation.yaml"), taxon_ids
    )
    taxon_changes = validate_taxon_changes(
        read_json_yaml(CATALOGUES / "taxon_changes.yaml"), taxon_ids
    )
    for change in taxon_changes:
        accepted = change["accepted_taxon_id"]
        if taxa_by_id[accepted]["accepted_taxon_id"] != accepted:
            fail(f"taxon change {change['previous_taxon_id']} must point to a canonical accepted taxon")
    achievements_document = read_json_yaml(CATALOGUES / "achievements.yaml")
    require_schema(achievements_document, "achievements")
    achievements = achievements_document.get("achievements", [])

    catalogue_rows: list[dict] = []
    catalogue_sources = sorted((CATALOGUES / "regions").glob("*/catalogue.yaml"))
    if not catalogue_sources:
        fail("at least one regional catalogue source is required")
    for source in catalogue_sources:
        region = source.parent.name
        override = source.with_name("overrides.yaml")
        source_paths += [source, override]
        catalogue = read_json_yaml(source)
        require_schema(catalogue, f"{region} catalogue")
        if catalogue.get("region") != region or region not in known_regions:
            fail(f"invalid region in {source.relative_to(ROOT)}")
        version = required_string(catalogue, "catalogue_version", f"{region} catalogue")
        status = catalogue.get("status")
        if status not in {"draft", "frozen"}:
            fail(f"{region} catalogue requires status draft or frozen")
        content_rules_version = required_string(
            catalogue, "content_rules_version", f"{region} catalogue"
        )
        entries = catalogue.get("taxa", [])
        seen: set[int] = set()
        for entry in entries:
            taxon_id = entry.get("taxon_id")
            if taxon_id not in taxon_ids or taxon_id in seen:
                fail(f"{region} has an unknown or duplicate taxon_id {taxon_id}")
            if taxa_by_id[taxon_id]["accepted_taxon_id"] != taxon_id:
                fail(f"{region} uses superseded taxon_id {taxon_id}; use its accepted taxon")
            seen.add(taxon_id)
            if entry.get("encounter_rarity") not in {
                "unknown",
                "common",
                "uncommon",
                "rare",
                "very_rare",
            }:
                fail(f"{region}/{taxon_id} requires an encounter rarity")
            if not entry.get("inclusion_provenance"):
                fail(f"{region}/{taxon_id} requires inclusion provenance")
        catalogue_rows.append(
            {
                "region": region,
                "version": version,
                "status": status,
                "content_rules_version": content_rules_version,
                "taxa": entries,
            }
        )

    achievement_by_region: dict[str, dict] = {}
    for entry in achievements:
        region = entry.get("region")
        if region not in known_regions:
            fail(f"achievement source references unknown region {region}")
        if region in achievement_by_region:
            fail(f"duplicate achievement source for region {region}")
        achievement_by_region[region] = entry
    achievement_taxa: set[int] = set()
    included_catalogues: list[dict] = []
    for catalogue in catalogue_rows:
        achievement = achievement_by_region.get(catalogue["region"])
        if achievement is None or achievement.get("catalogue_version") != catalogue["version"]:
            fail(f"{catalogue['region']} requires matching achievement source")
        essentials, icons = achievement.get("essentials", []), achievement.get("icons", [])
        allowed = {entry["taxon_id"] for entry in catalogue["taxa"]}
        for key, values, required_count in (
            ("essentials", essentials, 10),
            ("icons", icons, 5),
        ):
            if len(values) != len(set(values)):
                fail(f"{catalogue['region']} has duplicate {key}")
            if not set(values).issubset(allowed):
                fail(f"{catalogue['region']} {key} contain taxa outside its catalogue")
            if strict and catalogue["status"] == "frozen" and len(values) != required_count:
                fail(f"{catalogue['region']} must have exactly {required_count} {key}")
        if set(essentials).intersection(icons):
            fail(f"{catalogue['region']} has a taxon in both achievement lists without an override")
        catalogue["achievements"] = achievement
        if not strict or catalogue["status"] == "frozen":
            included_catalogues.append(catalogue)
            achievement_taxa.update(essentials)
            achievement_taxa.update(icons)

    if strict and not included_catalogues:
        fail("strict generation requires at least one frozen regional catalogue")
    catalogue_rows = included_catalogues

    media_assets, media_taxa, direct_media_taxa = validate_media(
        media_document := read_json_yaml(CATALOGUES / "media_manifest.yaml"),
        taxa_by_id,
        achievement_taxa,
        strict,
    )
    media_rejections = read_rejections(CATALOGUES / "media_rejections.yaml")
    unknown_rejection_taxa = sorted({row["taxon_id"] for row in media_rejections} - set(taxa_by_id))
    if unknown_rejection_taxa:
        fail(f"reference-media rejections point to unknown taxa: {unknown_rejection_taxa}")
    rejection_lookup: dict[int, set[str]] = {}
    for row in media_rejections:
        rejection_lookup.setdefault(row["taxon_id"], set()).add(row["source_key"])
    rejected_assignments = rejected_media_assignments(media_assets, rejection_lookup)
    if rejected_assignments:
        fail(f"rejected reference media remains assigned: {rejected_assignments}")
    for asset in media_assets:
        taxon_id = asset["taxon_id"]
        if taxa_by_id[taxon_id]["accepted_taxon_id"] != taxon_id:
            fail(f"media asset {asset['asset_id']} is assigned to superseded taxon {taxon_id}")
        matched_taxon_id = asset.get("matched_taxon_id")
        if matched_taxon_id is not None and taxa_by_id[matched_taxon_id]["accepted_taxon_id"] != matched_taxon_id:
            fail(f"media asset {asset['asset_id']} matches superseded taxon {matched_taxon_id}")
    media_waivers = {row["taxon_id"] for row in media_document.get("waivers", [])}
    published_ids = published_taxon_ids(catalogue_rows)
    missing_descriptions = descriptions.missing(published_ids)
    missing_conservation = conservation.missing(published_ids)
    # Coverage is reported, not waived into existence. A missing sourced narrative or global
    # assessment is an honest "unavailable" state in the app; malformed supplied records still
    # fail validation above.

    assignments = read_json_yaml(CATALOGUES / "country_territory_assignments.yaml")
    if strict and assignments.get("coverage_status") != "complete":
        fail("strict generation requires complete country/territory coverage")

    return {
        "manifest": content_manifest,
        "regions": regions,
        "taxa": list(taxa_by_id.values()),
        "descriptions": descriptions,
        "conservation": conservation,
        "taxon_changes": taxon_changes,
        "media_assets": media_assets,
        "media_taxa": media_taxa,
        "direct_media_taxa": direct_media_taxa,
        "media_waivers": media_waivers,
        "media_rejections": media_rejections,
        "catalogues": catalogue_rows,
        "published_taxon_ids": published_ids,
        "achievement_taxa": achievement_taxa,
        "missing_descriptions": missing_descriptions,
        "missing_conservation": missing_conservation,
        "source_digest": calculate_source_digest(source_paths),
        "generation_id": generation_id,
        "generation_sequence": generation_sequence,
        "generated_at": generated_at,
        "minimum_app_version": minimum_app_version,
    }


def insert_content(database: sqlite3.Connection, content: dict, release: bool) -> None:
    source_digest = content["source_digest"]
    release_status = "release" if release else "draft"
    database.execute(
        "INSERT INTO content_generation VALUES (?, ?, ?, ?, ?, ?, ?)",
        (
            content["generation_id"],
            content["generation_sequence"],
            CONTENT_SCHEMA_VERSION,
            source_digest,
            release_status,
            content["generated_at"],
            content["minimum_app_version"],
        ),
    )
    for taxon in sorted(content["taxa"], key=lambda row: row["taxon_id"]):
        common_names = taxon.get("common_names", {})
        database.execute(
            "INSERT INTO taxon VALUES (?, ?, ?, ?, ?, ?, ?)",
            (
                taxon["taxon_id"],
                taxon["rank"],
                taxon["scientific_name"],
                taxon["accepted_taxon_id"],
                json.dumps(taxon.get("taxonomy", {}), ensure_ascii=False, sort_keys=True),
                taxon["source_url"],
                taxon.get("source_revision"),
            ),
        )
        for locale, common_name in sorted(common_names.items()):
            database.execute(
                "INSERT INTO taxon_name VALUES (?, ?, ?, ?)",
                (taxon["taxon_id"], locale, common_name, 1),
            )
    for row in sorted(content["descriptions"].rows, key=lambda value: (value["taxon_id"], value["locale"])):
        database.execute(
            "INSERT INTO taxon_description VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                row["taxon_id"],
                row["locale"],
                row["summary"],
                row["source_url"],
                row["attribution"],
                row["licence_code"],
                row["licence_url"],
                row["retrieved_at"],
                row.get("source_revision"),
            ),
        )
    for row in sorted(content["conservation"].rows, key=lambda value: value["taxon_id"]):
        database.execute(
            "INSERT INTO taxon_conservation VALUES (?, ?, ?, ?, ?, ?)",
            (
                row["taxon_id"],
                row["status"],
                row["authority"],
                row["source_url"],
                row["retrieved_at"],
                row.get("source_revision"),
            ),
        )
    for row in sorted(content["taxon_changes"], key=lambda value: value["previous_taxon_id"]):
        database.execute(
            "INSERT INTO taxon_change VALUES (?, ?, ?, ?)",
            (
                row["previous_taxon_id"],
                row["accepted_taxon_id"],
                row["change_type"],
                row["source_revision"],
            ),
        )
    for region in sorted(content["regions"], key=lambda row: row["order"]):
        database.execute(
            "INSERT INTO region VALUES (?, ?, ?)",
            (
                region["key"],
                region["order"],
                json.dumps(region["names"], ensure_ascii=False, sort_keys=True),
            ),
        )
    for catalogue in sorted(content["catalogues"], key=lambda row: row["region"]):
        database.execute(
            "INSERT INTO catalogue_version VALUES (?, ?, ?, ?)",
            (
                catalogue["region"],
                catalogue["version"],
                catalogue["status"] or "draft",
                catalogue["content_rules_version"],
            ),
        )
        for index, entry in enumerate(catalogue["taxa"], start=1):
            database.execute(
                "INSERT INTO regional_taxon VALUES (?, ?, ?, ?, ?, ?, ?)",
                (
                    catalogue["region"],
                    catalogue["version"],
                    entry["taxon_id"],
                    entry["encounter_rarity"],
                    json.dumps(entry.get("seasonality", {}), ensure_ascii=False, sort_keys=True),
                    index,
                    entry["inclusion_provenance"],
                ),
            )
        achievement = catalogue["achievements"]
        for achievement_type, taxon_ids in (
            ("essentials", achievement.get("essentials", [])),
            ("icons", achievement.get("icons", [])),
        ):
            database.execute(
                "INSERT INTO regional_achievement VALUES (?, ?, ?, ?)",
                (
                    catalogue["region"],
                    catalogue["version"],
                    achievement_type,
                    achievement_type,
                ),
            )
            database.executemany(
                "INSERT INTO regional_achievement_taxon VALUES (?, ?, ?, ?, ?)",
                (
                    (
                        catalogue["region"], catalogue["version"], achievement_type,
                        taxon_id, sort_order,
                    )
                    for sort_order, taxon_id in enumerate(taxon_ids, start=1)
                ),
            )
    for item in sorted(content["media_assets"], key=lambda row: row["asset_id"]):
        database.execute(
            "INSERT INTO media_asset VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                item["asset_id"],
                item["taxon_id"],
                item["media_type"],
                item["provider"],
                item["provider_asset_id"],
                item["source_url"],
                item["creator"],
                item["licence_code"],
                item.get("licence_url"),
                item.get("assessment"),
                item.get("matched_taxon_id"),
                item["matched_taxon_name"],
                item["match_rank"],
                item["source_revision"],
            ),
        )
        for variant in sorted(item.get("variants", []), key=lambda row: row["variant"]):
            database.execute(
                "INSERT INTO media_variant VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    item["asset_id"],
                    variant["variant"],
                    variant["direct_url"],
                    variant["mime_type"],
                    variant["width"],
                    variant["height"],
                    variant.get("expected_bytes"),
                    variant.get("content_sha256"),
                ),
            )


def validate_publication_revision(
    content: dict,
    release: bool,
    output_directory: Path | None = None,
) -> None:
    """Prevent generating a pack the runtime must reject as an ambiguous revision."""
    report_path = (output_directory or OUTPUT) / "catalogue-report.json"
    if not report_path.is_file():
        return
    try:
        previous = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        fail("existing generated catalogue report is unreadable; preserve or explicitly remove it")
    previous_sequence = previous.get("generation_sequence", 0)
    candidate_sequence = content["generation_sequence"]
    candidate_status = "release" if release else "draft"
    if candidate_sequence < previous_sequence:
        fail("generation_sequence cannot move backwards")
    if candidate_sequence == previous_sequence:
        unchanged = (
            previous.get("generation_id") == content["generation_id"]
            and previous.get("source_digest") == content["source_digest"]
            and previous.get("release_status") == candidate_status
            and previous.get("schema_version") == CONTENT_SCHEMA_VERSION
        )
        if not unchanged:
            fail("changed content, tooling, schema or release status requires a new generation_sequence and generation_id")
    elif previous.get("generation_id") == content["generation_id"]:
        fail("a new generation_sequence requires a new generation_id")


def write_outputs(
    content: dict,
    release: bool,
    output_directory: Path = OUTPUT,
    android_assets: Path | None = ANDROID_ASSETS,
    previous_output_directory: Path | None = None,
) -> None:
    """Write a complete pack to explicit destinations.

    The optional destinations let the unattended pipeline build and verify in an isolated
    directory before atomically publishing.  Direct invocations retain the historical paths.
    """
    validate_publication_revision(
        content,
        release,
        previous_output_directory or output_directory,
    )
    legacy_stage = output_directory.with_name("generated-staging")
    if legacy_stage.exists():
        shutil.rmtree(legacy_stage)
    if output_directory.exists():
        shutil.rmtree(output_directory)
    output_directory.mkdir(parents=True)
    database_path = output_directory / "catalogue.sqlite"
    database = sqlite3.connect(database_path)
    try:
        create_schema(database)
        insert_content(database, content, release)
        database.commit()
        foreign_key_errors = database.execute("PRAGMA foreign_key_check").fetchall()
        if foreign_key_errors:
            fail(f"generated database has foreign-key errors: {foreign_key_errors[:5]}")
        logical_digest = logical_database_digest(database)
    finally:
        database.close()

    database_sha256 = hashlib.sha256(database_path.read_bytes()).hexdigest()
    photo_taxa = {
        asset["taxon_id"] for asset in content["media_assets"] if asset["media_type"] == "photo"
    }
    silhouette_taxa = {
        asset["taxon_id"] for asset in content["media_assets"] if asset["media_type"] == "silhouette"
    }
    specific_silhouette_taxa = {
        asset["taxon_id"] for asset in content["media_assets"]
        if asset["media_type"] == "silhouette" and asset["match_rank"] in {"species", "genus"}
    }
    family_silhouette_taxa = {
        asset["taxon_id"] for asset in content["media_assets"]
        if asset["media_type"] == "silhouette" and asset["match_rank"] in {"family", "order"}
    }

    report = {
        "schema_version": CONTENT_SCHEMA_VERSION,
        "generation_id": content["generation_id"],
        "generation_sequence": content["generation_sequence"],
        "generated_at": content["generated_at"],
        "minimum_app_version": content["minimum_app_version"],
        "source_digest": content["source_digest"],
        "logical_database_digest": logical_digest,
        "database_sha256": database_sha256,
        "release_status": "release" if release else "draft",
        "global_taxa": len(content["taxa"]),
        "published_taxa": len(content["published_taxon_ids"]),
        "published_taxa_without_common_names": sum(
            1 for taxon in content["taxa"]
            if taxon["taxon_id"] in content["published_taxon_ids"] and not taxon.get("common_names")
        ),
        "descriptions": len(content["descriptions"].rows),
        "description_waivers": len(content["descriptions"].waived_taxon_ids),
        "missing_published_descriptions": len(content["missing_descriptions"]),
        "conservation_records": len(content["conservation"].rows),
        "conservation_waivers": len(content["conservation"].waived_taxon_ids),
        "missing_published_conservation": len(content["missing_conservation"]),
        "media_records": len(content["media_assets"]),
        "published_taxa_with_photos": len(content["published_taxon_ids"] & photo_taxa),
        "published_taxa_with_curated_silhouettes": len(content["published_taxon_ids"] & silhouette_taxa),
        "published_taxa_with_specific_silhouettes": len(content["published_taxon_ids"] & specific_silhouette_taxa),
        "published_taxa_with_family_silhouettes": len(content["published_taxon_ids"] & family_silhouette_taxa),
        "published_taxa_using_group_silhouettes": len(content["published_taxon_ids"] - silhouette_taxa),
        "media_direct_taxa": len(content["direct_media_taxa"]),
        "media_waivers": len(content["media_waivers"]),
        "media_rejections": len(content["media_rejections"]),
        "achievement_taxa_without_direct_media": len(
            content["achievement_taxa"] - content["direct_media_taxa"] - content["media_waivers"]
        ),
        "catalogues": [
            {
                "region": item["region"],
                "version": item["version"],
                "taxa": len(item["taxa"]),
                "essentials": len(item["achievements"].get("essentials", [])),
                "icons": len(item["achievements"].get("icons", [])),
            }
            for item in content["catalogues"]
        ],
    }
    report_path = output_directory / "catalogue-report.json"
    report_path.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (output_directory / "catalogue-diff.md").write_text(
        "# Catalogue source report\n\n"
        f"Generation: `{content['generation_id']}`\n\n"
        f"Source digest: `{content['source_digest']}`\n\n"
        f"Logical database digest: `{logical_digest}`\n\n"
        f"Published description gaps: **{len(content['missing_descriptions'])}**\n\n"
        f"Published conservation gaps: **{len(content['missing_conservation'])}**\n\n"
        f"Achievement taxa without direct media: "
        f"**{len(content['achievement_taxa'] - content['direct_media_taxa'] - content['media_waivers'])}**\n\n"
        "| Region | Version | Taxa | Essentials | Icons |\n"
        "|---|---|---:|---:|---:|\n"
        + "".join(
            f"| {item['region']} | {item['version']} | {len(item['taxa'])} | "
            f"{len(item['achievements'].get('essentials', []))} | "
            f"{len(item['achievements'].get('icons', []))} |\n"
            for item in content["catalogues"]
        ),
        encoding="utf-8",
    )
    if android_assets is not None:
        android_assets.mkdir(parents=True, exist_ok=True)
        shutil.copy2(database_path, android_assets / "catalogue.sqlite")
        shutil.copy2(report_path, android_assets / "catalogue-report.json")
    write_content_pack(database_path, report_path, report, output_directory)


def write_content_pack(
    database_path: Path,
    report_path: Path,
    report: dict,
    output_directory: Path = OUTPUT,
) -> Path:
    """Package deterministic content with corruption checks; publisher signing remains separate."""
    files = {"catalogue.sqlite": database_path, "catalogue-report.json": report_path}
    manifest = {
        "schema_version": 2,
        "content_schema_version": CONTENT_SCHEMA_VERSION,
        "generation_id": report["generation_id"],
        "generation_sequence": report["generation_sequence"],
        "generated_at": report["generated_at"],
        "minimum_app_version": report["minimum_app_version"],
        "release_status": report["release_status"],
        "source_digest": report["source_digest"],
        "files": {
            name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in files.items()
        },
    }
    pack_path = output_directory / "wildlife-content-pack.zip"
    with zipfile.ZipFile(
        pack_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as archive:
        for name, path in files.items():
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())
        info = zipfile.ZipInfo("manifest.json", date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    return pack_path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--strict", action="store_true", help="Require complete, frozen release sources."
    )
    args = parser.parse_args()
    content = validate(args.strict)
    write_outputs(content, release=args.strict)
    print(
        f"Generated deterministic {('release' if args.strict else 'draft')} "
        f"content schema v{CONTENT_SCHEMA_VERSION}: {OUTPUT.relative_to(ROOT)}"
    )


if __name__ == "__main__":
    main()
