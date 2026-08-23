"""Refresh review-only iNaturalist evidence for the pilot content-management workflow.

This command is deliberately non-destructive: it reads the public iNaturalist API through the
existing candidate generator, computes a frequency-relative proposal and writes review CSVs. It
never changes a regional catalogue, achievement list, XP value or the content workbook.
"""

from __future__ import annotations

import argparse
import csv
import json
import subprocess
import sys
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
REVIEW = CATALOGUES / "review"
PILOTS = ("mediterranean_europe", "east_africa", "caribbean")
MODEL_VERSION = "inaturalist-regional-frequency-v1"


def proposed_rarity(percentile: float) -> str:
    if percentile < 0.60:
        return "common"
    if percentile < 0.85:
        return "uncommon"
    if percentile < 0.97:
        return "rare"
    return "very_rare"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def refresh_candidates(region: str, pages: int, refresh: bool, delay: float) -> None:
    command = [
        sys.executable,
        str(ROOT / "tools" / "generate_inaturalist_candidates.py"),
        region,
        "--pages",
        str(pages),
        "--delay",
        str(delay),
    ]
    if refresh:
        command.append("--refresh")
    subprocess.run(command, cwd=ROOT, check=True)


def write_evidence(region: str) -> Path:
    candidate_path = REVIEW / f"{region}_inaturalist_candidates.csv"
    if not candidate_path.exists():
        raise ValueError(f"Missing candidate file for {region}: {candidate_path.relative_to(ROOT)}")
    with candidate_path.open(encoding="utf-8", newline="") as handle:
        candidates = list(csv.DictReader(handle))

    proposals: dict[int, str] = {}
    by_group: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in candidates:
        if row["recommended"] == "yes":
            by_group[row["group"]].append(row)
    for rows in by_group.values():
        ranked = sorted(rows, key=lambda row: (-int(row["observation_count"]), int(row["taxon_id"])))
        for index, row in enumerate(ranked):
            proposals[int(row["taxon_id"])] = proposed_rarity(index / max(1, len(ranked)))

    catalogue = load_json(CATALOGUES / "regions" / region / "catalogue.yaml")
    current = {entry["taxon_id"]: entry for entry in catalogue["taxa"]}
    output_rows = []
    for candidate in candidates:
        taxon_id = int(candidate["taxon_id"])
        entry = current.get(taxon_id)
        output_rows.append({
            "region": region,
            "in_current_catalogue": "yes" if entry else "no",
            "recommended_candidate": candidate["recommended"],
            "group": candidate["group"],
            "regional_rank": candidate["rank"],
            "taxon_id": taxon_id,
            "common_name": candidate["common_name"],
            "scientific_name": candidate["scientific_name"],
            "observation_count": candidate["observation_count"],
            "proposed_frequency_rarity": proposals.get(taxon_id, "not_ranked"),
            "current_editorial_rarity": entry["encounter_rarity"] if entry else "",
            "model_version": MODEL_VERSION,
            "review_action": "compare then edit workbook",
        })

    # Catalogue taxa beyond the candidate pages are surfaced too, so their lack of evidence is
    # visible rather than silently treated as a rarity assignment.
    candidate_ids = {int(row["taxon_id"]) for row in candidates}
    taxa = {entry["taxon_id"]: entry for entry in load_json(CATALOGUES / "taxa.yaml")["taxa"]}
    for taxon_id, entry in sorted(current.items()):
        if taxon_id in candidate_ids:
            continue
        taxon = taxa[taxon_id]
        output_rows.append({
            "region": region,
            "in_current_catalogue": "yes",
            "recommended_candidate": "not_sampled",
            "group": taxon.get("taxonomy", {}).get("class", ""),
            "regional_rank": "",
            "taxon_id": taxon_id,
            "common_name": taxon.get("common_names", {}).get("en", ""),
            "scientific_name": taxon["scientific_name"],
            "observation_count": "",
            "proposed_frequency_rarity": "not_sampled",
            "current_editorial_rarity": entry["encounter_rarity"],
            "model_version": MODEL_VERSION,
            "review_action": "retain or review manually",
        })

    output_path = REVIEW / f"{region}_inaturalist_evidence.csv"
    fields = list(output_rows[0])
    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(sorted(output_rows, key=lambda row: (row["in_current_catalogue"] != "yes", row["group"], int(row["regional_rank"] or 999999), row["scientific_name"])))
    return output_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Refresh non-destructive iNaturalist catalogue evidence.")
    parser.add_argument("regions", nargs="*", choices=PILOTS)
    parser.add_argument("--pages", type=int, default=2, help="200-result pages per animal group")
    parser.add_argument("--refresh", action="store_true", help="Re-fetch cached public iNaturalist pages")
    parser.add_argument("--offline", action="store_true", help="Use the existing candidate CSVs without network requests")
    parser.add_argument("--delay", type=float, default=1.2, help="Seconds between public API requests")
    args = parser.parse_args()
    if args.pages < 1:
        parser.error("--pages must be positive")
    if args.offline and args.refresh:
        parser.error("--offline and --refresh cannot be combined")
    regions = args.regions or PILOTS
    for region in regions:
        if not args.offline:
            refresh_candidates(region, args.pages, args.refresh, args.delay)
        path = write_evidence(region)
        print(f"Wrote review-only evidence: {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
