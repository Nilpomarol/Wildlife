"""Promote an owner-approved Natural Earth review worksheet to runtime authoring data."""

from __future__ import annotations

import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REVIEW = ROOT / "catalogues/review/territory_assignment_checklist.csv"
OUTPUT = ROOT / "catalogues/country_territory_assignments.yaml"


def normalise_iso(value: str) -> str | None:
    return value if len(value) == 2 and value != "—" else None


def main() -> None:
    if not REVIEW.exists():
        raise SystemExit(f"Missing approved review worksheet: {REVIEW.relative_to(ROOT)}")

    with REVIEW.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows or any(not row["proposed_region"] for row in rows):
        raise SystemExit("The review worksheet must give every map unit a recommendation.")

    iso_assignments: dict[str, str] = {}
    map_units = []
    for row in rows:
        result = row["proposed_region"]
        iso = normalise_iso(row["iso"])
        unit = {
            "map_unit": row["name"],
            "source_dataset": "natural_earth_admin0_map_units_10m_5_1_1",
            "source_type": row["type"],
        }
        if iso:
            unit["territory"] = iso
        if result in {"unsupported", "marine_worldwide"}:
            unit["assignment_result"] = (
                "unsupported_for_regional_progression" if result == "unsupported" else result
            )
        else:
            unit["region"] = result
        if "disputed-boundary" in row["review"]:
            unit["boundary_status"] = "disputed_de_facto_geometry"
        map_units.append(unit)

        if iso and "region" in unit:
            existing = iso_assignments.setdefault(iso, unit["region"])
            if existing != unit["region"]:
                raise SystemExit(f"Conflicting regions for ISO territory {iso}: {existing}, {unit['region']}")

    map_units.sort(key=lambda item: item["map_unit"])
    manifest = {
        "schema_version": 2,
        "coverage_status": "complete",
        "boundary_source": "natural_earth_admin0_map_units_10m_5_1_1",
        "boundary_version": "regional-boundaries-v1",
        "approval": "owner-approved-2026-08-21",
        "notes": [
            "ISO assignments are a review index only; point assignment must use the local versioned boundary geometry.",
            "map_unit_assignments preserve source geometry pieces without an ISO territory code.",
            "marine_worldwide and unsupported entries remain in personal history but do not progress a land region.",
        ],
        "assignments": [
            {"territory": territory, "region": region}
            for territory, region in sorted(iso_assignments.items())
        ],
        "map_unit_assignments": map_units,
    }
    OUTPUT.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    supported = sum("region" in unit for unit in map_units)
    non_progressing = len(map_units) - supported
    print(
        f"Wrote {len(iso_assignments)} ISO assignments and {len(map_units)} map-unit assignments "
        f"({supported} regional, {non_progressing} non-progressing)."
    )


if __name__ == "__main__":
    main()
