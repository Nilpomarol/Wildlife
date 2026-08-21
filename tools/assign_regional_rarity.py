"""Assign versioned, region-local encounter rarity from public iNaturalist counts.

Ranks are calculated separately by animal group within each region.  This is an
experimental automated model, not a conservation assessment.
"""
from __future__ import annotations

import csv
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
REGIONS = ("mediterranean_europe", "east_africa", "caribbean")
MODEL_VERSION = "inaturalist-regional-frequency-v1"


def rarity(percentile: float) -> str:
    if percentile < 0.60:
        return "common"
    if percentile < 0.85:
        return "uncommon"
    if percentile < 0.97:
        return "rare"
    return "very_rare"


def main() -> None:
    for region in REGIONS:
        candidate_path = CATALOGUES / "review" / f"{region}_inaturalist_candidates.csv"
        with candidate_path.open(encoding="utf-8", newline="") as handle:
            candidates = list(csv.DictReader(handle))
        counts = {int(row["taxon_id"]): int(row["observation_count"]) for row in candidates}
        groups = defaultdict(list)
        for row in candidates:
            if row["recommended"] == "yes":
                groups[row["group"]].append((int(row["taxon_id"]), int(row["observation_count"])))
        assignments = {}
        for group, values in groups.items():
            ranked = sorted(values, key=lambda item: (-item[1], item[0]))
            for index, (taxon_id, _) in enumerate(ranked):
                assignments[taxon_id] = rarity(index / max(1, len(ranked)))
        path = CATALOGUES / "regions" / region / "catalogue.yaml"
        catalogue = json.loads(path.read_text(encoding="utf-8"))
        for entry in catalogue["taxa"]:
            taxon_id = entry["taxon_id"]
            if taxon_id in assignments:
                entry["encounter_rarity"] = assignments[taxon_id]
                entry["inclusion_provenance"] += f" Automated rarity: {MODEL_VERSION}."
            else:
                entry["encounter_rarity"] = "very_rare"
                entry["inclusion_provenance"] += (
                    f" Automated rarity: {MODEL_VERSION}; no top-page regional count,"
                    " so retained as a conservative very-rare experimental tier."
                )
        catalogue["rarity_model_version"] = MODEL_VERSION
        path.write_text(json.dumps(catalogue, indent=2) + "\n", encoding="utf-8")
        print(f"Assigned {len(catalogue['taxa'])} regional rarity values for {region}.")


if __name__ == "__main__":
    main()
