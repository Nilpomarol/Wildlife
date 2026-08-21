"""Import an owner-editable Essentials/Icons CSV into a regional draft catalogue."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
REQUIRED = {"essential": 10, "icon": 5}
VALID_RARITIES = {"common", "uncommon", "rare", "very_rare"}


def fail(message: str) -> None:
    raise SystemExit(f"Checklist import failed: {message}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("region", help="Stable region key, for example mediterranean_europe")
    parser.add_argument("--csv", type=Path, required=True, help="Owner-editable checklist CSV")
    args = parser.parse_args()
    catalogue_path = CATALOGUES / "regions" / args.region / "catalogue.yaml"
    achievement_path = CATALOGUES / "achievements.yaml"
    taxa_path = CATALOGUES / "taxa.yaml"
    if not args.csv.exists() or not catalogue_path.exists():
        fail("CSV or regional catalogue source is missing")
    taxa = {item["taxon_id"] for item in json.loads(taxa_path.read_text(encoding="utf-8"))["taxa"]}
    catalogue = json.loads(catalogue_path.read_text(encoding="utf-8"))
    achievements = json.loads(achievement_path.read_text(encoding="utf-8"))
    with args.csv.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    by_list = {name: [] for name in REQUIRED}
    seen = set()
    for row in rows:
        list_name = row.get("list", "").strip().lower()
        if list_name not in REQUIRED:
            fail(f"unknown list '{list_name}'")
        try:
            taxon_id = int(row["taxon_id"])
            position = int(row["position"])
        except (KeyError, ValueError):
            fail("every row requires numeric position and taxon_id")
        if taxon_id not in taxa or taxon_id in seen:
            fail(f"taxon {taxon_id} is unknown or repeated")
        rarity = row.get("encounter_rarity", "").strip()
        if rarity not in VALID_RARITIES:
            fail(f"taxon {taxon_id} has an invalid encounter rarity")
        prestige = row.get("prestige", "").strip()
        if prestige != ("legendary" if list_name == "icon" else "standard"):
            fail(f"taxon {taxon_id} has a prestige incompatible with its checklist")
        seen.add(taxon_id)
        by_list[list_name].append((position, {
            "taxon_id": taxon_id,
            "encounter_rarity": rarity,
            "prestige": prestige,
            "seasonality": {"en": row.get("seasonality", "").strip()},
            "inclusion_provenance": (
                f"Owner-managed {args.region} {list_name} checklist; imported from {args.csv.name}. "
                "Encounter rarity and prestige are curated game metadata."
            ),
        }))
    for list_name, expected_count in REQUIRED.items():
        positions = sorted(position for position, _ in by_list[list_name])
        if positions != list(range(1, expected_count + 1)):
            fail(f"{list_name} must contain positions 1 through {expected_count} exactly once")

    entries = [entry for list_name in ("essential", "icon") for _, entry in sorted(by_list[list_name])]
    catalogue["taxa"] = entries
    achievement = next((item for item in achievements["achievements"] if item["region"] == args.region), None)
    if achievement is None:
        fail(f"no achievement source for {args.region}")
    achievement["catalogue_version"] = catalogue["catalogue_version"]
    achievement["essentials"] = [entry["taxon_id"] for _, entry in sorted(by_list["essential"])]
    achievement["icons"] = [entry["taxon_id"] for _, entry in sorted(by_list["icon"])]
    catalogue_path.write_text(json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    achievement_path.write_text(json.dumps(achievements, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(by_list['essential'])} Essentials and {len(by_list['icon'])} Icons for {args.region}.")


if __name__ == "__main__":
    main()
