"""Generate deterministic local GeoJSON boundary assets from approved Natural Earth map units."""

from __future__ import annotations

import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalogues/boundaries/source/ne_10m_admin_0_map_units_5_1_1"
DBF = SOURCE / "ne_10m_admin_0_map_units.dbf"
SHP = SOURCE / "ne_10m_admin_0_map_units.shp"
ASSIGNMENTS = ROOT / "catalogues/country_territory_assignments.yaml"
OUTPUT = ROOT / "catalogues/boundaries/generated"
GEOJSON = OUTPUT / "regional-boundaries-v1.geojson"
MANIFEST = OUTPUT / "regional-boundaries-v1.manifest.json"
APP_ASSET = ROOT / "app/src/main/assets/boundaries/regional-boundaries-v1.geojson"


def dbf_rows(path: Path):
    with path.open("rb") as handle:
        header = handle.read(32)
        count = struct.unpack("<I", header[4:8])[0]
        header_length = struct.unpack("<H", header[8:10])[0]
        record_length = struct.unpack("<H", header[10:12])[0]
        fields = []
        while handle.tell() < header_length:
            descriptor = handle.read(32)
            if not descriptor or descriptor[0] == 0x0D:
                break
            fields.append((descriptor[:11].split(b"\0", 1)[0].decode("ascii"), descriptor[16]))
        handle.seek(header_length)
        for _ in range(count):
            record = handle.read(record_length)
            if len(record) != record_length or record[0:1] == b"*":
                continue
            row, offset = {}, 1
            for name, length in fields:
                row[name] = record[offset:offset + length].decode("utf-8", errors="replace").strip(" \0")
                offset += length
            yield row


def shp_geometries(path: Path):
    with path.open("rb") as handle:
        handle.read(100)
        while header := handle.read(8):
            _, content_words = struct.unpack(">2i", header)
            content = handle.read(content_words * 2)
            shape_type = struct.unpack_from("<i", content)[0]
            if shape_type == 0:
                yield None
                continue
            if shape_type not in {5, 15, 25}:  # Polygon, PolygonZ, PolygonM
                raise ValueError(f"Unsupported source shape type {shape_type}")
            part_count, point_count = struct.unpack_from("<2i", content, 36)
            parts = struct.unpack_from(f"<{part_count}i", content, 44)
            point_offset = 44 + part_count * 4
            points = list(struct.iter_unpack("<2d", content[point_offset:point_offset + point_count * 16]))
            rings = []
            for index, start in enumerate(parts):
                end = parts[index + 1] if index + 1 < len(parts) else point_count
                rings.append([[round(x, 7), round(y, 7)] for x, y in points[start:end]])
            yield rings


def signed_area(ring: list[list[float]]) -> float:
    return sum(
        ring[index][0] * ring[(index + 1) % len(ring)][1]
        - ring[(index + 1) % len(ring)][0] * ring[index][1]
        for index in range(len(ring))
    ) / 2


def point_in_ring(point: list[float], ring: list[list[float]]) -> bool:
    x, y = point
    inside = False
    for index, (x1, y1) in enumerate(ring):
        x2, y2 = ring[(index + 1) % len(ring)]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            inside = not inside
    return inside


def multipolygon(rings: list[list[list[float]]]) -> list[list[list[list[float]]]]:
    if not rings:
        return []
    exterior_sign = 1 if signed_area(max(rings, key=lambda ring: abs(signed_area(ring)))) > 0 else -1
    exteriors = [ring for ring in rings if signed_area(ring) * exterior_sign > 0]
    if not exteriors:
        exteriors = rings
    polygons = [[ring] for ring in exteriors]
    for ring in rings:
        if ring in exteriors:
            continue
        containers = [index for index, exterior in enumerate(exteriors) if point_in_ring(ring[0], exterior)]
        if containers:
            polygons[containers[0]].append(ring)
        else:
            polygons.append([ring])
    return polygons


def main() -> None:
    if not all(path.exists() for path in (DBF, SHP, ASSIGNMENTS)):
        raise SystemExit("Boundary source or approved assignment manifest is missing.")
    manifest = json.loads(ASSIGNMENTS.read_text(encoding="utf-8"))
    by_name = {item["map_unit"]: item for item in manifest["map_unit_assignments"]}
    rows = list(dbf_rows(DBF))
    geometries = list(shp_geometries(SHP))
    if len(rows) != len(geometries):
        raise SystemExit("Natural Earth DBF and SHP record counts differ.")
    features = []
    unmatched = []
    for row, rings in zip(rows, geometries):
        name = row.get("NAME", "")
        assignment = by_name.get(name)
        if assignment is None:
            unmatched.append(name)
            continue
        if rings is None:
            raise SystemExit(f"Source map unit has no geometry: {name}")
        properties = {
            "map_unit": name,
            "source_dataset": assignment["source_dataset"],
            "source_type": assignment["source_type"],
        }
        for key in ("territory", "region", "assignment_result", "boundary_status"):
            if key in assignment:
                properties[key] = assignment[key]
        features.append({
            "type": "Feature",
            "properties": properties,
            "geometry": {"type": "MultiPolygon", "coordinates": multipolygon(rings)},
        })
    if unmatched:
        raise SystemExit(f"No approved assignment for source map units: {', '.join(sorted(set(unmatched)))}")
    if len(features) != len(by_name):
        raise SystemExit("Approved map-unit list and boundary source do not have one-to-one coverage.")
    features.sort(key=lambda feature: feature["properties"]["map_unit"])
    OUTPUT.mkdir(parents=True, exist_ok=True)
    collection = {"type": "FeatureCollection", "name": "regional-boundaries-v1", "features": features}
    encoded_geojson = json.dumps(collection, ensure_ascii=False, separators=(",", ":")) + "\n"
    GEOJSON.write_text(encoded_geojson, encoding="utf-8")
    APP_ASSET.parent.mkdir(parents=True, exist_ok=True)
    APP_ASSET.write_text(encoded_geojson, encoding="utf-8")
    summary = {
        "schema_version": 1,
        "boundary_version": manifest["boundary_version"],
        "source": manifest["boundary_source"],
        "source_feature_count": len(features),
        "regional_feature_count": sum("region" in item["properties"] for item in features),
        "non_progressing_feature_count": sum("assignment_result" in item["properties"] for item in features),
        "geojson": GEOJSON.name,
    }
    MANIFEST.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(features)} local boundary features: {GEOJSON.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
