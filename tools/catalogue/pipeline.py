from __future__ import annotations

import hashlib
import json
import math
import shutil
import time
import urllib.parse
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from tools import generate_catalogues
from tools.content_refresh import (
    CachedHttpClient,
    INaturalistMediaResolver,
    INaturalistResolver,
    PhyloPicResolver,
    conservation_candidate,
    description_candidate,
)

from .commons import CommonsSearchResolver
from .config import PipelineConfig, PipelinePaths
from .io import atomic_copy, atomic_json, publish_directory, sha256_file
from tools.reference_media_rejections import normalized_source_key, rejection_map
from .progress import NullProgressReporter


SPECIES_COUNTS_API = "https://api.inaturalist.org/v1/observations/species_counts"
GROUP_CLASS = {
    "mammals": "Mammalia",
    "birds": "Aves",
    "reptiles": "Reptilia",
    "fish": "Actinopterygii",
    "amphibians": "Amphibia",
}


def utc_now() -> tuple[str, str]:
    now = datetime.now(timezone.utc).replace(microsecond=0)
    return now.strftime("%Y-%m-%dT%H:%M:%SZ"), now.date().isoformat()


def rarity_for(index: int, total: int, count: int, config: PipelineConfig) -> str:
    if count < int(config["minimum_rarity_observations"]):
        return "unknown"
    percentile = index / max(1, total)
    thresholds = config["rarity_percentiles"]
    if percentile < thresholds["common"]:
        return "common"
    if percentile < thresholds["uncommon"]:
        return "uncommon"
    if percentile < thresholds["rare"]:
        return "rare"
    return "very_rare"


def normalized_candidate_page(response: dict) -> dict:
    rows = []
    for result in response.get("results", []):
        if not isinstance(result, dict) or not isinstance(result.get("taxon"), dict):
            continue
        taxon = result["taxon"]
        if not isinstance(taxon.get("id"), int) or not isinstance(taxon.get("name"), str):
            continue
        rows.append({
            "count": int(result.get("count", 0)),
            "taxon": {
                key: taxon[key]
                for key in ("id", "name", "rank", "preferred_common_name")
                if key in taxon
            },
        })
    return {"total_results": int(response.get("total_results", len(rows))), "results": rows}


def select_usable_candidates(rows: list[dict], usable: set[int], locked: set[int]) -> set[int]:
    """Fill every group target from ranked usable evidence while preserving locked achievements."""
    selected = set(locked) & usable
    group_sources: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        group_sources[row["group"]].append(row)
    for group_rows in group_sources.values():
        target = int(group_rows[0]["target"]) if group_rows else 0
        eligible = [
            row["taxon_id"]
            for row in sorted(group_rows, key=lambda value: (value["rank"], value["taxon_id"]))
            if row["taxon_id"] in usable
        ]
        selected.update(eligible[:target])
    return selected


