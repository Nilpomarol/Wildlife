"""Create a reviewable regional catalogue candidate list from public iNaturalist data.

This tool ranks candidates; it never changes a frozen catalogue or assigns game rarity.
"""
from __future__ import annotations

import argparse
import csv
import json
import time
import urllib.parse
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalogues" / "candidate_sources.yaml"
EXCLUSIONS = ROOT / "catalogues" / "candidate_exclusions.yaml"
OUTPUT = ROOT / "catalogues" / "review"
RAW_CACHE = OUTPUT / "inaturalist_raw"
API = "https://api.inaturalist.org/v1/observations/species_counts"
USER_AGENT = "WildlifeCatalogueAuthoring/1.0 (read-only candidate review)"


def fetch(params: dict[str, str | int]) -> dict:
    request = urllib.request.Request(
        f"{API}?{urllib.parse.urlencode(params)}",
        headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as error:
            rate_limited = isinstance(error, urllib.error.HTTPError) and error.code == 429
            if not rate_limited and attempt == 3:
                raise
            if rate_limited and attempt == 3:
                raise
            delay = (15 if rate_limited else 5) * (attempt + 1)
            print(f"Public request failed ({error}); waiting {delay}s before retrying.", flush=True)
            time.sleep(delay)
    raise AssertionError("unreachable")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("region", help="Regional key from catalogues/candidate_sources.yaml")
    parser.add_argument("--pages", type=int, default=1, help="200-result pages requested per animal group")
    parser.add_argument("--page-start", type=int, default=1, help="First public result page to fetch")
    parser.add_argument("--refresh", action="store_true", help="Re-fetch pages already stored locally")
    parser.add_argument("--delay", type=float, default=1.2, help="Seconds between public API requests")
    args = parser.parse_args()

    source = json.loads(SOURCE.read_text(encoding="utf-8"))["regions"].get(args.region)
    if source is None:
        raise SystemExit(f"Unknown candidate source region: {args.region}")
    exclusions = {int(key): value for key, value in json.loads(EXCLUSIONS.read_text(encoding="utf-8"))
                  .get(args.region, {}).items()}

    RAW_CACHE.mkdir(parents=True, exist_ok=True)
    candidates: dict[tuple[str, int], dict] = {}
    regional_place_ids = ",".join(str(place["id"]) for place in source["places"])
    for group in source["groups"]:
        for place in [{"id": regional_place_ids, "name": "regional union"}]:
            for page in range(args.page_start, args.page_start + args.pages):
                cache = RAW_CACHE / f"{args.region}_{group['key']}_{page}.json"
                if not cache.exists() or args.refresh:
                    data = fetch({
                        "place_id": place["id"], "taxon_id": group["taxon_id"], "rank": "species",
                        "verifiable": "true", "per_page": 200, "page": page,
                        "order": "desc", "order_by": "observations_count",
                    })
                    cache.write_text(json.dumps(data), encoding="utf-8")
                    print(f"Fetched {group['key']} page {page} for {place['name']}", flush=True)
                    time.sleep(args.delay)
            for cache in sorted(RAW_CACHE.glob(f"{args.region}_{group['key']}_*.json")):
                data = json.loads(cache.read_text(encoding="utf-8"))
                for result in data.get("results", []):
                    taxon = result["taxon"]
                    key = (group["key"], taxon["id"])
                    candidate = candidates.setdefault(key, {
                        "group": group["key"], "target": group["target"], "taxon_id": taxon["id"],
                        "common_name": taxon.get("preferred_common_name") or taxon["name"],
                        "scientific_name": taxon["name"], "country_coverage": set(), "observations": 0,
                    })
                    candidate["country_coverage"].add(place["name"])
                    candidate["observations"] += result.get("count", 0)

    rows = []
    for group in source["groups"]:
        ranked = sorted(
            (item for item in candidates.values() if item["group"] == group["key"]),
            key=lambda item: (-item["observations"], item["scientific_name"]),
        )
        included = 0
        for position, item in enumerate(ranked, start=1):
            exclusion = exclusions.get(item["taxon_id"])
            if exclusion is None:
                included += 1
            rows.append({
                "group": item["group"], "rank": position,
                "recommended": "exclude" if exclusion else ("yes" if included <= group["target"] else "no"),
                "taxon_id": item["taxon_id"], "common_name": item["common_name"],
                "scientific_name": item["scientific_name"], "countries_with_records": len(item["country_coverage"]),
                "observation_count": item["observations"],
                "review_note": exclusion or "iNaturalist regional candidate only; review habitat fit, introduced status and game value before inclusion.",
            })
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / f"{args.region}_inaturalist_candidates.csv"
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} candidates to {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
