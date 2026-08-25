"""Produce a deterministic, non-mutating readiness audit for the three pilot packs."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
REVIEW = CATALOGUES / "review"
GENERATED = CATALOGUES / "generated"
COMPATIBLE_LICENCES = {"pdm", "cc0", "cc-by", "cc-by-sa"}


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def audit() -> dict:
    achievements = {entry["region"]: entry for entry in read_json(CATALOGUES / "achievements.yaml")["achievements"]}
    media_document = read_json(CATALOGUES / "media_manifest.yaml")
    media_items = media_document.get("assets", media_document.get("media", []))
    media_waivers = {row["taxon_id"] for row in media_document.get("waivers", [])}
    media_by_taxon = {}
    for media in media_items:
        media_by_taxon.setdefault(media["taxon_id"], []).append(media)
    report = read_json(GENERATED / "catalogue-report.json")
    pack_path = GENERATED / "wildlife-content-pack.zip"
    pack = {"present": pack_path.is_file(), "valid": False, "source_digest_matches": False}
    if pack_path.is_file():
        with zipfile.ZipFile(pack_path) as archive:
            manifest = json.loads(archive.read("manifest.json"))
            database = archive.read("catalogue.sqlite")
            pack["valid"] = (
                manifest.get("schema_version") == 3
                and manifest.get("content_schema_version") == 3
                and manifest.get("generation_id") == report.get("generation_id")
                and manifest.get("generation_sequence") == report.get("generation_sequence")
                and manifest.get("generated_at") == report.get("generated_at")
                and manifest.get("minimum_app_version") == report.get("minimum_app_version")
                and manifest.get("release_status") == report.get("release_status")
                and manifest.get("files", {}).get("catalogue.sqlite") == sha256_bytes(database)
            )
            pack["source_digest_matches"] = manifest.get("source_digest") == report.get("source_digest")

    regional = []
    blockers = []
    frozen_regions = 0
    catalogue_paths = sorted((CATALOGUES / "regions").glob("*/catalogue.yaml"))
    for catalogue_path in catalogue_paths:
        catalogue = read_json(catalogue_path)
        region = catalogue_path.parent.name
        achievement = achievements.get(region, {})
        taxa = catalogue.get("taxa", [])
        essentials = achievement.get("essentials", [])
        icons = achievement.get("icons", [])
        listed = set(essentials).union(icons)
        media_complete = all(
            taxon_id in media_waivers
            or any(item.get("licence_code") in COMPATIBLE_LICENCES for item in media_by_taxon.get(taxon_id, []))
            for taxon_id in listed
        )
        direct_media_complete = all(
            taxon_id in media_waivers or any(
                item.get("media_type") == "photo"
                and {variant.get("variant") for variant in item.get("variants", [])}
                >= {"thumbnail", "detail"}
                for item in media_by_taxon.get(taxon_id, [])
            )
            for taxon_id in listed
        )
        evidence_path = REVIEW / f"{region}_inaturalist_evidence.csv"
        unknown_rarity = sum(entry.get("encounter_rarity") == "unknown" for entry in taxa)
        checks = {
            "catalogue_frozen": catalogue.get("status") == "frozen",
            "matching_checklist_version": achievement.get("catalogue_version") == catalogue.get("catalogue_version"),
            "ten_essentials": len(essentials) == 10 and len(set(essentials)) == 10,
            "five_icons": len(icons) == 5 and len(set(icons)) == 5,
            "checklists_in_catalogue": listed.issubset({entry.get("taxon_id") for entry in taxa}),
            "achievement_media_licenced": media_complete,
            "achievement_media_direct_variants": direct_media_complete,
            "iNaturalist_evidence_present": evidence_path.is_file(),
            "no_unknown_rarity": unknown_rarity == 0,
        }
        failed = [name for name, value in checks.items() if not value]
        if catalogue.get("status") == "frozen":
            frozen_regions += 1
        if failed and catalogue.get("status") == "frozen":
            blockers.append({"region": region, "checks": failed})
        regional.append({
            "region": region,
            "catalogue_version": catalogue.get("catalogue_version"),
            "taxa": len(taxa),
            "unknown_rarity": unknown_rarity,
            "checks": checks,
        })
    if frozen_regions == 0:
        blockers.append({"region": "regional_catalogues", "checks": ["at_least_one_frozen_catalogue"]})
    if not pack["valid"] or not pack["source_digest_matches"]:
        blockers.append({"region": "generated_pack", "checks": ["valid_pack_matching_current_sources"]})
    content_checks = {
        "content_schema_v3": report.get("schema_version") == 3,
        "achievement_direct_media_complete": report.get("achievement_taxa_without_direct_media") == 0,
    }
    failed_content = [name for name, value in content_checks.items() if not value]
    if failed_content:
        blockers.append({"region": "published_content", "checks": failed_content})
    return {
        "schema_version": 3,
        "release_ready": not blockers,
        "source_digest": report.get("source_digest"),
        "pack": pack,
        "content_checks": content_checks,
        "content_coverage": {
            "published_taxa": report.get("published_taxa", 0),
            "missing_descriptions": report.get("missing_published_descriptions", 0),
            "missing_conservation": report.get("missing_published_conservation", 0),
            "taxa_with_photos": report.get("published_taxa_with_photos", 0),
            "taxa_with_curated_silhouettes": report.get("published_taxa_with_curated_silhouettes", 0),
            "taxa_using_group_silhouettes": report.get("published_taxa_using_group_silhouettes", 0),
        },
        "regions": regional,
        "blockers": blockers,
    }


def markdown(result: dict) -> str:
    lines = ["# Pilot release audit", "", f"Release ready: **{'YES' if result['release_ready'] else 'NO'}**", ""]
    lines += ["| Region | Version | Taxa | Unknown rarity | Passed checks |", "|---|---|---:|---:|---:|"]
    for region in result["regions"]:
        passed = sum(region["checks"].values())
        lines.append(f"| {region['region']} | {region['catalogue_version']} | {region['taxa']} | {region['unknown_rarity']} | {passed}/{len(region['checks'])} |")
    coverage = result["content_coverage"]
    lines += [
        "",
        "## Non-blocking content coverage",
        "",
        f"- Missing sourced descriptions: {coverage['missing_descriptions']} of {coverage['published_taxa']} published taxa.",
        f"- Missing conservation assessments: {coverage['missing_conservation']} of {coverage['published_taxa']} published taxa.",
        f"- Curated reference photos: {coverage['taxa_with_photos']} of {coverage['published_taxa']} published taxa.",
        f"- Curated silhouettes: {coverage['taxa_with_curated_silhouettes']} of {coverage['published_taxa']} published taxa; {coverage['taxa_using_group_silhouettes']} use the bundled group fallback.",
        "- Missing values render as unavailable; every supplied value still must pass provenance validation.",
    ]
    lines += ["", "## Blockers", ""]
    if result["blockers"]:
        for blocker in result["blockers"]:
            lines.append(f"- **{blocker['region']}**: {', '.join(blocker['checks'])}")
    else:
        lines.append("- None. Run strict catalogue generation as the final promotion check.")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit pilot-release readiness without changing content.")
    parser.add_argument("--strict", action="store_true", help="Return a non-zero status when any release blocker remains.")
    args = parser.parse_args()
    result = audit()
    (REVIEW / "pilot-release-audit.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    (REVIEW / "pilot-release-audit.md").write_text(markdown(result), encoding="utf-8")
    print(f"Pilot release audit: {'ready' if result['release_ready'] else 'blocked'}")
    if args.strict and not result["release_ready"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
