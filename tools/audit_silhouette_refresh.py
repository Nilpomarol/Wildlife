"""Read-only audit of catalogue silhouette coverage and cached PhyloPic evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from tools.content_refresh import PhyloPicResolver, read_json


ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
DEFAULT_CACHE = CATALOGUES / "review" / "catalogue_pipeline" / "cache"
DEFAULT_RUN_REPORT = CATALOGUES / "review" / "catalogue_pipeline" / "last-run.json"
ROLE_RANKS = {"specific": ("species", "genus"), "family": ("family", "order")}
RANK_FIELDS = {"species": "scientific_name", "genus": "genus", "family": "family", "order": "order"}
CLASS_GROUPS = {
    "Mammalia": "mammals",
    "Aves": "birds",
    "Reptilia": "reptiles",
    "Amphibia": "amphibians",
    "Actinopterygii": "fish",
    "Chondrichthyes": "fish",
    "Myxini": "fish",
    "Petromyzonti": "fish",
}


class MissingCacheEvidence(ValueError):
    pass


class ReadOnlyCacheClient:
    """The small CachedHttpClient surface used by PhyloPicResolver, without writes/network."""

    def __init__(self, cache_root: Path):
        self.cache_root = cache_root
        self.loaded_urls: list[str] = []
        self.loaded_image_records = 0

    def json(self, namespace: str, key: str, url: str, normalizer=None) -> dict:
        path = self.cache_root / "raw" / namespace / f"{key}.json.gz"
        legacy = path.with_suffix("")
        source = path if path.is_file() else legacy
        if not source.is_file():
            raise MissingCacheEvidence(f"No cached provider response for {url}")
        value = read_json(source)
        self.loaded_urls.append(url)
        if _contains_image_record(value):
            self.loaded_image_records += 1
        return normalizer(value) if normalizer else value

    def validate_image(self, direct_url: str, retrieved_at: str) -> dict:
        key = hashlib.sha256(direct_url.encode()).hexdigest()
        path = self.cache_root / "validated" / f"{key}.json"
        if not path.is_file():
            raise MissingCacheEvidence(f"Provider metadata exists but image validation is not cached for {direct_url}")
        value = read_json(path)
        if value.get("direct_url") != direct_url:
            raise ValueError(f"Image validation cache collision for {direct_url}")
        return value


def _contains_image_record(value: object) -> bool:
    if isinstance(value, dict):
        links = value.get("_links")
        if isinstance(value.get("uuid"), str) and isinstance(links, dict):
            return "rasterFiles" in links or "license" in links
        return any(_contains_image_record(item) for item in value.values())
    if isinstance(value, list):
        return any(_contains_image_record(item) for item in value)
    return False


def silhouette_role(asset: dict) -> str:
    if asset.get("match_rank") in {"species", "genus"}:
        return "specific"
    if asset.get("match_rank") in {"family", "order"}:
        return "family"
    return "group"


def published_taxa(catalogues: Path) -> tuple[set[int], dict[str, set[int]]]:
    by_region: dict[str, set[int]] = {}
    for path in sorted((catalogues / "regions").glob("*/catalogue.yaml")):
        document = json.loads(path.read_text(encoding="utf-8"))
        by_region[path.parent.name] = {
            row["taxon_id"] for row in document.get("taxa", []) if isinstance(row.get("taxon_id"), int)
        }
    return set().union(*by_region.values()) if by_region else set(), by_region


def classify_cached_role(
    taxon: dict,
    role: str,
    cache_root: Path,
    resolver: PhyloPicResolver | None = None,
) -> dict:
    rejected_evidence = False
    checked: list[dict] = []
    resolver = resolver or PhyloPicResolver(ReadOnlyCacheClient(cache_root))
    for rank in ROLE_RANKS[role]:
        value = taxon.get(RANK_FIELDS[rank]) if rank == "species" else taxon.get("taxonomy", {}).get(RANK_FIELDS[rank])
        if not isinstance(value, str) or not value.strip():
            continue
        try:
            match = resolver._resolve_name(value, "audit")
        except MissingCacheEvidence as error:
            checked.append({"rank": rank, "name": value, "result": "incomplete", "detail": str(error)})
            return {"reason": "not_attempted_or_incomplete_cache", "checked": checked}
        except (KeyError, TypeError, ValueError) as error:
            checked.append({"rank": rank, "name": value, "result": "rejected", "detail": str(error)})
            rejected_evidence = True
            continue
        if match is not None:
            checked.append({"rank": rank, "name": value, "result": "resolved"})
            return {"reason": "resolved_in_cache_not_published", "match_rank": rank, "checked": checked}
        normalized = resolver._normalized_name(value)
        result = "rejected" if resolver._image_index and resolver._image_index.get(normalized) else "no_match"
        checked.append({"rank": rank, "name": value, "result": result})
        rejected_evidence = rejected_evidence or result == "rejected"
    return {
        "reason": "cached_asset_rejected" if rejected_evidence else "cached_clean_no_match",
        "checked": checked,
    }


def _failure_taxa(run_report: dict) -> set[int]:
    return {
        row["taxon_id"] for row in run_report.get("failures", [])
        if row.get("stage") == "phylopic" and isinstance(row.get("taxon_id"), int)
    }


def audit(root: Path = ROOT) -> dict:
    catalogues = root / "catalogues"
    cache_root = catalogues / "review" / "catalogue_pipeline" / "cache"
    run_path = catalogues / "review" / "catalogue_pipeline" / "last-run.json"
    published, regions = published_taxa(catalogues)
    taxa_doc = json.loads((catalogues / "taxa.yaml").read_text(encoding="utf-8"))
    taxa = {row["taxon_id"]: row for row in taxa_doc.get("taxa", []) if row.get("taxon_id") in published}
    manifest = json.loads((catalogues / "media_manifest.yaml").read_text(encoding="utf-8"))
    assignments = {
        (row.get("taxon_id"), silhouette_role(row))
        for row in manifest.get("assets", []) if row.get("media_type") == "silhouette"
    }
    run_report = json.loads(run_path.read_text(encoding="utf-8")) if run_path.is_file() else {}
    failure_taxa = _failure_taxa(run_report)
    stage_status = {row.get("name"): row.get("status") for row in run_report.get("stages", [])}
    run_complete = stage_status.get("photos-silhouettes") in {"complete", "degraded"}
    cache_resolver = PhyloPicResolver(ReadOnlyCacheClient(cache_root))
    try:
        cache_resolver.prepare()
        index_error = None
    except (MissingCacheEvidence, KeyError, TypeError, ValueError) as error:
        index_error = str(error)

    rows: list[dict] = []
    for taxon_id in sorted(published):
        taxon = taxa.get(taxon_id)
        if taxon is None:
            continue
        group = CLASS_GROUPS.get(taxon.get("taxonomy", {}).get("class"), "other")
        family = taxon.get("taxonomy", {}).get("family") or "unknown"
        for role in ROLE_RANKS:
            if (taxon_id, role) in assignments:
                outcome = {"reason": "published"}
            elif taxon_id in failure_taxa:
                outcome = {"reason": "request_failure"}
            elif index_error is not None:
                outcome = {
                    "reason": "not_attempted_or_incomplete_cache",
                    "checked": [{"result": "incomplete", "detail": index_error}],
                }
            else:
                outcome = classify_cached_role(taxon, role, cache_root, cache_resolver)
            rows.append({
                "taxon_id": taxon_id,
                "scientific_name": taxon.get("scientific_name"),
                "group": group,
                "family": family,
                "role": role,
                **outcome,
            })

    summary = {}
    for role in ROLE_RANKS:
        role_rows = [row for row in rows if row["role"] == role]
        summary[role] = dict(sorted(Counter(row["reason"] for row in role_rows).items()))
    by_group: dict[str, dict[str, dict[str, int]]] = defaultdict(dict)
    for group in sorted({row["group"] for row in rows}):
        for role in ROLE_RANKS:
            selected = [row for row in rows if row["group"] == group and row["role"] == role]
            by_group[group][role] = dict(sorted(Counter(row["reason"] for row in selected).items()))

    raw_cache = cache_root / "raw" / "phylopic"
    validated_cache = cache_root / "validated"
    return {
        "schema_version": 1,
        "audited_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "mode": "read-only-cache-audit",
        "refresh_report_complete": run_complete,
        "refresh_report_stages": run_report.get("stages", []),
        "published_taxa": len(published),
        "regions": {name: len(values) for name, values in sorted(regions.items())},
        "cache": {
            "phylopic_responses": len(list(raw_cache.glob("*.json*"))) if raw_cache.is_dir() else 0,
            "validated_media_records": len(list(validated_cache.glob("*.json"))) if validated_cache.is_dir() else 0,
        },
        "summary": summary,
        "by_group": dict(by_group),
        "details": rows,
        "interpretation": (
            "Results other than published are provisional while refresh_report_complete is false. "
            "not_attempted_or_incomplete_cache means the resolver lacks enough cached evidence to decide; "
            "cached_clean_no_match is a confirmed exact-name provider gap; cached_asset_rejected means cached "
            "image records existed but none passed the production resolver; resolved_in_cache_not_published "
            "means eligible cached media has not yet reached the manifest."
        ),
    }


def render(report: dict) -> str:
    state = "complete" if report["refresh_report_complete"] else "LIVE / INCOMPLETE"
    lines = [
        f"Silhouette refresh audit ({state})",
        f"Published taxa: {report['published_taxa']}",
        f"PhyloPic cache: {report['cache']['phylopic_responses']} responses",
        "",
    ]
    labels = {
        "published": "published",
        "resolved_in_cache_not_published": "resolved, awaiting publication",
        "cached_clean_no_match": "confirmed provider no-match",
        "cached_asset_rejected": "cached asset rejected",
        "request_failure": "request failure",
        "not_attempted_or_incomplete_cache": "not attempted / incomplete cache",
    }
    for role, values in report["summary"].items():
        lines.append(f"{role.capitalize()} silhouettes")
        for reason, count in values.items():
            lines.append(f"  {labels.get(reason, reason)}: {count}")
        lines.append("")
    if not report["refresh_report_complete"]:
        lines.append("This is a provisional snapshot. Run it again after the refresh finishes for final provider-gap counts.")
    return "\n".join(lines).rstrip()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Print the complete machine-readable report.")
    parser.add_argument("--output", type=Path, help="Also save the complete JSON report to this path.")
    args = parser.parse_args(argv)
    report = audit()
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True) if args.json else render(report))
    return 0


if __name__ == "__main__":
    sys.exit(main())
