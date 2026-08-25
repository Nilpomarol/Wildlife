from __future__ import annotations

import hashlib
import re
import urllib.parse
from typing import Callable

from tools.content_refresh import (
    COMMONS_API,
    CachedHttpClient,
    WikimediaResolver,
    clean_html,
    compatible_licence_url,
    normalise_licence,
)
from tools.reference_media_rejections import normalized_source_key


def _text(value: object) -> str:
    if isinstance(value, dict):
        value = value.get("value")
    return clean_html(value) if isinstance(value, str) else ""


def score_commons_page(
    page: dict,
    scientific_name: str,
    *,
    minimum_ratio: float,
    maximum_ratio: float,
    reject_terms: set[str],
) -> tuple[float, dict] | None:
    """Apply hard safety/licence/species checks, then score a Commons candidate."""
    try:
        info = page["imageinfo"][0]
        metadata = info.get("extmetadata", {})
        width, height = int(info["width"]), int(info["height"])
    except (KeyError, IndexError, TypeError, ValueError):
        return None
    if width <= 0 or height <= 0:
        return None
    ratio = width / height
    if not minimum_ratio <= ratio <= maximum_ratio:
        return None
    licence = normalise_licence(_text(metadata.get("LicenseShortName")))
    raw_licence = _text(metadata.get("LicenseShortName"))
    version_match = re.search(r"(\d+(?:\.\d+)*)\s*$", raw_licence)
    version = version_match.group(1) if version_match else "1.0" if licence in {"cc0", "pdm"} else ""
    if licence not in {"cc0", "cc-by", "cc-by-sa", "pdm"}:
        return None
    licence_url = compatible_licence_url(licence, version)
    if not licence_url:
        remote_url = _text(metadata.get("LicenseUrl"))
        licence_url = remote_url if remote_url.startswith("https://") else None
    if not licence_url:
        return None

    title = str(page.get("title", ""))
    description = _text(metadata.get("ImageDescription"))
    categories = _text(metadata.get("Categories"))
    assessments = _text(metadata.get("Assessments"))
    searchable = " ".join((title, description, categories)).casefold()
    normalized = " ".join(scientific_name.casefold().split())
    if normalized not in searchable:
        return None
    tokens = set(re.findall(r"[a-z]+", searchable))
    if tokens.intersection(reject_terms):
        return None

    score = 100.0
    if normalized in title.casefold():
        score += 35
    if normalized in description.casefold():
        score += 20
    assessment_text = assessments.casefold()
    if "featured" in assessment_text:
        score += 30
    if "quality" in assessment_text:
        score += 20
    score += min(width, 6000) / 600
    score -= abs(ratio - 1.5) * 8
    creator = _text(metadata.get("Artist")) or _text(metadata.get("Credit"))
    if not creator:
        return None
    return score, {
        "title": title,
        "creator": creator,
        "licence_code": licence,
        "licence_version": version,
        "licence_url": licence_url,
        "ratio": ratio,
        "score": score,
    }