class CataloguePipeline:
    def __init__(
        self,
        root: Path,
        *,
        offline: bool = False,
        refresh: bool = False,
        progress=None,
    ):
        self.paths = PipelinePaths(root.resolve())
        self.config = PipelineConfig.load(self.paths.catalogues / "pipeline.json")
        self.client = CachedHttpClient(
            self.paths.cache,
            offline=offline,
            refresh=refresh,
            delay=float(self.config["network"]["request_delay_seconds"]),
        )
        self.offline = offline
        self.refresh = refresh
        self.progress = progress or NullProgressReporter()
        self.report: dict = {
            "schema_version": 1,
            "policy_version": self.config["policy_version"],
            "offline": offline,
            "refresh": refresh,
            "stages": [],
            "failures": [],
        }

    def read(self, relative: str) -> dict:
        value = json.loads((self.paths.root / relative).read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise ValueError(f"{relative} must contain an object")
        return value

    def _stage(self, name: str, operation, *, optional: bool = False):
        started_at = time.monotonic()
        self.progress.stage_started(name)
        try:
            result = operation()
            self.report["stages"].append({"name": name, "status": "complete"})
            self.progress.stage_finished(name, "complete", time.monotonic() - started_at)
            return result
        except Exception as error:
            failure = {"stage": name, "error": str(error)}
            self.report["failures"].append(failure)
            status = "degraded" if optional else "failed"
            self.report["stages"].append({"name": name, "status": status})
            self.progress.stage_finished(name, status, time.monotonic() - started_at)
            if not optional:
                raise
            return None

    def _provider_progress(self, label: str):
        def update(current: int, total: int, found: int = 0, failed: int = 0) -> None:
            statistics = self.client.statistics()
            self.progress.task_updated(
                label,
                current,
                total,
                found=found,
                failed=failed,
                **statistics,
            )
        return update

    def _candidate_page(self, region: str, group: dict, places: str, page: int) -> dict:
        params: dict[str, str | int] = {
            "place_id": places,
            "taxon_id": group["taxon_id"],
            "rank": "species",
            "verifiable": "true",
            "per_page": 200,
            "page": page,
            "order": "desc",
            "order_by": "observations_count",
        }
        url = f"{SPECIES_COUNTS_API}?{urllib.parse.urlencode(params)}"
        return self.client.json(
            "inaturalist-species-counts",
            f"{region}-{group['key']}-{page}",
            url,
            normalized_candidate_page,
        )

    def refresh_candidates(self, region_key: str | None = None) -> dict[str, list[dict]]:
        sources = self.read("catalogues/candidate_sources.yaml")["regions"]
        if region_key is not None:
            if region_key not in sources:
                raise ValueError(f"Region has no autonomous candidate source: {region_key}")
            sources = {region_key: sources[region_key]}
        exclusions_document = self.read("catalogues/candidate_exclusions.yaml")
        total_pages = sum(
            max(1, math.ceil(math.ceil(group["target"] * self.config["candidate_oversample_factor"]) / 200))
            for source in sources.values()
            for group in source["groups"]
        )
        completed_pages = 0
        self.progress.task_started("Evidence pages", total_pages)
        output: dict[str, list[dict]] = {}
        for region, source in sorted(sources.items()):
            place_ids = ",".join(str(item["id"]) for item in source["places"])
            excluded = {int(value) for value in exclusions_document.get(region, {})}
            region_rows: list[dict] = []
            for group in source["groups"]:
                requested = math.ceil(group["target"] * self.config["candidate_oversample_factor"])
                pages = max(1, math.ceil(requested / 200))
                merged: dict[int, dict] = {}
                for page in range(1, pages + 1):
                    for item in self._candidate_page(region, group, place_ids, page)["results"]:
                        taxon = item["taxon"]
                        taxon_id = taxon["id"]
                        current = merged.get(taxon_id)
                        row = {
                            "group": group["key"],
                            "target": group["target"],
                            "taxon_id": taxon_id,
                            "scientific_name": taxon["name"],
                            "common_name": taxon.get("preferred_common_name") or taxon["name"],
                            "observation_count": item["count"],
                        }
                        if current is None or row["observation_count"] > current["observation_count"]:
                            merged[taxon_id] = row
                    completed_pages += 1
                    statistics = self.client.statistics()
                    self.progress.task_updated(
                        "Evidence pages",
                        completed_pages,
                        total_pages,
                        **statistics,
                    )
                ranked = sorted(
                    (row for row in merged.values() if row["taxon_id"] not in excluded),
                    key=lambda row: (-row["observation_count"], row["scientific_name"].casefold(), row["taxon_id"]),
                )
                for position, row in enumerate(ranked, start=1):
                    row["rank"] = position
                    row["selected"] = position <= group["target"]
                    region_rows.append(row)
            output[region] = region_rows
            atomic_json(self.paths.review / f"{region}-candidates.json", {
                "schema_version": 1,
                "region": region,
                "policy_version": self.config["policy_version"],
                "candidates": region_rows,
            })
        return output

    def _achievement_ids(self) -> dict[str, set[int]]:
        result: dict[str, set[int]] = {}
        for row in self.read("catalogues/achievements.yaml").get("achievements", []):
            result[row["region"]] = set(row.get("essentials", []) + row.get("icons", []))
        return result

    def _resolve_taxonomy(self, taxon_ids: set[int]) -> tuple[dict[int, dict], dict[int, dict]]:
        resolver = INaturalistResolver(self.client)
        self.progress.task_started("Taxon records", len(taxon_ids))
        taxa, missing = resolver.resolve(
            sorted(taxon_ids),
            lambda current, total: self._provider_progress("Taxon records")(
                current, total, current, 0
            ),
        )
        if missing:
            statistics = self.client.statistics()
            self.progress.task_updated(
                "Taxon records", len(taxa), len(taxon_ids), found=len(taxa), failed=len(missing),
                force=True, **statistics,
            )
        if missing:
            self.report["failures"].append({
                "stage": "taxonomy",
                "error": f"No iNaturalist taxon evidence for {len(missing)} IDs",
                "taxon_ids": missing,
            })
        ancestor_ids = {
            ancestor
            for taxon in taxa.values()
            for ancestor in taxon.get("ancestor_ids", [])
            if isinstance(ancestor, int)
        }
        self.progress.task_started("Taxonomy ancestors", len(ancestor_ids))
        ancestors, ancestor_missing = resolver.resolve(
            sorted(ancestor_ids),
            lambda current, total: self._provider_progress("Taxonomy ancestors")(
                current, total, current, 0
            ),
        )
        if ancestor_missing:
            statistics = self.client.statistics()
            self.progress.task_updated(
                "Taxonomy ancestors", len(ancestors), len(ancestor_ids), found=len(ancestors),
                failed=len(ancestor_missing), force=True, **statistics,
            )
        if ancestor_missing:
            self.report["failures"].append({
                "stage": "taxonomy",
                "error": f"No iNaturalist ancestor evidence for {len(ancestor_missing)} IDs",
            })
        return taxa, ancestors

    @staticmethod
    def _taxonomy(taxon: dict, combined: dict[int, dict]) -> dict:
        result = {}
        for ancestor_id in taxon.get("ancestor_ids", []):
            ancestor = combined.get(ancestor_id)
            if not isinstance(ancestor, dict):
                continue
            rank, name = ancestor.get("rank"), ancestor.get("name")
            if rank in {"kingdom", "phylum", "class", "order", "family", "genus"} and isinstance(name, str):
                result[rank] = name
        return result

    def apply_selection(self, candidates: dict[str, list[dict]]) -> set[int]:
        achievements = self._achievement_ids()
        candidate_ids = {
            region: {row["taxon_id"] for row in rows} | achievements.get(region, set())
            for region, rows in candidates.items()
        }
        all_selected = set().union(*candidate_ids.values()) if candidate_ids else set()
        taxa_evidence, ancestor_evidence = self._resolve_taxonomy(all_selected)
        existing_document = self.read("catalogues/taxa.yaml")
        existing = {row["taxon_id"]: row for row in existing_document["taxa"]}
        combined = {**ancestor_evidence, **taxa_evidence}
        usable: set[int] = set()
        for taxon_id in sorted(all_selected):
            evidence = taxa_evidence.get(taxon_id)
            if evidence is None:
                if taxon_id in existing:
                    usable.add(taxon_id)
                continue
            if evidence.get("is_active") is False or evidence.get("rank") != "species":
                self.report["failures"].append({
                    "stage": "taxonomy",
                    "taxon_id": taxon_id,
                    "error": "Inactive or non-species candidate requires a future taxon-change migration",
                })
                if taxon_id in existing and any(taxon_id in values for values in achievements.values()):
                    usable.add(taxon_id)
                continue
            row = dict(existing.get(taxon_id, {}))
            row.update({
                "taxon_id": taxon_id,
                "rank": "species",
                "scientific_name": evidence["name"],
                "source_url": f"https://www.inaturalist.org/taxa/{taxon_id}",
                "source_revision": f"inaturalist-taxon:{taxon_id}:{evidence.get('updated_at', 'unknown')}",
                "taxonomy": self._taxonomy(evidence, combined),
            })
            row["accepted_taxon_id"] = taxon_id
            common_name = evidence.get("preferred_common_name")
            if isinstance(common_name, str) and common_name.strip():
                row["common_names"] = {**row.get("common_names", {}), "en": common_name.strip()}
            existing[taxon_id] = row
            usable.add(taxon_id)
        existing_document["taxa"] = [existing[key] for key in sorted(existing)]
        atomic_json(self.paths.catalogues / "taxa.yaml", existing_document)

        achievements_document = self.read("catalogues/achievements.yaml")
        achievement_rows = {row["region"]: row for row in achievements_document["achievements"]}
        for region, rows in candidates.items():
            selected = select_usable_candidates(
                rows,
                usable,
                set(achievements.get(region, set())),
            )
            by_group: dict[str, list[dict]] = defaultdict(list)
            for row in rows:
                if row["taxon_id"] in selected:
                    by_group[row["group"]].append(row)
            assignments: dict[int, str] = {}
            for group_rows in by_group.values():
                ranked = sorted(group_rows, key=lambda row: (-row["observation_count"], row["taxon_id"]))
                for index, row in enumerate(ranked):
                    assignments[row["taxon_id"]] = rarity_for(
                        index, len(ranked), row["observation_count"], self.config
                    )
            path = self.paths.catalogues / "regions" / region / "catalogue.yaml"
            prior = self.read(str(path.relative_to(self.paths.root)).replace("\\", "/"))
            prior_entries = {row["taxon_id"]: row for row in prior.get("taxa", [])}
            evidence_digest = hashlib.sha256(
                json.dumps(rows, sort_keys=True, separators=(",", ":")).encode()
            ).hexdigest()[:10]
            version = f"{region.replace('_', '-')}-auto-{evidence_digest}"
            entries = []
            ordered = sorted(
                selected,
                key=lambda taxon_id: (
                    next((row["group"] for row in rows if row["taxon_id"] == taxon_id), "zz"),
                    -next((row["observation_count"] for row in rows if row["taxon_id"] == taxon_id), 0),
                    taxon_id,
                ),
            )
            for taxon_id in ordered:
                previous = prior_entries.get(taxon_id, {})
                entries.append({
                    "taxon_id": taxon_id,
                    "encounter_rarity": assignments.get(taxon_id, "unknown"),
                    "seasonality": previous.get("seasonality", {}),
                    "inclusion_provenance": (
                        f"Selected by {self.config['policy_version']} from read-only iNaturalist "
                        "regional verifiable observation counts. Rarity is encounter frequency, "
                        "not conservation status."
                    ),
                })
            prior.update({
                "catalogue_version": version,
                "status": "draft",
                "content_rules_version": self.config["policy_version"],
                "rarity_model_version": self.config["policy_version"],
                "taxa": entries,
            })
            atomic_json(path, prior)
            achievement = achievement_rows.setdefault(region, {
                "region": region,
                "essentials": [],
                "icons": [],
            })
            achievement["catalogue_version"] = version
        achievements_document["achievements"] = [
            achievement_rows[key] for key in sorted(achievement_rows)
        ]
        atomic_json(self.paths.catalogues / "achievements.yaml", achievements_document)
        return usable

    def published_taxa(self, region_key: str | None = None) -> set[int]:
        result = set()
        for path in sorted((self.paths.catalogues / "regions").glob("*/catalogue.yaml")):
            document = json.loads(path.read_text(encoding="utf-8"))
            if region_key is None or document.get("region") == region_key:
                result.update(row["taxon_id"] for row in document["taxa"])
        return result

    def refresh_metadata(self, published: set[int]) -> dict[int, dict]:
        taxa_evidence, _ = self._resolve_taxonomy(published)
        _, retrieved_at = utc_now()
        descriptions = self.read("catalogues/taxon_descriptions.yaml")
        description_rows = {
            (row["taxon_id"], row["locale"]): row for row in descriptions.get("descriptions", [])
        }
        conservation = self.read("catalogues/taxon_conservation.yaml")
        conservation_rows = {row["taxon_id"]: row for row in conservation.get("conservation", [])}
        self.progress.task_started("Local metadata projection", len(taxa_evidence))
        for index, (taxon_id, evidence) in enumerate(sorted(taxa_evidence.items()), start=1):
            description = description_candidate(evidence, retrieved_at)
            if description is not None:
                description_rows[(taxon_id, "en")] = description
            assessment = conservation_candidate(evidence, retrieved_at)
            if assessment is not None:
                conservation_rows[taxon_id] = assessment
            self.progress.task_updated(
                "Local metadata projection", index, len(taxa_evidence),
            )
        descriptions["descriptions"] = [description_rows[key] for key in sorted(description_rows)]
        conservation["conservation"] = [conservation_rows[key] for key in sorted(conservation_rows)]
        atomic_json(self.paths.catalogues / "taxon_descriptions.yaml", descriptions)
        atomic_json(self.paths.catalogues / "taxon_conservation.yaml", conservation)
        return taxa_evidence

    def _acceptable_photo(self, asset: dict) -> bool:
        if asset.get("media_type") != "photo":
            return False
        detail = next((row for row in asset.get("variants", []) if row.get("variant") == "detail"), None)
        if not detail or not detail.get("height"):
            return False
        ratio = detail["width"] / detail["height"]
        policy = self.config["photo"]
        text = f"{asset.get('source_url', '')} {asset.get('assessment', '')}".casefold()
        tokens = set(__import__("re").findall(r"[a-z]+", text))
        return (
            float(policy["minimum_landscape_ratio"]) <= ratio <= float(policy["maximum_landscape_ratio"])
            and not tokens.intersection({str(term).casefold() for term in policy["reject_terms"]})
        )

    def refresh_media(self, published: set[int], evidence: dict[int, dict]) -> None:
        document = self.read("catalogues/media_manifest.yaml")
        assets = document.get("assets", [])
        rejected_sources = rejection_map(self.paths.catalogues / "media_rejections.yaml")
        taxa = {row["taxon_id"]: row for row in self.read("catalogues/taxa.yaml")["taxa"]}
        retrieved_at, reviewed_at = utc_now()
        photos = {
            row["taxon_id"]: row for row in assets
            if row.get("media_type") == "photo"
            and self._acceptable_photo(row)
            and normalized_source_key(row["source_url"])
                not in rejected_sources.get(row["taxon_id"], set())
        }
        def silhouette_role(row: dict) -> str:
            if row.get("match_rank") in {"species", "genus"}:
                return "specific"
            if row.get("match_rank") in {"family", "order"}:
                return "family"
            return "group"

        silhouettes = {
            (row["taxon_id"], silhouette_role(row)): row
            for row in assets if row.get("media_type") == "silhouette"
        }
        retained = [
            row for row in assets
            if row.get("taxon_id") not in published
            or (row.get("media_type") == "photo" and photos.get(row.get("taxon_id")) is row)
            or (
                row.get("media_type") == "silhouette"
                and silhouettes.get((row.get("taxon_id"), silhouette_role(row))) is row
            )
        ]
        commons = CommonsSearchResolver(
            self.client,
            self.config["photo"],
            rejected_sources=rejected_sources,
        )
        inaturalist = INaturalistMediaResolver(self.client)
        missing_photo_ids = sorted(published - set(photos))
        search_label = "Wikimedia species search"
        validation_label = "Wikimedia candidate validation"
        self.progress.task_started(search_label, len(missing_photo_ids))

        def validation_started(candidate_total: int, species_total: int) -> None:
            self.progress.note(
                f"Search complete: {candidate_total:,} eligible candidates across "
                f"{species_total:,} species"
            )
            self.progress.task_started(validation_label, candidate_total)

        commons_assets, failures = commons.resolve_many(
            [taxa[taxon_id] for taxon_id in missing_photo_ids],
            retrieved_at,
            self._provider_progress(search_label),
            validation_started,
            self._provider_progress(validation_label),
        )
        self.report["failures"].extend({"stage": "commons-photo", **row} for row in failures)
        photos.update(commons_assets)
        retained.extend(commons_assets.values())

        fallback_ids = [taxon_id for taxon_id in missing_photo_ids if taxon_id not in photos and taxon_id in evidence]
        self.progress.task_started("iNaturalist photo fallback", len(fallback_ids))
        fallback_assets, failures = inaturalist.resolve(
            [evidence[taxon_id] for taxon_id in fallback_ids],
            retrieved_at,
            self._provider_progress("iNaturalist photo fallback"),
            excluded_source_urls=rejected_sources,
        )
        self.report["failures"].extend({"stage": "inaturalist-photo", **row} for row in failures)
        for asset in fallback_assets:
            if self._acceptable_photo(asset):
                photos[asset["taxon_id"]] = asset
                retained.append(asset)

        resolver = PhyloPicResolver(self.client)
        specific_ids = {taxon_id for taxon_id, role in silhouettes if role == "specific"}
        family_ids = {taxon_id for taxon_id, role in silhouettes if role == "family"}
        missing_specific = [taxa[taxon_id] for taxon_id in sorted(published - specific_ids)]

        def phylopic_index_started(total_pages: int, total_images: int) -> None:
            self.progress.note(f"PhyloPic collection: {total_images:,} images")
            self.progress.task_started("PhyloPic provider index", total_pages)

        self.progress.task_started("Specific silhouettes", len(missing_specific))
        resolved, failures = resolver.resolve(
            missing_specific,
            retrieved_at,
            tuple(self.config["silhouette"]["specific_rank_order"]),
            self._provider_progress("Specific silhouettes"),
            phylopic_index_started,
            self._provider_progress("PhyloPic provider index"),
        )
        missing_family = [taxa[taxon_id] for taxon_id in sorted(published - family_ids)]
        self.progress.task_started("Family silhouettes", len(missing_family))
        family_resolved, family_failures = resolver.resolve(
            missing_family,
            retrieved_at,
            tuple(self.config["silhouette"]["family_rank_order"]),
            self._provider_progress("Family silhouettes"),
        )
        resolved.extend(family_resolved)
        failures.extend(family_failures)
        retained.extend(resolved)
        silhouettes.update({(row["taxon_id"], silhouette_role(row)): row for row in resolved})
        self.report["failures"].extend({"stage": "phylopic", **row} for row in failures)

        direct_photo_ids = set(photos)
        achievement_ids = set().union(*self._achievement_ids().values()) & published
        prior_waivers = {row["taxon_id"]: row for row in document.get("waivers", [])}
        for taxon_id in sorted(achievement_ids - direct_photo_ids):
            prior_waivers[taxon_id] = {
                "taxon_id": taxon_id,
                "reason": (
                    "The autonomous pipeline exhausted exact-species compatible Commons and "
                    "licensed iNaturalist landscape candidates; the app must use a silhouette."
                ),
                "reviewed_by": f"autonomous:{self.config['policy_version']}",
                "reviewed_at": reviewed_at,
            }
        for taxon_id in direct_photo_ids:
            prior_waivers.pop(taxon_id, None)
        document.update({
            "schema_version": 3,
            "assets": sorted(retained, key=lambda row: (row["taxon_id"], row["media_type"], row["asset_id"])),
            "waivers": [prior_waivers[key] for key in sorted(prior_waivers)],
        })
        atomic_json(self.paths.catalogues / "media_manifest.yaml", document)
        self.report["media"] = {
            "photos": len(photos),
            "silhouettes": len(silhouettes),
            "specific_silhouettes": sum(role == "specific" for _, role in silhouettes),
            "family_silhouettes": sum(role == "family" for _, role in silhouettes),
            "published": len(published),
            "group_fallbacks": len(published - {taxon_id for taxon_id, _ in silhouettes}),
            "rejected_photo_sources": sum(len(values) for values in rejected_sources.values()),
        }

    def _source_paths(self) -> list[Path]:
        paths = [
            path for path in self.paths.catalogues.iterdir()
            if path.is_file() and path.suffix in {".yaml", ".json"}
        ]
        paths.extend((self.paths.catalogues / "regions").glob("*/catalogue.yaml"))
        paths.extend((self.paths.catalogues / "regions").glob("*/overrides.yaml"))
        paths.extend((self.paths.root / "tools" / "catalogue").glob("*.py"))
        paths.extend((self.paths.root / "app" / "src" / "main" / "assets" / "taxon-glyphs").glob("*.svg"))
        paths.extend([
            self.paths.root / "tools" / "generate_catalogues.py",
            self.paths.root / "tools" / "catalogue_content.py",
            self.paths.root / "tools" / "content_refresh.py",
            self.paths.root / "tools" / "reference_media_rejections.py",
        ])
        return sorted({path.resolve() for path in paths if path.is_file()}, key=lambda path: path.as_posix())

    def _input_state(self) -> dict:
        files = {
            path.relative_to(self.paths.root).as_posix(): sha256_file(path)
            for path in self._source_paths()
        }
        fingerprint = hashlib.sha256(
            json.dumps(files, sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest()
        return {"schema_version": 1, "input_fingerprint": fingerprint, "files": files}

    def _ensure_revision(self) -> None:
        previous_path = self.paths.generated / "catalogue-inputs.json"
        previous = json.loads(previous_path.read_text(encoding="utf-8")) if previous_path.is_file() else {}
        current = self._input_state()
        if previous.get("input_fingerprint") == current["input_fingerprint"]:
            return
        manifest_path = self.paths.catalogues / "content_manifest.yaml"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        timestamp, _ = utc_now()
        sequence = int(manifest.get("generation_sequence", 0)) + 1
        manifest.update({
            "generation_sequence": sequence,
            "generation_id": f"catalogue-v{sequence}-{timestamp.replace(':', '').replace('-', '')}",
            "generated_at": timestamp,
            "source_revision": self.config["policy_version"],
        })
        atomic_json(manifest_path, manifest)

    def build(self, *, release: bool = False) -> dict:
        self._ensure_revision()
        content = generate_catalogues.validate(release)
        stage = self.paths.catalogues / ".generated-stage"
        if stage.exists():
            shutil.rmtree(stage)
        generate_catalogues.write_outputs(
            content,
            release,
            output_directory=stage,
            android_assets=None,
            previous_output_directory=self.paths.generated,
        )
        inputs = self._input_state()
        report_path = stage / "catalogue-report.json"
        report = json.loads(report_path.read_text(encoding="utf-8"))
        inputs["generation_id"] = report["generation_id"]
        inputs["generation_sequence"] = report["generation_sequence"]
        inputs["android_assets"] = {
            "catalogue.sqlite": sha256_file(stage / "catalogue.sqlite"),
            "catalogue-report.json": sha256_file(report_path),
        }
        atomic_json(stage / "catalogue-inputs.json", inputs)
        publish_directory(stage, self.paths.generated)
        atomic_copy(self.paths.generated / "catalogue.sqlite", self.paths.android_assets / "catalogue.sqlite")
        atomic_copy(self.paths.generated / "catalogue-report.json", self.paths.android_assets / "catalogue-report.json")
        return report

    def verify(self) -> dict:
        manifest_path = self.paths.generated / "catalogue-inputs.json"
        if not manifest_path.is_file():
            raise ValueError("Catalogue input manifest is missing; run `python -m tools.catalogue build`")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        errors = []
        glyph_directory = self.paths.root / "app" / "src" / "main" / "assets" / "taxon-glyphs"
        for group in GROUP_CLASS:
            if not (glyph_directory / f"{group}.svg").is_file():
                errors.append(f"missing offline group silhouette: {group}.svg")
        for relative, expected in manifest.get("files", {}).items():
            path = self.paths.root / relative
            if not path.is_file():
                errors.append(f"missing source: {relative}")
            elif sha256_file(path) != expected:
                errors.append(f"stale source: {relative}")
        for name, expected in manifest.get("android_assets", {}).items():
            path = self.paths.android_assets / name
            if not path.is_file() or sha256_file(path) != expected:
                errors.append(f"stale Android asset: {name}")
        if errors:
            raise ValueError("Catalogue build is stale:\n- " + "\n- ".join(errors))
        return {
            "generation_id": manifest["generation_id"],
            "generation_sequence": manifest["generation_sequence"],
            "source_files": len(manifest["files"]),
        }

    def run(self, *, release: bool = False, region_key: str | None = None) -> dict:
        try:
            if region_key is not None:
                configured = self.read("catalogues/candidate_sources.yaml").get("regions", {})
                if region_key not in configured:
                    raise ValueError(f"Region has no autonomous candidate source: {region_key}")
            candidates = self._stage(
                "regional-candidates",
                lambda: self.refresh_candidates(region_key),
                optional=True,
            )
            if candidates is not None:
                self._stage("selection-taxonomy-rarity", lambda: self.apply_selection(candidates))
            published = self.published_taxa(region_key)
            evidence = self._stage("metadata", lambda: self.refresh_metadata(published), optional=True) or {}
            self._stage("photos-silhouettes", lambda: self.refresh_media(published, evidence), optional=True)
            build_report = self._stage("validate-build-publish", lambda: self.build(release=release))
            verification = self._stage("freshness-verification", self.verify)
            self.report.update({
                "published_taxa": len(published),
                "region": region_key or "all",
                "build": build_report,
                "verification": verification,
            })
            return self.report
        finally:
            self.report["completed_at"] = utc_now()[0]
            self.report["provider_io"] = self.client.statistics()
            atomic_json(self.paths.review / "last-run.json", self.report)
