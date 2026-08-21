"""Promote selected iNaturalist candidate sheets to broad regional catalogue sources.

New entries are intentionally marked `unknown` encounter rarity until editorial rarity review;
the source data never turns raw reporting frequency into a biological claim.
"""
from __future__ import annotations

import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
REGIONS = ("mediterranean_europe", "east_africa", "caribbean")
GROUP_CLASS = {"mammals": "Mammalia", "birds": "Aves", "reptiles": "Reptilia", "fish": "Actinopterygii", "amphibians": "Amphibia"}


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    taxa_source = CATALOGUES / "taxa.yaml"
    taxa = load(taxa_source)
    by_id = {item["taxon_id"]: item for item in taxa["taxa"]}
    for region in REGIONS:
        catalogue_path = CATALOGUES / "regions" / region / "catalogue.yaml"
        catalogue = load(catalogue_path)
        existing = {item["taxon_id"]: item for item in catalogue["taxa"]}
        candidates_path = CATALOGUES / "review" / f"{region}_inaturalist_candidates.csv"
        with candidates_path.open(encoding="utf-8", newline="") as handle:
            candidates = [row for row in csv.DictReader(handle) if row["recommended"] == "yes"]
        entries = []
        for candidate in candidates:
            taxon_id = int(candidate["taxon_id"])
            by_id.setdefault(taxon_id, {
                "taxon_id": taxon_id, "rank": "species",
                "scientific_name": candidate["scientific_name"],
                "common_names": {"en": candidate["common_name"]},
                "taxonomy": {"class": GROUP_CLASS[candidate["group"]]},
                "source_url": f"https://www.inaturalist.org/taxa/{taxon_id}",
            })
            entries.append(existing.get(taxon_id, {
                "taxon_id": taxon_id,
                "encounter_rarity": "unknown",
                "prestige": "standard",
                "seasonality": {"en": "year-round"},
                "inclusion_provenance": (
                    "Broad regional field-guide candidate curated from public iNaturalist data; "
                    "encounter rarity remains under editorial review and is not inferred from report counts."
                ),
            }))
        selected_ids = {entry["taxon_id"] for entry in entries}
        for taxon_id, entry in existing.items():
            if taxon_id not in selected_ids:
                entries.append(entry)
        catalogue["catalogue_version"] = f"{region.replace('_', '-')}-broad-draft-1"
        catalogue["status"] = "draft"
        catalogue["taxa"] = entries
        catalogue_path.write_text(json.dumps(catalogue, indent=2) + "\n", encoding="utf-8")
        achievements = load(CATALOGUES / "achievements.yaml")
        for achievement in achievements["achievements"]:
            if achievement["region"] == region:
                achievement["catalogue_version"] = catalogue["catalogue_version"]
        (CATALOGUES / "achievements.yaml").write_text(json.dumps(achievements, indent=2) + "\n", encoding="utf-8")
    taxa["taxa"] = sorted(by_id.values(), key=lambda item: item["taxon_id"])
    taxa_source.write_text(json.dumps(taxa, indent=2) + "\n", encoding="utf-8")
    print(f"Promoted {len(taxa['taxa'])} global taxa into broad draft sources.")


if __name__ == "__main__":
    main()