class CommonsSearchResolver:
    def __init__(
        self,
        client: CachedHttpClient,
        policy: dict,
        rejected_sources: dict[int, set[str]] | None = None,
    ):
        self.client = client
        self.policy = policy
        self.curated = WikimediaResolver(client)
        self.rejected_sources = rejected_sources or {}

    def _search(self, scientific_name: str) -> list[dict]:
        params: dict[str, str | int] = {
            "action": "query",
            "format": "json",
            "formatversion": 2,
            "generator": "search",
            "gsrsearch": f'"{scientific_name}" filetype:bitmap',
            "gsrnamespace": 6,
            "gsrlimit": int(self.policy["commons_search_limit"]),
            "prop": "imageinfo",
            "iilimit": 1,
            "iiprop": "timestamp|user|url|size|mime|sha1|extmetadata",
            "iiextmetadatafilter": (
                "Artist|Credit|LicenseShortName|LicenseUrl|ImageDescription|Categories|Assessments"
            ),
            "iiextmetadatalanguage": "en",
        }
        url = f"{COMMONS_API}?{urllib.parse.urlencode(params)}"
        key = hashlib.sha256(scientific_name.casefold().encode()).hexdigest()[:20]
        response = self.client.json("wikimedia-search", key, url)
        pages = response.get("query", {}).get("pages", [])
        return pages if isinstance(pages, list) else []

    def _ranked_inputs(self, taxon: dict) -> list[dict]:
        scientific_name = taxon["scientific_name"]
        reject_terms = {str(term).casefold() for term in self.policy["reject_terms"]}
        ranked = []
        for page in self._search(scientific_name):
            scored = score_commons_page(
                page,
                scientific_name,
                minimum_ratio=float(self.policy["minimum_landscape_ratio"]),
                maximum_ratio=float(self.policy["maximum_landscape_ratio"]),
                reject_terms=reject_terms,
            )
            if scored is not None:
                ranked.append((scored[0], int(page.get("pageid", 0)), scored[1]))
        inputs = []
        for _, _, candidate in sorted(ranked, key=lambda item: (-item[0], item[1])):
            title = candidate["title"]
            source_url = "https://commons.wikimedia.org/wiki/" + urllib.parse.quote(
                title.replace(" ", "_"), safe=":()_-"
            )
            rejected = getattr(self, "rejected_sources", {})
            if normalized_source_key(source_url) in rejected.get(taxon["taxon_id"], set()):
                continue
            inputs.append({
                "taxon_id": taxon["taxon_id"],
                "scientific_name": scientific_name,
                "source_url": source_url,
                "creator": candidate["creator"],
                "licence_code": candidate["licence_code"],
                "licence_version": candidate["licence_version"],
                "selection_note": (
                    "Automatically selected exact-species landscape image; rejected specimen, "
                    "dead-animal and indirect-evidence terms."
                ),
            })
        return inputs

    def resolve_many(
        self,
        taxa: list[dict],
        retrieved_at: str,
        search_progress: Callable[[int, int, int, int], None] | None = None,
        validation_started: Callable[[int, int], None] | None = None,
        validation_progress: Callable[[int, int, int, int], None] | None = None,
    ) -> tuple[dict[int, dict], list[dict]]:
        """Search individually, then validate Commons metadata in provider-efficient batches."""
        failures: list[dict] = []
        queues: dict[int, list[dict]] = {}
        ordered_taxa = sorted(taxa, key=lambda row: row["taxon_id"])
        species_with_candidates = 0
        for index, taxon in enumerate(ordered_taxa, start=1):
            try:
                queues[taxon["taxon_id"]] = self._ranked_inputs(taxon)
                if queues[taxon["taxon_id"]]:
                    species_with_candidates += 1
            except Exception as error:
                failures.append({
                    "taxon_id": taxon["taxon_id"],
                    "source_url": f"https://commons.wikimedia.org/wiki/Special:MediaSearch?search={urllib.parse.quote(taxon['scientific_name'])}",
                    "error": str(error),
                })
            if search_progress:
                search_progress(index, len(ordered_taxa), species_with_candidates, len(failures))

        candidate_total = sum(len(queue) for queue in queues.values())
        if validation_started:
            validation_started(candidate_total, species_with_candidates)
        resolved: dict[int, dict] = {}
        candidates_decided = 0
        while any(queues.values()):
            round_inputs = [queues[taxon_id][0] for taxon_id in sorted(queues) if queues[taxon_id]]
            for index in range(0, len(round_inputs), 25):
                batch = round_inputs[index:index + 25]
                try:
                    assets, errors = self.curated.resolve(batch, retrieved_at)
                    failures.extend(errors)
                except Exception as error:
                    assets = []
                    failures.extend({
                        "taxon_id": item["taxon_id"],
                        "source_url": item["source_url"],
                        "error": str(error),
                    } for item in batch)
                for asset in assets:
                    taxon_id = asset["taxon_id"]
                    asset["asset_id"] = f"commons:{asset['provider_asset_id']}:{taxon_id}"
                    resolved[taxon_id] = asset
                for item in batch:
                    taxon_id = item["taxon_id"]
                    if taxon_id in resolved:
                        candidates_decided += len(queues[taxon_id])
                        queues[taxon_id].clear()
                    else:
                        queues[taxon_id].pop(0)
                        candidates_decided += 1
                if validation_progress:
                    validation_progress(
                        candidates_decided,
                        candidate_total,
                        len(resolved),
                        len(failures),
                    )
        if validation_progress:
            validation_progress(candidate_total, candidate_total, len(resolved), len(failures))
        return resolved, failures

    def resolve(self, taxon: dict, retrieved_at: str) -> tuple[dict | None, list[dict]]:
        assets, failures = self.resolve_many([taxon], retrieved_at)
        return assets.get(taxon["taxon_id"]), failures
