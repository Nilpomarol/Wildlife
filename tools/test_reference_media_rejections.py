from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.reference_media_rejections import (
    import_export,
    normalized_source_key,
    read_rejections,
    rejected_media_assignments,
)


class ReferenceMediaRejectionsTest(unittest.TestCase):
    def test_commons_source_normalization_handles_encoding_and_underscores(self) -> None:
        self.assertEqual(
            normalized_source_key("https://commons.wikimedia.org/wiki/File:Red_fox.jpg"),
            normalized_source_key("https://commons.wikimedia.org/wiki/File%3ARed%20fox.jpg/"),
        )

    def test_import_is_strict_deduplicated_and_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            export = root / "wildlife-local-data.json"
            destination = root / "media_rejections.yaml"
            export.write_text(json.dumps({
                "reference_media_rejections": [{
                    "taxon_id": 42,
                    "source_url": "https://commons.wikimedia.org/wiki/File:Bad_photo.jpg",
                    "catalogue_generation_id": "generation-7",
                    "reason": "user_requested_replacement",
                    "rejected_at_ms": 1234,
                }],
            }), encoding="utf-8")

            first = import_export(export, destination)
            second = import_export(export, destination)

            self.assertEqual(1, first["imported"])
            self.assertEqual(0, second["imported"])
            rows = read_rejections(destination)
            self.assertEqual(1, len(rows))
            self.assertEqual("wikimedia_commons", rows[0]["provider"])

    def test_rejected_assignments_are_reported_by_asset_id(self) -> None:
        source = "https://www.inaturalist.org/photos/99"
        rejected = {42: {normalized_source_key(source)}}
        self.assertEqual(
            ["inaturalist:99"],
            rejected_media_assignments([{
                "asset_id": "inaturalist:99",
                "taxon_id": 42,
                "media_type": "photo",
                "source_url": source,
            }], rejected),
        )

    def test_unstable_or_unknown_sources_are_rejected(self) -> None:
        for source in (
            "http://commons.wikimedia.org/wiki/File:Bad.jpg",
            "https://example.test/File:Bad.jpg",
            "https://commons.wikimedia.org/wiki/File:Bad.jpg?download=1",
        ):
            with self.assertRaises(ValueError):
                normalized_source_key(source)


if __name__ == "__main__":
    unittest.main()
