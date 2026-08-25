"""Promote explicitly reviewed refresh candidates into offline source manifests."""

from __future__ import annotations

import argparse
import json
from copy import deepcopy
from datetime import date
from pathlib import Path

from catalogue_content import validate_conservation, validate_descriptions, validate_media
from content_refresh import atomic_write_json, read_json
from refresh_catalogue_content import CATALOGUES, MEDIA_MANIFEST, REVIEW, source_taxa


DECISIONS = REVIEW / "review-decisions.yaml"


def source_policy_decisions(snapshot: dict, reviewer: str, reviewed_at: str) -> dict:
    if not reviewer.strip():
        raise ValueError("Bulk source-policy approval requires a reviewer")
    try:
        date.fromisoformat(reviewed_at)
    except ValueError as error:
        raise ValueError("Bulk source-policy approval has an invalid review date") from error
    rows = []
    reasons = {
        "description": (
            "Owner-directed alpha import of the exact-taxon iNaturalist-linked Wikipedia candidate; "
            "provenance/licence validated automatically and prose not individually edited."
        ),
        "conservation": (
            "Owner-directed alpha import of the exact-taxon iNaturalist conservation candidate; "
            "authority and provenance validated automatically."
        ),
    }
    for kind, key in (
        ("description", "description_candidates"),
        ("conservation", "conservation_candidates"),
    ):
        for candidate in snapshot.get(key, []):
            rows.append({
                "kind": kind,
                "taxon_id": candidate["taxon_id"],
                "decision": "approve",
                "reviewed_by": reviewer.strip(),
                "reviewed_at": reviewed_at,
                "reason": reasons[kind],
            })
    return {
        "schema_version": 1,
        "candidate_snapshot_digest": snapshot.get("snapshot_digest"),
        "decisions": rows,
    }


def _review_fields(row: dict, label: str) -> dict:
    reviewer = row.get("reviewed_by")
    reason = row.get("reason")
    reviewed_at = row.get("reviewed_at")
    if not isinstance(reviewer, str) or not reviewer.strip():
        raise ValueError(f"{label} requires reviewed_by")
    if not isinstance(reason, str) or not reason.strip():
        raise ValueError(f"{label} requires reason")
    if not isinstance(reviewed_at, str):
        raise ValueError(f"{label} requires reviewed_at")
    try:
        date.fromisoformat(reviewed_at)
    except ValueError as error:
        raise ValueError(f"{label} has an invalid reviewed_at date") from error
    return {"reason": reason.strip(), "reviewed_by": reviewer.strip(), "reviewed_at": reviewed_at}


def _candidate_map(snapshot: dict, key: str) -> dict[int, dict]:
    return {row["taxon_id"]: row for row in snapshot.get(key, [])}


