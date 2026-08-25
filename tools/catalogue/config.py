from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class PipelinePaths:
    root: Path

    @property
    def catalogues(self) -> Path:
        return self.root / "catalogues"

    @property
    def review(self) -> Path:
        return self.catalogues / "review" / "catalogue_pipeline"

    @property
    def cache(self) -> Path:
        return self.review / "cache"

    @property
    def generated(self) -> Path:
        return self.catalogues / "generated"

    @property
    def android_assets(self) -> Path:
        return self.root / "app" / "src" / "main" / "assets" / "catalogues"


@dataclass(frozen=True)
class PipelineConfig:
    raw: dict

    @classmethod
    def load(cls, path: Path) -> "PipelineConfig":
        raw = json.loads(path.read_text(encoding="utf-8"))
        if raw.get("schema_version") != 1:
            raise ValueError("catalogues/pipeline.json requires schema_version 1")
        factor = raw.get("candidate_oversample_factor")
        minimum = raw.get("minimum_rarity_observations")
        percentiles = raw.get("rarity_percentiles", {})
        if not isinstance(factor, (int, float)) or not 1 <= factor <= 3:
            raise ValueError("candidate_oversample_factor must be between 1 and 3")
        if not isinstance(minimum, int) or minimum < 1:
            raise ValueError("minimum_rarity_observations must be positive")
        thresholds = [percentiles.get(key) for key in ("common", "uncommon", "rare")]
        if not all(isinstance(value, (int, float)) for value in thresholds):
            raise ValueError("rarity_percentiles requires common, uncommon and rare")
        if not 0 < thresholds[0] < thresholds[1] < thresholds[2] < 1:
            raise ValueError("rarity percentiles must be strictly increasing below one")
        photo = raw.get("photo", {})
        if not isinstance(photo.get("reject_terms"), list) or not photo["reject_terms"]:
            raise ValueError("photo.reject_terms must be a non-empty list")
        silhouette = raw.get("silhouette", {})
        allowed_ranks = {"species", "genus", "family", "order"}
        for key in ("specific_rank_order", "family_rank_order"):
            order = silhouette.get(key)
            if (
                not isinstance(order, list)
                or not order
                or len(order) != len(set(order))
                or not set(order).issubset(allowed_ranks)
            ):
                raise ValueError(f"silhouette.{key} requires unique supported ranks")
        return cls(raw)

    def __getitem__(self, key: str):
        return self.raw[key]
