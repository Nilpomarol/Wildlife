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
PILOTS = ("mediterranean_europe", "east_africa", "caribbean")
COMPATIBLE_LICENCES = {"CC0", "CC BY", "CC BY-SA", "Public Domain"}


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def audit() -> dict:
    achievements = {entry["region"]: entry for entry in read_json(CATALOGUES / "achievements.yaml")["achievements"]}
    media_by_taxon = {}
    for media in read_json(CATALOGUES / "media_manifest.yaml")["media"]:
        media_by_taxon.setdefault(media["taxon_id"], []).append(media)
    report = read_json(GENERATED / "catalogue-report.json")
    pack_path = GENERATED / "wildlife-content-pack.zip"
    pack = {"present": pack_path.is_file(), "valid": False, "source_digest_matches": False}
    if pack_path.is_file():
        with zipfile.ZipFile(pack_path) as archive:
            manifest = json.loads(archive.read("manifest.json"))
            database = archive.read("catalogue.sqlite")
            pack["valid"] = (
                manifest.get("schema_version") == 1
                and manifest.get("files", {}).get("catalogue.sqlite") == sha256_bytes(database)
            )
            pack["source_digest_matches"] = manifest.get("source_digest") == report.get("source_digest")

    regional = []
    blockers = []
    for region in PILOTS:
        catalogue = read_json(CATALOGUES / "regions" / region / "catalogue.yaml")
        achievement = achievements.get(region, {})
        taxa = catalogue.get("taxa", [])
        essentials = achievement.get("essentials", [])
        icons = achievement.get("icons", [])
        listed = set(essentials).union(icons)
        media_complete = all(
            any(item.get("licence_code") in COMPATIBLE_LICENCES for item in media_by_taxon.get(taxon_id, []))
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
            "iNaturalist_evidence_present": evidence_path.is_file(),
            "no_unknown_rarity": unknown_rarity == 0,
        }
        failed = [name for name, value in checks.items() if not value]
        if failed:
            blockers.append({"region": region, "checks": failed})
        regional.append({
            "region": region,
            "catalogue_version": catalogue.get("catalogue_version"),
            "taxa": len(taxa),
            "unknown_rarity": unknown_rarity,
            "checks": checks,
        })
    if not pack["valid"] or not pack["source_digest_matches"]:
        blockers.append({"region": "generated_pack", "checks": ["valid_pack_matching_current_sources"]})
    return {
        "schema_version": 1,
        "release_ready": not blockers,
        "source_digest": report.get("source_digest"),
        "pack": pack,
        "regions": regional,
        "blockers": blockers,
    }


def markdown(result: dict) -> str:
    lines = ["# Pilot release audit", "", f"Release ready: **{'YES' if result['release_ready'] else 'NO'}**", ""]
    lines += ["| Region | Version | Taxa | Unknown rarity | Passed checks |", "|---|---|---:|---:|---:|"]
    for region in result["regions"]:
        passed = sum(region["checks"].values())
        lines.append(f"| {region['region']} | {region['catalogue_version']} | {region['taxa']} | {region['unknown_rarity']} | {passed}/{len(region['checks'])} |")
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
