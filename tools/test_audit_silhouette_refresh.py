from __future__ import annotations

import gzip
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.audit_silhouette_refresh import classify_cached_role
from tools.content_refresh import PHYLOPIC_API, _api_url


def cache_json(root: Path, url: str, value: dict) -> None:
    key = hashlib.sha256(url.encode()).hexdigest()[:20]
    path = root / "raw" / "phylopic" / f"{key}.json.gz"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(json.dumps(value).encode("utf-8")))


class SilhouetteRefreshAuditTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.cache = Path(self.temporary.name)
        self.taxon = {
            "taxon_id": 1,
            "scientific_name": "Testudo example",
            "taxonomy": {"genus": "Testudo", "family": "Testudinidae", "order": "Testudines"},
        }

    def tearDown(self):
        self.temporary.cleanup()

    def cache_index(self, images: list[dict]) -> None:
        cache_json(self.cache, f"{PHYLOPIC_API}/images", {
            "build": 1,
            "totalItems": len(images),
            "totalPages": 1,
        })
        cache_json(self.cache, _api_url(f"{PHYLOPIC_API}/images", {
            "build": 1,
            "embed_items": "true",
            "page": 0,
        }), {"_embedded": {"items": images}})

    def test_missing_cache_is_not_mislabeled_as_provider_gap(self):
        result = classify_cached_role(self.taxon, "specific", self.cache)
        self.assertEqual("not_attempted_or_incomplete_cache", result["reason"])

    def test_complete_empty_responses_are_clean_provider_gap(self):
        self.cache_index([])
        result = classify_cached_role(self.taxon, "specific", self.cache)
        self.assertEqual("cached_clean_no_match", result["reason"])

    def test_later_rank_is_not_checked_after_incomplete_first_rank(self):
        result = classify_cached_role(self.taxon, "specific", self.cache)
        self.assertEqual("not_attempted_or_incomplete_cache", result["reason"])
        self.assertEqual("species", result["checked"][0]["rank"])


if __name__ == "__main__":
    unittest.main()
