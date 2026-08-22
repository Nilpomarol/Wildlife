"""Generate the lightweight, display-only Wildlife world atlas.

The full regional boundary asset remains the assignment source of truth. This generator
dissolves those map-unit polygons into visual regions and simplifies them only for rendering.
It requires Shapely (`pip install shapely`) and never replaces assignment geometry.
"""

from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path

from shapely import from_geojson
from shapely.geometry import mapping
from shapely.ops import unary_union


ROOT = Path(__file__).resolve().parents[1]
BOUNDARIES = ROOT / "catalogues/boundaries/generated/regional-boundaries-v1.geojson"
REGIONS = ROOT / "catalogues/regions.yaml"
OUTPUT = ROOT / "catalogues/boundaries/generated"
APP_ASSETS = ROOT / "app/src/main/assets/atlas"
ATLAS_NAME = "regional-atlas-v1.geojson"
LABELS_NAME = "regional-atlas-labels-v1.geojson"
SIMPLIFICATION_TOLERANCE = 0.06


def region_names(path: Path) -> dict[str, str]:
    names: dict[str, str] = {}
    current_key: str | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        key_match = re.match(r"\s*- key:\s*(\S+)", line)
        if key_match:
            current_key = key_match.group(1)
            continue
        name_match = re.match(r"\s*names:\s*\{\s*en:\s*([^,}]+)", line)
        if current_key and name_match:
            names[current_key] = name_match.group(1).strip()
    return names


def rounded(value):
    if isinstance(value, float):
        return round(value, 5)
    if isinstance(value, list):
        return [rounded(item) for item in value]
    if isinstance(value, tuple):
        return [rounded(item) for item in value]
    if isinstance(value, dict):
        return {key: rounded(item) for key, item in value.items()}
    return value


def write_collection(path: Path, name: str, features: list[dict]) -> None:
    collection = {"type": "FeatureCollection", "name": name, "features": features}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(collection, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    boundary_collection = json.loads(BOUNDARIES.read_text(encoding="utf-8"))
    names = region_names(REGIONS)
    grouped: dict[str, list] = defaultdict(list)
    for feature in boundary_collection["features"]:
        region = feature["properties"].get("region", "unsupported_land")
        grouped[region].append(from_geojson(json.dumps(feature["geometry"])))

    atlas_features: list[dict] = []
    label_features: list[dict] = []
    for region in sorted(grouped, key=lambda key: (key == "unsupported_land", key)):
        dissolved = unary_union(grouped[region])
        simplified = dissolved.simplify(SIMPLIFICATION_TOLERANCE, preserve_topology=True)
        properties = {
            "region": region,
            "display_name": names.get(region, "Other land"),
            "progressing": region != "unsupported_land",
        }
        atlas_features.append({
            "type": "Feature",
            "properties": properties,
            "geometry": rounded(mapping(simplified)),
        })
        if region != "unsupported_land":
            anchor = simplified.representative_point()
            label_features.append({
                "type": "Feature",
                "properties": properties,
                "geometry": {
                    "type": "Point",
                    "coordinates": [round(anchor.x, 5), round(anchor.y, 5)],
                },
            })

    for directory in (OUTPUT, APP_ASSETS):
        write_collection(directory / ATLAS_NAME, "regional-atlas-v1", atlas_features)
        write_collection(directory / LABELS_NAME, "regional-atlas-labels-v1", label_features)

    source_size = BOUNDARIES.stat().st_size
    atlas_size = (APP_ASSETS / ATLAS_NAME).stat().st_size
    print(
        f"Generated {len(atlas_features)} display regions and {len(label_features)} label anchors; "
        f"atlas {atlas_size:,} bytes (assignment source {source_size:,} bytes)."
    )


if __name__ == "__main__":
    main()
