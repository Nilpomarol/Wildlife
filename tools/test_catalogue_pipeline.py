from __future__ import annotations

import io
import tempfile
import unittest
from pathlib import Path

from tools.catalogue.commons import CommonsSearchResolver, score_commons_page
from tools.catalogue.config import PipelineConfig
from tools.catalogue.io import atomic_json, publish_directory
from tools.catalogue.pipeline import normalized_candidate_page, rarity_for, select_usable_candidates
from tools.catalogue.progress import ConsoleProgressReporter
from tools.reference_media_rejections import normalized_source_key


def config() -> PipelineConfig:
    return PipelineConfig({
        "minimum_rarity_observations": 5,
        "rarity_percentiles": {"common": 0.6, "uncommon": 0.85, "rare": 0.97},
    })


def commons_page(*, title: str = "File:Panthera leo alive.jpg", width: int = 1800, height: int = 1200):
    return {
        "pageid": 42,
        "title": title,
        "imageinfo": [{
            "width": width,
            "height": height,
            "extmetadata": {
                "Artist": {"value": "A. Photographer"},
                "LicenseShortName": {"value": "CC BY-SA 4.0"},
                "LicenseUrl": {"value": "https://creativecommons.org/licenses/by-sa/4.0/"},
                "ImageDescription": {"value": "A living <i>Panthera leo</i> in grassland"},
                "Categories": {"value": "Panthera leo"},
                "Assessments": {"value": "Quality image"},
            },
        }],
    }


class CataloguePolicyTest(unittest.TestCase):
    def test_rarity_keeps_insufficient_evidence_unknown(self):
        self.assertEqual("unknown", rarity_for(99, 100, 4, config()))
        self.assertEqual("common", rarity_for(0, 100, 100, config()))
        self.assertEqual("very_rare", rarity_for(99, 100, 100, config()))

    def test_candidate_normalization_drops_unneeded_provider_fields(self):
        normalized = normalized_candidate_page({
            "total_results": 1,
            "results": [{
                "count": 12,
                "taxon": {"id": 1, "name": "Panthera leo", "rank": "species", "junk": "drop"},
            }],
        })
        self.assertEqual(12, normalized["results"][0]["count"])
        self.assertNotIn("junk", normalized["results"][0]["taxon"])

    def test_unresolved_top_candidate_is_backfilled_without_losing_locked_taxa(self):
        rows = [
            {"group": "birds", "target": 2, "taxon_id": 1, "rank": 1},
            {"group": "birds", "target": 2, "taxon_id": 2, "rank": 2},
            {"group": "birds", "target": 2, "taxon_id": 3, "rank": 3},
        ]
        self.assertEqual({2, 3, 99}, select_usable_candidates(rows, {2, 3, 99}, {99}))

    def test_commons_policy_accepts_exact_living_landscape(self):
        result = score_commons_page(
            commons_page(),
            "Panthera leo",
            minimum_ratio=1.05,
            maximum_ratio=2.4,
            reject_terms={"skeleton", "specimen", "dead"},
        )
        self.assertIsNotNone(result)

    def test_commons_policy_rejects_portrait_and_bad_subject(self):
        common = dict(
            scientific_name="Panthera leo",
            minimum_ratio=1.05,
            maximum_ratio=2.4,
            reject_terms={"skeleton", "specimen", "dead"},
        )
        self.assertIsNone(score_commons_page(commons_page(width=800, height=1200), **common))
        self.assertIsNone(score_commons_page(commons_page(title="File:Panthera leo skeleton.jpg"), **common))

    def test_commons_metadata_is_resolved_in_batches(self):
        class Curated:
            def __init__(self):
                self.batch_sizes = []

            def resolve(self, items, retrieved_at):
                self.batch_sizes.append(len(items))
                return ([{
                    "taxon_id": item["taxon_id"],
                    "provider_asset_id": "42",
                    "asset_id": "temporary",
                } for item in items], [])

        resolver = CommonsSearchResolver.__new__(CommonsSearchResolver)
        resolver.policy = {
            "reject_terms": ["skeleton"],
            "minimum_landscape_ratio": 1.05,
            "maximum_landscape_ratio": 2.4,
        }
        resolver.curated = Curated()
        resolver._search = lambda _: [commons_page()]
        validation_start = []
        validation_updates = []
        assets, failures = resolver.resolve_many([
            {"taxon_id": taxon_id, "scientific_name": "Panthera leo"}
            for taxon_id in range(1, 27)
        ], "2026-08-25T00:00:00Z",
            validation_started=lambda candidates, species: validation_start.append((candidates, species)),
            validation_progress=lambda current, total, found, failed: validation_updates.append(
                (current, total, found, failed)
            ),
        )
        self.assertEqual([], failures)
        self.assertEqual([25, 1], resolver.curated.batch_sizes)
        self.assertEqual(26, len(assets))
        self.assertEqual("commons:42:26", assets[26]["asset_id"])
        self.assertEqual([(26, 26)], validation_start)
        self.assertEqual((26, 26, 26, 0), validation_updates[-1])

    def test_commons_validation_counts_skipped_lower_ranked_candidates_as_decided(self):
        class Curated:
            def resolve(self, items, retrieved_at):
                first = items[0]
                return ([{
                    "taxon_id": first["taxon_id"],
                    "provider_asset_id": "accepted",
                    "asset_id": "temporary",
                }], [])

        resolver = CommonsSearchResolver.__new__(CommonsSearchResolver)
        resolver.policy = {
            "reject_terms": ["skeleton"],
            "minimum_landscape_ratio": 1.05,
            "maximum_landscape_ratio": 2.4,
        }
        resolver.curated = Curated()
        resolver._ranked_inputs = lambda taxon: [
            {"taxon_id": taxon["taxon_id"], "source_url": f"https://example.test/{rank}"}
            for rank in range(3)
        ]
        updates = []

        assets, failures = resolver.resolve_many(
            [{"taxon_id": 1, "scientific_name": "Panthera leo"}],
            "2026-08-25T00:00:00Z",
            validation_progress=lambda *values: updates.append(values),
        )

        self.assertEqual([], failures)
        self.assertEqual({1}, set(assets))
        self.assertEqual((3, 3, 1, 0), updates[-1])

    def test_commons_policy_never_reselects_a_rejected_source(self):
        resolver = CommonsSearchResolver.__new__(CommonsSearchResolver)
        resolver.policy = {
            "reject_terms": ["skeleton"],
            "minimum_landscape_ratio": 1.05,
            "maximum_landscape_ratio": 2.4,
        }
        resolver._search = lambda _: [
            commons_page(title="File:Panthera leo first.jpg"),
            commons_page(title="File:Panthera leo second.jpg"),
        ]
        rejected_url = "https://commons.wikimedia.org/wiki/File:Panthera_leo_first.jpg"
        resolver.rejected_sources = {10: {normalized_source_key(rejected_url)}}

        inputs = resolver._ranked_inputs({"taxon_id": 10, "scientific_name": "Panthera leo"})

        self.assertEqual(1, len(inputs))
        self.assertIn("second.jpg", inputs[0]["source_url"])