def apply_decisions(
    snapshot: dict,
    decisions: dict,
    descriptions: dict,
    conservation: dict,
    media: dict,
    taxon_ids: set[int],
    required_media: set[int],
) -> tuple[dict, dict, dict, dict]:
    if decisions.get("schema_version") != 1:
        raise ValueError("Review decisions require schema_version 1")
    if decisions.get("candidate_snapshot_digest") != snapshot.get("snapshot_digest"):
        raise ValueError("Review decisions do not match the current candidate snapshot")
    candidates = {
        "description": _candidate_map(snapshot, "description_candidates"),
        "conservation": _candidate_map(snapshot, "conservation_candidates"),
        "media": _candidate_map(snapshot, "media_candidates"),
    }
    documents = {
        "description": deepcopy(descriptions),
        "conservation": deepcopy(conservation),
        "media": deepcopy(media),
    }
    row_keys = {"description": "descriptions", "conservation": "conservation", "media": "assets"}
    seen: set[tuple[str, int]] = set()
    counts = {"approved": 0, "waived": 0, "rejected": 0}
    for decision in decisions.get("decisions", []):
        kind = decision.get("kind")
        taxon_id = decision.get("taxon_id")
        outcome = decision.get("decision")
        label = f"review decision {kind}/{taxon_id}"
        if kind not in documents or not isinstance(taxon_id, int) or taxon_id not in taxon_ids:
            raise ValueError(f"{label} has an invalid kind or taxon_id")
        if outcome not in {"approve", "waive", "reject"}:
            raise ValueError(f"{label} requires approve, waive or reject")
        if (kind, taxon_id) in seen:
            raise ValueError(f"Duplicate {label}")
        seen.add((kind, taxon_id))
        review = _review_fields(decision, label)
        document = documents[kind]
        rows_key = row_keys[kind]
        document.setdefault(rows_key, [])
        document.setdefault("waivers", [])
        if outcome == "approve":
            candidate = candidates[kind].get(taxon_id)
            if candidate is None:
                raise ValueError(f"{label} has no candidate in the frozen snapshot")
            document[rows_key] = [row for row in document[rows_key] if row["taxon_id"] != taxon_id]
            document["waivers"] = [row for row in document["waivers"] if row["taxon_id"] != taxon_id]
            document[rows_key].append(candidate)
            counts["approved"] += 1
        elif outcome == "waive":
            document[rows_key] = [row for row in document[rows_key] if row["taxon_id"] != taxon_id]
            document["waivers"] = [row for row in document["waivers"] if row["taxon_id"] != taxon_id]
            document["waivers"].append({"taxon_id": taxon_id, **review})
            counts["waived"] += 1
        else:
            counts["rejected"] += 1

    documents["description"]["descriptions"].sort(key=lambda row: (row["taxon_id"], row["locale"]))
    documents["description"]["waivers"].sort(key=lambda row: row["taxon_id"])
    documents["conservation"]["conservation"].sort(key=lambda row: row["taxon_id"])
    documents["conservation"]["waivers"].sort(key=lambda row: row["taxon_id"])
    documents["media"]["assets"].sort(key=lambda row: (row["taxon_id"], row["asset_id"]))
    documents["media"]["waivers"].sort(key=lambda row: row["taxon_id"])
    validate_descriptions(documents["description"], taxon_ids)
    validate_conservation(documents["conservation"], taxon_ids)
    validate_media(documents["media"], taxon_ids, required_media, strict=False)
    return documents["description"], documents["conservation"], documents["media"], counts


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate or apply reviewed content-refresh decisions.")
    parser.add_argument("--apply", action="store_true", help="Atomically update source manifests after validation")
    parser.add_argument("--decisions", type=Path, default=DECISIONS, help="Reviewed decision document")
    parser.add_argument("--prepare-sourced-approvals", action="store_true", help="Record explicit owner-directed bulk approval of sourced metadata candidates")
    parser.add_argument("--reviewer", help="Reviewer recorded for --prepare-sourced-approvals")
    parser.add_argument("--reviewed-at", default=date.today().isoformat(), help="ISO review date for bulk source-policy approval")
    args = parser.parse_args()
    snapshot = read_json(REVIEW / "candidate-snapshot.json")
    if args.prepare_sourced_approvals:
        if not args.reviewer:
            parser.error("--prepare-sourced-approvals requires --reviewer")
        decisions = source_policy_decisions(snapshot, args.reviewer, args.reviewed_at)
        atomic_write_json(args.decisions, decisions)
    else:
        decisions = read_json(args.decisions)
    taxa, _, required_media = source_taxa()
    taxon_ids = set(taxa)
    updated_descriptions, updated_conservation, updated_media, counts = apply_decisions(
        snapshot,
        decisions,
        read_json(CATALOGUES / "taxon_descriptions.yaml"),
        read_json(CATALOGUES / "taxon_conservation.yaml"),
        read_json(MEDIA_MANIFEST),
        taxon_ids,
        required_media,
    )
    if args.apply:
        atomic_write_json(CATALOGUES / "taxon_descriptions.yaml", updated_descriptions)
        atomic_write_json(CATALOGUES / "taxon_conservation.yaml", updated_conservation)
        atomic_write_json(MEDIA_MANIFEST, updated_media)
    print(json.dumps({**counts, "applied": args.apply}, indent=2))


if __name__ == "__main__":
    main()
