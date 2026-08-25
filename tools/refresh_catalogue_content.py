"""Refresh reviewable species metadata and resolve curated media outside the Android build.

The default command freezes remote candidates and emits review queues. ``--apply-media`` upgrades
already-curated Commons source pages only after both direct variants have been downloaded, decoded,
dimension-checked and hashed. Description and conservation candidates always require editorial
promotion into their source manifests.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import re
from datetime import date
from pathlib import Path

from catalogue_content import validate_media, validate_taxa
from content_refresh import (
    CachedHttpClient,
    INaturalistMediaResolver,
    INaturalistResolver,
    PhyloPicResolver,
    WikimediaResolver,
    atomic_write,
    atomic_write_json,
    conservation_candidate,
    description_candidate,
    read_json,
    snapshot_digest,
)


ROOT = Path(__file__).resolve().parents[1]
CATALOGUES = ROOT / "catalogues"
REVIEW = CATALOGUES / "review" / "content_refresh"
MEDIA_MANIFEST = CATALOGUES / "media_manifest.yaml"


def source_taxa() -> tuple[dict[int, dict], set[int], set[int]]:
    taxa = {row["taxon_id"]: row for row in read_json(CATALOGUES / "taxa.yaml")["taxa"]}
    published: set[int] = set()
    published_regions: set[str] = set()
    for catalogue_path in sorted((CATALOGUES / "regions").glob("*/catalogue.yaml")):
        catalogue = read_json(catalogue_path)
        region = catalogue.get("region")
        if not isinstance(region, str) or not region:
            raise ValueError(f"Regional catalogue has no region key: {catalogue_path}")
        published_regions.add(region)
        published.update(row["taxon_id"] for row in catalogue.get("taxa", []))
    achievements = read_json(CATALOGUES / "achievements.yaml").get("achievements", [])
    required_media = {
        taxon_id
        for achievement in achievements
        if achievement.get("region") in published_regions
        for taxon_id in achievement.get("essentials", []) + achievement.get("icons", [])
    }
    unknown = published - set(taxa)
    if unknown:
        raise ValueError(f"Published catalogues reference unknown global taxa: {sorted(unknown)}")
    return taxa, published, required_media


def coverage(document: dict, rows_key: str) -> tuple[set[int], set[int]]:
    covered = {row["taxon_id"] for row in document.get(rows_key, [])}
    waived = {row["taxon_id"] for row in document.get("waivers", [])}
    return covered, waived


def media_coverage(document: dict) -> tuple[set[int], set[int], set[int]]:
    items = document.get("assets", document.get("media", []))
    provenance = {item["taxon_id"] for item in items}
    direct = {
        item["taxon_id"]
        for item in items
        if {variant.get("variant") for variant in item.get("variants", [])} >= {"thumbnail", "detail"}
    }
    waived = {item["taxon_id"] for item in document.get("waivers", [])}
    return provenance, direct, waived


def media_resolution_inputs(document: dict) -> list[dict]:
    if document.get("schema_version") == 1:
        return document.get("media", [])
    if document.get("schema_version") not in {2, 3}:
        raise ValueError("Unsupported media manifest schema")
    inputs = []
    for asset in document.get("assets", []):
        if asset.get("provider") != "wikimedia_commons":
            continue
        match = re.search(r"/(?:licenses/[^/]+|publicdomain/(?:zero|mark))/(\d+(?:\.\d+)?)/", asset["licence_url"])
        inputs.append({
            "taxon_id": asset["taxon_id"],
            "scientific_name": asset.get("matched_taxon_name"),
            "source_url": asset["source_url"],
            "creator": asset["creator"],
            "licence_code": asset["licence_code"],
            "licence_version": match.group(1) if match else "",
            "selection_note": asset.get("assessment", "Reviewed exact-species reference photo."),
        })
    return inputs


def write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        raise ValueError(f"Refusing to write an empty review queue: {path}")
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=list(rows[0]), lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    atomic_write(path, stream.getvalue().encode("utf-8"))


def taxonomy_from_evidence(taxon: dict, evidence: dict[int, dict]) -> dict[str, str]:
    ranks = {"kingdom", "phylum", "class", "order", "family", "genus"}
    taxonomy: dict[str, str] = {}
    for ancestor_id in taxon.get("ancestor_ids", []):
        ancestor = evidence.get(ancestor_id)
        if not isinstance(ancestor, dict):
            continue
        rank, name = ancestor.get("rank"), ancestor.get("name")
        if rank in ranks and isinstance(name, str) and name.strip():
            taxonomy[rank] = name.strip()
    return taxonomy


def apply_taxonomy_evidence(
    taxa_document: dict,
    selected: set[int],
    taxa_evidence: dict[int, dict],
    ancestor_evidence: dict[int, dict],
) -> dict:
    combined = {**ancestor_evidence, **taxa_evidence}
    updated = []
    for source in taxa_document.get("taxa", []):
        row = dict(source)
        taxon_id = row.get("taxon_id")
        evidence = taxa_evidence.get(taxon_id) if taxon_id in selected else None
        if evidence is not None:
            if evidence.get("is_active") is False:
                raise ValueError(f"Taxon {taxon_id} is inactive and requires an explicit taxon change")
            if evidence.get("name") != row.get("scientific_name"):
                raise ValueError(f"Taxon {taxon_id} scientific name changed and requires explicit review")
            if evidence.get("rank") != row.get("rank"):
                raise ValueError(f"Taxon {taxon_id} rank changed and requires explicit review")
            taxonomy = taxonomy_from_evidence(evidence, combined)
            if taxonomy:
                row["taxonomy"] = taxonomy
            common_name = evidence.get("preferred_common_name")
            if isinstance(common_name, str) and common_name.strip():
                row["common_names"] = {**row.get("common_names", {}), "en": common_name.strip()}
            revision = evidence.get("updated_at")
            if isinstance(revision, str) and revision:
                row["source_revision"] = f"inaturalist-taxon:{taxon_id}:{revision}"
        updated.append(row)
    result = {**taxa_document, "taxa": updated}
    validate_taxa(result["taxa"])
    return result


def queue_rows(
    taxa: dict[int, dict],
    published: set[int],
    existing: set[int],
    waived: set[int],
    candidates: dict[int, dict],
    kind: str,
) -> list[dict]:
    rows = []
    for taxon_id in sorted(published):
        candidate = candidates.get(taxon_id)
        state = "published" if taxon_id in existing else "waived" if taxon_id in waived else "candidate" if candidate else "missing"
        rows.append({
            "taxon_id": taxon_id,
            "scientific_name": taxa[taxon_id]["scientific_name"],
            "state": state,
            "candidate_source_url": candidate.get("source_url", "") if candidate else "",
            "review_action": "none" if state in {"published", "waived"} else f"review_{kind}" if candidate else "source_or_waive",
        })
    return rows


def media_queue_rows(
    taxa: dict[int, dict],
    published: set[int],
    required: set[int],
    media_document: dict,
    resolved_taxa: set[int],
    failures: list[dict],
) -> list[dict]:
    provenance, direct, waived = media_coverage(media_document)
    failure_ids = {row["taxon_id"] for row in failures}
    rows = []
    for taxon_id in sorted(published):
        if taxon_id in direct:
            state = "published_direct"
        elif taxon_id in resolved_taxa:
            state = "resolved_candidate"
        elif taxon_id in waived:
            state = "waived"
        elif taxon_id in failure_ids:
            state = "resolution_failed"
        elif taxon_id in provenance:
            state = "legacy_provenance"
        else:
            state = "missing"
        rows.append({
            "taxon_id": taxon_id,
            "scientific_name": taxa[taxon_id]["scientific_name"],
            "achievement_required": "yes" if taxon_id in required else "no",
            "state": state,
            "review_action": "none" if state in {"published_direct", "waived"} else "resolve_or_waive" if taxon_id in required else "optional_source",
        })
    return rows


def run(args: argparse.Namespace) -> dict:
    if args.offline and args.refresh:
        raise ValueError("--offline and --refresh cannot be combined")
    taxa, published, required_media = source_taxa()
    selected = sorted(published)
    if args.taxon_id:
        requested = set(args.taxon_id)
        unknown = requested - published
        if unknown:
            raise ValueError(f"--taxon-id contains unpublished taxa: {sorted(unknown)}")
        selected = sorted(requested)
    if args.limit:
        selected = selected[:args.limit]

    prior_snapshot_path = REVIEW / "candidate-snapshot.json"
    prior_snapshot = read_json(prior_snapshot_path) if prior_snapshot_path.exists() else {}
    prior_description_candidates = {
        row["taxon_id"]: row for row in prior_snapshot.get("description_candidates", [])
    }
    prior_conservation_candidates = {
        row["taxon_id"]: row for row in prior_snapshot.get("conservation_candidates", [])
    }
    prior_media_candidates = prior_snapshot.get("media_candidates", [])
    prior_media_failures = prior_snapshot.get("media_failures", [])

    client = CachedHttpClient(REVIEW, offline=args.offline, refresh=args.refresh, delay=args.delay)
    inaturalist_taxa: dict[int, dict] = {}
    missing_remote_taxa: list[int] = []
    if not args.skip_taxa:
        inaturalist_taxa, missing_remote_taxa = INaturalistResolver(client).resolve(selected)
    if args.apply_taxonomy:
        if args.skip_taxa:
            raise ValueError("--apply-taxonomy cannot be combined with --skip-taxa")
        ancestor_ids = sorted({
            ancestor_id
            for taxon in inaturalist_taxa.values()
            for ancestor_id in taxon.get("ancestor_ids", [])
            if ancestor_id not in inaturalist_taxa
        })
        ancestor_taxa, missing_ancestors = INaturalistResolver(client).resolve(ancestor_ids)
        if missing_ancestors:
            raise ValueError(f"Missing iNaturalist ancestor evidence: {missing_ancestors[:20]}")
        taxonomy_document = apply_taxonomy_evidence(
            read_json(CATALOGUES / "taxa.yaml"),
            set(selected),
            inaturalist_taxa,
            ancestor_taxa,
        )
        atomic_write_json(CATALOGUES / "taxa.yaml", taxonomy_document)
    refreshed_description_candidates = {
        taxon_id: candidate
        for taxon_id, row in inaturalist_taxa.items()
        if (candidate := description_candidate(row, args.retrieved_at)) is not None
    }
    refreshed_conservation_candidates = {
        taxon_id: candidate
        for taxon_id, row in inaturalist_taxa.items()
        if (candidate := conservation_candidate(row, args.retrieved_at)) is not None
    }
    if args.skip_taxa:
        description_candidates = prior_description_candidates
        conservation_candidates = prior_conservation_candidates
        missing_remote_taxa = prior_snapshot.get("missing_inaturalist_taxa", [])
        snapshot_taxa = prior_snapshot.get("selected_taxa", [])
    else:
        description_candidates = {
            taxon_id: candidate
            for taxon_id, candidate in prior_description_candidates.items()
            if taxon_id not in selected
        }
        description_candidates.update(refreshed_description_candidates)
        conservation_candidates = {
            taxon_id: candidate
            for taxon_id, candidate in prior_conservation_candidates.items()
            if taxon_id not in selected
        }
        conservation_candidates.update(refreshed_conservation_candidates)
        prior_missing = set(prior_snapshot.get("missing_inaturalist_taxa", [])) - set(selected)
        missing_remote_taxa = sorted(prior_missing.union(missing_remote_taxa))
        snapshot_taxa = sorted(set(prior_snapshot.get("selected_taxa", [])).union(selected))

    media_document = read_json(MEDIA_MANIFEST)
    resolved_assets: list[dict] = []
    media_failures: list[dict] = []
    if args.skip_media:
        resolved_assets = prior_media_candidates or media_document.get("assets", [])
        media_failures = prior_media_failures
    elif not args.skip_media:
        commons_inputs = media_resolution_inputs(media_document)
        for item in commons_inputs:
            item["scientific_name"] = taxa[item["taxon_id"]]["scientific_name"]
        commons_assets, commons_failures = WikimediaResolver(client).resolve(
            commons_inputs, args.retrieved_at
        )
        resolved_by_key = {}
        for source in media_document.get("assets", []):
            row = dict(source)
            matched_id = row.get("matched_taxon_id")
            if not row.get("matched_taxon_name") and matched_id in taxa:
                row["matched_taxon_name"] = taxa[matched_id]["scientific_name"]
            resolved_by_key[(row["taxon_id"], row["media_type"])] = row
        resolved_by_key.update({
            (row["taxon_id"], row["media_type"]): row
            for row in prior_media_candidates
            if row["taxon_id"] not in selected
        })
        resolved_by_key.update({(row["taxon_id"], row["media_type"]): row for row in commons_assets})
        inaturalist_assets, inaturalist_failures = INaturalistMediaResolver(client).resolve(
            [row for taxon_id, row in inaturalist_taxa.items() if (taxon_id, "photo") not in resolved_by_key],
            args.retrieved_at,
        )
        for row in inaturalist_assets:
            resolved_by_key.setdefault((row["taxon_id"], "photo"), row)
        silhouette_assets, silhouette_failures = PhyloPicResolver(client).resolve(
            [taxa[taxon_id] for taxon_id in selected],
            args.retrieved_at,
        )
        for row in silhouette_assets:
            resolved_by_key[(row["taxon_id"], "silhouette")] = row
        resolved_assets = list(resolved_by_key.values())
        media_failures = [
            row for row in prior_media_failures if row["taxon_id"] not in selected
        ] + commons_failures + inaturalist_failures + silhouette_failures

    descriptions = read_json(CATALOGUES / "taxon_descriptions.yaml")
    conservation = read_json(CATALOGUES / "taxon_conservation.yaml")
    description_existing, description_waived = coverage(descriptions, "descriptions")
    conservation_existing, conservation_waived = coverage(conservation, "conservation")
    snapshot = {
        "schema_version": 1,
        "retrieved_at": args.retrieved_at,
        "selected_taxa": snapshot_taxa,
        "missing_inaturalist_taxa": missing_remote_taxa,
        "description_candidates": [description_candidates[key] for key in sorted(description_candidates)],
        "conservation_candidates": [conservation_candidates[key] for key in sorted(conservation_candidates)],
        "media_candidates": sorted(resolved_assets, key=lambda row: (row["taxon_id"], row["asset_id"])),
        "media_failures": sorted(media_failures, key=lambda row: row["taxon_id"]),
    }
    snapshot["snapshot_digest"] = snapshot_digest(snapshot)
    atomic_write_json(REVIEW / "candidate-snapshot.json", snapshot)
    description_queue = queue_rows(
        taxa, published, description_existing, description_waived, description_candidates, "description"
    )
    conservation_queue = queue_rows(
        taxa, published, conservation_existing, conservation_waived, conservation_candidates, "conservation"
    )
    media_queue = media_queue_rows(
        taxa,
        published,
        required_media,
        media_document,
        {row["taxon_id"] for row in resolved_assets},
        media_failures,
    )
    write_csv(REVIEW / "description-queue.csv", description_queue)
    write_csv(REVIEW / "conservation-queue.csv", conservation_queue)
    write_csv(REVIEW / "media-queue.csv", media_queue)

    if args.apply_media:
        if args.skip_media:
            raise ValueError("--apply-media cannot be combined with --skip-media")
        if media_failures:
            raise ValueError(f"Refusing to apply media with {len(media_failures)} resolution failures")
        upgraded = {
            "schema_version": 3,
            "assets": snapshot["media_candidates"],
            "waivers": media_document.get("waivers", []),
        }
        validate_media(upgraded, taxa, required_media, strict=True)
        atomic_write_json(MEDIA_MANIFEST, upgraded)

    media_bytes = {
        variant: sum(
            item.get("expected_bytes", 0)
            for asset in resolved_assets
            for item in asset.get("variants", [])
            if item.get("variant") == variant
        )
        for variant in ("thumbnail", "detail")
    }
    summary = {
        "schema_version": 1,
        "snapshot_digest": snapshot["snapshot_digest"],
        "published_taxa": len(published),
        "selected_taxa": len(selected),
        "description_candidates": len(description_candidates),
        "description_without_candidate": sum(row["state"] == "missing" for row in description_queue),
        "conservation_candidates": len(conservation_candidates),
        "conservation_without_candidate": sum(row["state"] == "missing" for row in conservation_queue),
        "resolved_media_assets": len(resolved_assets),
        "media_thumbnail_bytes": media_bytes["thumbnail"],
        "media_detail_bytes": media_bytes["detail"],
        "media_failures": len(media_failures),
        "media_applied": bool(args.apply_media),
    }
    atomic_write_json(REVIEW / "refresh-report.json", summary)
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description="Refresh reviewable published-species content evidence.")
    parser.add_argument("--offline", action="store_true", help="Use only previously frozen remote evidence")
    parser.add_argument("--refresh", action="store_true", help="Replace previously frozen remote evidence")
    parser.add_argument("--skip-taxa", action="store_true", help="Do not refresh iNaturalist text/status candidates")
    parser.add_argument("--skip-media", action="store_true", help="Do not resolve or validate curated media")
    parser.add_argument("--apply-media", action="store_true", help="Replace legacy media only after strict byte validation")
    parser.add_argument("--apply-taxonomy", action="store_true", help="Apply exact iNaturalist ancestor taxonomy after validation")
    parser.add_argument("--taxon-id", action="append", type=int, help="Refresh one published taxon; repeatable")
    parser.add_argument("--limit", type=int, help="Limit the sorted published taxon set for a bounded review run")
    parser.add_argument("--delay", type=float, default=1.05, help="Minimum delay between public API requests")
    parser.add_argument("--retrieved-at", default=date.today().isoformat(), help="Frozen ISO retrieval date")
    args = parser.parse_args()
    if args.limit is not None and args.limit <= 0:
        parser.error("--limit must be positive")
    if args.delay < 0:
        parser.error("--delay cannot be negative")
    result = run(args)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