class PublicationTest(unittest.TestCase):
    def test_directory_publication_replaces_complete_tree(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            destination, stage = root / "generated", root / "stage"
            destination.mkdir()
            (destination / "old").write_text("old", encoding="utf-8")
            stage.mkdir()
            atomic_json(stage / "new.json", {"ready": True})
            publish_directory(stage, destination)
            self.assertFalse((destination / "old").exists())
            self.assertTrue((destination / "new.json").is_file())
            self.assertFalse(stage.exists())


class ProgressReporterTest(unittest.TestCase):
    def test_console_progress_is_line_oriented_and_summarises_saved_details(self):
        output = io.StringIO()
        reporter = ConsoleProgressReporter(heartbeat_seconds=3600, stream=output)
        try:
            reporter.stage_started("photos-silhouettes")
            reporter.task_started("Specific silhouettes", 10)
            reporter.task_updated(
                "Specific silhouettes", 10, 10, found=7, failed=3,
                cache_hits=12, network_requests=4,
            )
            reporter.stage_finished("photos-silhouettes", "complete", 2.0)
            reporter.summary({
                "region": "mediterranean_europe",
                "media": {"photos": 8, "specific_silhouettes": 7, "family_silhouettes": 6},
                "provider_io": {"cache_hits": 12, "network_requests": 4},
                "failures": [{"error": "unavailable"}],
            }, Path("last-run.json"))
        finally:
            reporter.close()

        text = output.getvalue()
        self.assertIn("[4/6] Reference photos and silhouettes", text)
        self.assertIn("Specific silhouettes: 10 / 10", text)
        self.assertIn("found 7", text)
        self.assertIn("Detailed report: last-run.json", text)
        self.assertIn("Provider issues: 1", text)

if __name__ == "__main__":
    unittest.main()
