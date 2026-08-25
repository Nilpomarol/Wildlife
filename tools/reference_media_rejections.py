from __future__ import annotations

import json
import os
import urllib.parse
from pathlib import Path


ALLOWED_SOURCE_HOSTS = {
    "commons.wikimedia.org": "wikimedia_commons",
    "inaturalist.org": "inaturalist",
    "www.inaturalist.org": "inaturalist",
}
REASONS = {"user_requested_replacement", "authoring_review"}


def atomic_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def normalized_source_key(source_url: str) -> str:
    if not isinstance(source_url, str) or not source_url.strip():
        raise ValueError("Reference-media rejection requires source_url")
    parsed = urllib.parse.urlsplit(source_url.strip())
    host = (parsed.hostname or "").casefold()
    if parsed.scheme != "https" or host not in ALLOWED_SOURCE_HOSTS:
        raise ValueError(f"Unsupported rejected-media source: {source_url}")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError(f"Rejected-media source must be a stable page URL: {source_url}")
    path = urllib.parse.unquote(parsed.path).replace("_", " ").rstrip("/").casefold()
    return f"{host}{path}"


def validate_rejection(raw: dict) -> dict:
    if not isinstance(raw, dict):
        raise ValueError("Reference-media rejection must be an object")
    taxon_id = raw.get("taxon_id")
    if not isinstance(taxon_id, int) or isinstance(taxon_id, bool) or taxon_id <= 0:
        raise ValueError("Reference-media rejection requires a positive taxon_id")
    source_url = raw.get("source_url")
    key = normalized_source_key(source_url)
    reason = raw.get("reason")
    if reason not in REASONS:
        raise ValueError(f"Unsupported reference-media rejection reason: {reason}")
    rejected_at_ms = raw.get("rejected_at_ms")
    if not isinstance(rejected_at_ms, int) or isinstance(rejected_at_ms, bool) or rejected_at_ms <= 0:
        raise ValueError("Reference-media rejection requires rejected_at_ms")
    generation = raw.get("catalogue_generation_id")
    if generation is not None and (not isinstance(generation, str) or not generation.strip()):
        raise ValueError("catalogue_generation_id must be absent or a non-empty string")
    provider = ALLOWED_SOURCE_HOSTS[urllib.parse.urlsplit(source_url).hostname.casefold()]
    return {
        "taxon_id": taxon_id,
        "source_url": source_url.strip(),
        "source_key": key,
        "provider": provider,
        "catalogue_generation_id": generation,
        "reason": reason,
        "rejected_at_ms": rejected_at_ms,
    }


def read_rejections(path: Path) -> list[dict]:
    if not path.is_file():
        return []
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("schema_version") != 1 or not isinstance(document.get("rejections"), list):
        raise ValueError(f"{path} requires schema_version 1 and a rejections list")
    seen: set[tuple[int, str]] = set()
    rows = []
    for raw in document["rejections"]:
        row = validate_rejection(raw)
        identity = (row["taxon_id"], row["source_key"])
        if identity in seen:
            raise ValueError(
                f"Duplicate reference-media rejection for taxon {row['taxon_id']}: {row['source_url']}"
            )
        seen.add(identity)
        rows.append(row)
    return rows


def rejection_map(path: Path) -> dict[int, set[str]]:
    result: dict[int, set[str]] = {}
    for row in read_rejections(path):
        result.setdefault(row["taxon_id"], set()).add(row["source_key"])
    return result


def import_export(export_path: Path, destination: Path) -> dict:
    exported = json.loads(export_path.read_text(encoding="utf-8"))
    incoming = exported.get("reference_media_rejections")
    if not isinstance(incoming, list):
        raise ValueError("Wildlife export has no reference_media_rejections list")
    existing = {
        (row["taxon_id"], row["source_key"]): row
        for row in read_rejections(destination)
    }
    imported = 0
    for raw in incoming:
        validated = validate_rejection(raw)
        identity = (validated["taxon_id"], validated["source_key"])
        if identity not in existing:
            existing[identity] = validated
            imported += 1
    rows = sorted(existing.values(), key=lambda row: (row["taxon_id"], row["source_key"]))
    atomic_json(destination, {
        "schema_version": 1,
        "rejections": [{key: row.get(key) for key in (
            "taxon_id", "source_url", "provider", "catalogue_generation_id", "reason",
            "rejected_at_ms",
        )} for row in rows],
    })
    return {"imported": imported, "total": len(rows), "destination": str(destination)}


def rejected_media_assignments(assets: list[dict], rejected: dict[int, set[str]]) -> list[str]:
    violations = []
    for asset in assets:
        if asset.get("media_type") != "photo":
            continue
        try:
            key = normalized_source_key(asset.get("source_url"))
        except ValueError:
            continue
        if key in rejected.get(asset.get("taxon_id"), set()):
            violations.append(asset.get("asset_id", "unknown"))
    return sorted(violations)
