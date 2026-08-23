"""Deterministic draft/release catalogue generator. YAML source files use JSON syntax (valid YAML)."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sqlite3
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
OUTPUT = CATALOGUES / "generated"
ANDROID_ASSETS = ROOT / "app" / "src" / "main" / "assets" / "catalogues"
PILOTS = ("mediterranean_europe", "east_africa", "caribbean")


def fail(message: str) -> None:
    raise ValueError(f"Catalogue validation failed: {message}")


def read_json_yaml(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing required source: {path.relative_to(ROOT)}")
    except json.JSONDecodeError as error:
        fail(f"{path.relative_to(ROOT)} must use JSON-compatible YAML: {error.msg}")


def region_keys() -> set[str]:
    text = (CATALOGUES / "regions.yaml").read_text(encoding="utf-8")
    keys = set(re.findall(r"^  - key: ([a-z0-9_]+)$", text, re.MULTILINE))
    if len(keys) != 24:
        fail(f"expected 24 adopted region keys, found {len(keys)}")
    return keys


def source_digest(paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(paths, key=lambda item: item.as_posix()):
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def validate(strict: bool) -> tuple[list[dict], list[dict], list[dict], str]:
    known_regions = region_keys()
    taxa = read_json_yaml(CATALOGUES / "taxa.yaml").get("taxa", [])
    media = read_json_yaml(CATALOGUES / "media_manifest.yaml").get("media", [])
    achievements = read_json_yaml(CATALOGUES / "achievements.yaml").get("achievements", [])
    taxon_ids = set()
    for taxon in taxa:
        taxon_id = taxon.get("taxon_id")
        if not isinstance(taxon_id, int) or taxon_id <= 0:
            fail("every global taxon requires a positive integer taxon_id")
        if taxon_id in taxon_ids:
            fail(f"duplicate global taxon_id {taxon_id}")
        taxon_ids.add(taxon_id)
    compatible_licences = {"CC0", "CC BY", "CC BY-SA", "Public Domain"}
    media_taxon_ids = set()
    for item in media:
        media_taxon_id = item.get("taxon_id")
        if media_taxon_id not in taxon_ids:
            fail("every media record requires a known global taxon_id")
        if not all(isinstance(item.get(key), str) and item[key].strip() for key in ("source_url", "creator", "licence_code")):
            fail(f"media for {media_taxon_id} requires source URL, creator and licence code")
        if strict and item["licence_code"] not in compatible_licences:
            fail(f"media for {media_taxon_id} has an incompatible release licence")
        media_taxon_ids.add(media_taxon_id)

    catalogue_rows: list[dict] = []
    source_paths = [CATALOGUES / "taxa.yaml", CATALOGUES / "media_manifest.yaml", CATALOGUES / "achievements.yaml"]
    for region in PILOTS:
        source = CATALOGUES / "regions" / region / "catalogue.yaml"
        source_paths += [source, source.with_name("overrides.yaml")]
        catalogue = read_json_yaml(source)
        if catalogue.get("region") != region or region not in known_regions:
            fail(f"invalid pilot region in {source.relative_to(ROOT)}")
        version = catalogue.get("catalogue_version")
        if not isinstance(version, str) or not version:
            fail(f"{region} requires a catalogue_version")
        entries = catalogue.get("taxa", [])
        seen = set()
        for entry in entries:
            taxon_id = entry.get("taxon_id")
            if taxon_id not in taxon_ids or taxon_id in seen:
                fail(f"{region} has an unknown or duplicate taxon_id {taxon_id}")
            seen.add(taxon_id)
            if entry.get("encounter_rarity") not in {"unknown", "common", "uncommon", "rare", "very_rare"}:
                fail(f"{region}/{taxon_id} requires an encounter rarity")
            if not entry.get("inclusion_provenance"):
                fail(f"{region}/{taxon_id} requires inclusion provenance")
        catalogue_rows.append({"region": region, "version": version, "status": catalogue.get("status"), "taxa": entries})
    achievement_by_region = {entry.get("region"): entry for entry in achievements}
    for catalogue in catalogue_rows:
        achievement = achievement_by_region.get(catalogue["region"])
        if achievement is None or achievement.get("catalogue_version") != catalogue["version"]:
            fail(f"{catalogue['region']} requires matching achievement source")
        essentials, icons = achievement.get("essentials", []), achievement.get("icons", [])
        allowed = {entry["taxon_id"] for entry in catalogue["taxa"]}
        for key, values, required_count in (("essentials", essentials, 10), ("icons", icons, 5)):
            if len(values) != len(set(values)):
                fail(f"{catalogue['region']} has duplicate {key}")
            if not set(values).issubset(allowed):
                fail(f"{catalogue['region']} {key} contain taxa outside its catalogue")
            if strict and len(values) != required_count:
                fail(f"{catalogue['region']} must have exactly {required_count} {key}")
        if set(essentials).intersection(icons):
            fail(f"{catalogue['region']} has a taxon in both achievement lists without an override")
        achievement_taxa = set(essentials).union(icons)
        if strict and not achievement_taxa.issubset(media_taxon_ids):
            fail(f"{catalogue['region']} requires licence-verified media for every achievement taxon")
        if strict and catalogue["status"] != "frozen":
            fail(f"{catalogue['region']} is not frozen")

    assignments = read_json_yaml(CATALOGUES / "country_territory_assignments.yaml")
    if strict and assignments.get("coverage_status") != "complete":
        fail("strict generation requires complete country/territory coverage")
    for catalogue in catalogue_rows:
        catalogue["achievements"] = achievement_by_region[catalogue["region"]]
    return taxa, media, catalogue_rows, source_digest(source_paths)


def write_outputs(
    taxa: list[dict], media: list[dict], catalogues: list[dict], digest: str, release: bool,
) -> None:
    # A direct replacement avoids Windows directory-handle failures caused by antivirus/indexing
    # services. Release packaging consumes only a successfully validated output directory.
    legacy_stage = OUTPUT.with_name("generated-staging")
    if legacy_stage.exists():
        shutil.rmtree(legacy_stage)
    if OUTPUT.exists():
        shutil.rmtree(OUTPUT)
    OUTPUT.mkdir(parents=True)
    database_path = OUTPUT / "catalogue.sqlite"
    database = sqlite3.connect(database_path)
    try:
        database.executescript("""
            CREATE TABLE catalogue_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE taxon (taxon_id INTEGER PRIMARY KEY, scientific_name TEXT NOT NULL,
                common_names_json TEXT NOT NULL, taxonomy_json TEXT NOT NULL);
            CREATE TABLE regional_taxon (region_key TEXT NOT NULL, catalogue_version TEXT NOT NULL,
                taxon_id INTEGER NOT NULL, encounter_rarity TEXT NOT NULL,
                seasonality_json TEXT NOT NULL, sort_order INTEGER NOT NULL, inclusion_provenance TEXT NOT NULL,
                PRIMARY KEY(region_key, catalogue_version, taxon_id));
            CREATE TABLE regional_achievement (region_key TEXT NOT NULL, catalogue_version TEXT NOT NULL,
                achievement_key TEXT NOT NULL, achievement_type TEXT NOT NULL, taxon_ids_json TEXT NOT NULL,
                PRIMARY KEY(region_key, catalogue_version, achievement_key));
            CREATE TABLE media_provenance (taxon_id INTEGER NOT NULL, source_url TEXT NOT NULL,
                creator TEXT NOT NULL, licence_code TEXT NOT NULL, metadata_json TEXT NOT NULL,
                PRIMARY KEY(taxon_id, source_url));
        """)
        database.executemany("INSERT INTO catalogue_metadata VALUES (?, ?)", [
            ("schema_version", "1"), ("source_digest", digest), ("generator", "generate_catalogues.py"),
            ("release_status", "release" if release else "draft"),
        ])
        for taxon in sorted(taxa, key=lambda row: row["taxon_id"]):
            database.execute("INSERT INTO taxon VALUES (?, ?, ?, ?)", (
                taxon["taxon_id"], taxon["scientific_name"],
                json.dumps(taxon.get("common_names", {}), sort_keys=True),
                json.dumps(taxon.get("taxonomy", {}), sort_keys=True),
            ))
        for catalogue in sorted(catalogues, key=lambda row: row["region"]):
            for index, entry in enumerate(catalogue["taxa"], start=1):
                database.execute("INSERT INTO regional_taxon VALUES (?, ?, ?, ?, ?, ?, ?)", (
                    catalogue["region"], catalogue["version"], entry["taxon_id"], entry["encounter_rarity"],
                    json.dumps(entry.get("seasonality", {}), sort_keys=True), index,
                    entry["inclusion_provenance"],
                ))
            achievement = catalogue["achievements"]
            for achievementType, taxonIds in (
                ("essentials", achievement.get("essentials", [])),
                ("icons", achievement.get("icons", [])),
            ):
                database.execute("INSERT INTO regional_achievement VALUES (?, ?, ?, ?, ?)", (
                    catalogue["region"], catalogue["version"], achievementType,
                    achievementType, json.dumps(taxonIds),
                ))
        for item in media:
            database.execute("INSERT INTO media_provenance VALUES (?, ?, ?, ?, ?)", (
                item["taxon_id"], item["source_url"], item["creator"], item["licence_code"],
                json.dumps(item, sort_keys=True),
            ))
        database.commit()
    finally:
        database.close()
    report = {
        "source_digest": digest,
        "release_status": "release" if release else "draft",
        "global_taxa": len(taxa),
        "media_records": len(media),
        "catalogues": [
            {
                "region": item["region"], "version": item["version"], "taxa": len(item["taxa"]),
                "essentials": len(item["achievements"].get("essentials", [])),
                "icons": len(item["achievements"].get("icons", [])),
            }
            for item in catalogues
        ],
    }
    (OUTPUT / "catalogue-report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (OUTPUT / "catalogue-diff.md").write_text(
        "# Catalogue source report\n\n"
        f"Source digest: `{digest}`\n\n"
        "| Region | Version | Taxa | Essentials | Icons |\n|---|---|---:|---:|---:|\n" +
        "".join(
            f"| {item['region']} | {item['version']} | {len(item['taxa'])} | "
            f"{len(item['achievements'].get('essentials', []))} | {len(item['achievements'].get('icons', []))} |\n"
            for item in catalogues
        ),
        encoding="utf-8",
    )
    ANDROID_ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy2(database_path, ANDROID_ASSETS / "catalogue.sqlite")
    shutil.copy2(OUTPUT / "catalogue-report.json", ANDROID_ASSETS / "catalogue-report.json")
    write_content_pack(database_path, OUTPUT / "catalogue-report.json", digest)


def write_content_pack(database_path: Path, report_path: Path, digest: str) -> None:
    """Package generated content for a later validated on-device install.

    The archive proves accidental-corruption integrity through SHA-256 checksums. It is not a
    remote trust/signature protocol; download and release authorization remain a separate step.
    """
    files = {
        "catalogue.sqlite": database_path,
        "catalogue-report.json": report_path,
    }
    manifest = {
        "schema_version": 1,
        "source_digest": digest,
        "files": {
            name: hashlib.sha256(path.read_bytes()).hexdigest()
            for name, path in files.items()
        },
    }
    pack_path = OUTPUT / "wildlife-content-pack.zip"
    with zipfile.ZipFile(pack_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, path in files.items():
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())
        info = zipfile.ZipInfo("manifest.json", date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, json.dumps(manifest, indent=2, sort_keys=True) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="Require complete, frozen release sources.")
    args = parser.parse_args()
    taxa, media, catalogues, digest = validate(args.strict)
    write_outputs(taxa, media, catalogues, digest, release=args.strict)
    print(f"Generated deterministic {('release' if args.strict else 'draft')} catalogue: {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
