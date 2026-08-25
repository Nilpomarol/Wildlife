from __future__ import annotations

import sys
import tempfile
import unittest
import sqlite3
from pathlib import Path
from unittest.mock import patch


TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

from catalogue_content import (  # noqa: E402
    create_schema,
    logical_database_digest,
    validate_descriptions,
    validate_media,
    validate_taxa,
    validate_taxon_changes,
)
from generate_catalogues import read_regions, validate, validate_publication_revision  # noqa: E402


class PublishedContentValidationTest(unittest.TestCase):
    def test_taxon_without_common_name_is_preserved_for_runtime_scientific_name_fallback(self) -> None:
        rows = validate_taxa([{
            "taxon_id": 1,
            "rank": "species",
            "scientific_name": "Species without vernacular name",
            "taxonomy": {"family": "Exampleidae"},
            "source_url": "https://www.inaturalist.org/taxa/1",
        }])
        self.assertEqual({}, rows[1]["common_names"])

    def test_generated_database_uses_the_canonical_runtime_media_contract(self) -> None:
        database_path = TOOLS.parent / "app" / "src" / "main" / "assets" / "catalogues" / "catalogue.sqlite"
        with sqlite3.connect(database_path) as database:
            self.assertEqual(4, database.execute("PRAGMA user_version").fetchone()[0])
            photos = database.execute(
                "SELECT licence_code, creator, matched_taxon_name FROM media_asset WHERE media_type='photo'"
            ).fetchall()
            self.assertGreater(len(photos), 0)
            self.assertTrue(all(code in {"pdm", "cc0", "cc-by", "cc-by-sa"} for code, _, _ in photos))
            self.assertTrue(all(creator and creator.strip() for _, creator, _ in photos))
            self.assertTrue(all(name and name.strip() for _, _, name in photos))

    def test_repository_draft_sources_are_stable_and_report_remaining_coverage_gaps(self) -> None:
        first = validate(strict=False)
        second = validate(strict=False)

        self.assertEqual(first["source_digest"], second["source_digest"])
        self.assertEqual(2_419, len(first["published_taxon_ids"]))
        self.assertGreater(len(first["missing_descriptions"]), 0)
        self.assertLess(len(first["missing_descriptions"]), len(first["published_taxon_ids"]))
        self.assertGreater(len(first["missing_conservation"]), 0)
        self.assertLess(len(first["missing_conservation"]), len(first["published_taxon_ids"]))
        self.assertGreaterEqual(len(first["direct_media_taxa"]), 381)
        self.assertEqual(set(), first["achievement_taxa"] - first["direct_media_taxa"] - first["media_waivers"])

    def test_region_source_accepts_the_supported_25th_region(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "regions.yaml"
            path.write_text(
                "schema_version: 1\nregions:\n" + "".join(
                    f"  - key: region_{number}\n    order: {number}\n    names: {{ en: Region {number} }}\n"
                    for number in range(1, 26)
                ),
                encoding="utf-8",
            )
            self.assertEqual(25, len(read_regions(path)))

    def test_changed_publication_requires_new_sequence_and_generation_id(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            (output / "catalogue-report.json").write_text(
                '{"generation_id":"one","generation_sequence":4,"schema_version":3,"source_digest":"' + "a" * 64 + '","release_status":"draft"}',
                encoding="utf-8",
            )
            content = {"generation_id": "one", "generation_sequence": 4, "source_digest": "b" * 64}
            with patch("generate_catalogues.OUTPUT", output):
                with self.assertRaisesRegex(ValueError, "requires a new generation_sequence"):
                    validate_publication_revision(content, release=False)
                content.update(generation_sequence=5)
                with self.assertRaisesRegex(ValueError, "requires a new generation_id"):
                    validate_publication_revision(content, release=False)

    def test_media_v2_accepts_only_complete_stable_direct_variants(self) -> None:
        sha = "a" * 64
        document = {
            "schema_version": 2,
            "assets": [
                {
                    "asset_id": "commons:file:example",
                    "taxon_id": 10,
                    "media_type": "photo",
                    "provider": "wikimedia_commons",
                    "provider_asset_id": "File:Example.jpg@123",
                    "source_url": "https://commons.wikimedia.org/wiki/File:Example.jpg",
                    "creator": "Example Author",
                    "licence_code": "cc-by-sa",
                    "matched_taxon_name": "Example species",
                    "licence_url": "https://creativecommons.org/licenses/by-sa/4.0/",
                    "assessment": "quality",
                    "matched_taxon_id": 10,
                    "match_rank": "species",
                    "source_revision": "commons-revision-123",
                    "variants": [
                        {
                            "variant": "thumbnail",
                            "direct_url": "https://upload.wikimedia.org/example-256.jpg",
                            "mime_type": "image/jpeg",
                            "width": 256,
                            "height": 192,
                            "expected_bytes": 12_345,
                            "content_sha256": sha,
                        },
                        {
                            "variant": "detail",
                            "direct_url": "https://upload.wikimedia.org/example-1280.jpg",
                            "mime_type": "image/jpeg",
                            "width": 1_280,
                            "height": 960,
                            "expected_bytes": 234_567,
                            "content_sha256": sha,
                        },
                    ],
                }
            ],
        }

        assets, provenance_taxa, direct_taxa = validate_media(
            document, {10}, {10}, strict=True
        )

        self.assertEqual("commons:file:example", assets[0]["asset_id"])
        self.assertEqual({10}, provenance_taxa)
        self.assertEqual({10}, direct_taxa)

    def test_media_v2_rejects_query_urls_and_unapproved_hosts(self) -> None:
        base_variant = {
            "variant": "thumbnail",
            "direct_url": "https://upload.wikimedia.org/example.jpg?token=temporary",
            "mime_type": "image/jpeg",
            "width": 256,
            "height": 192,
        }
        document = {
            "schema_version": 2,
            "assets": [
                {
                    "asset_id": "commons:file:example",
                    "taxon_id": 10,
                    "media_type": "photo",
                    "provider": "wikimedia_commons",
                    "provider_asset_id": "File:Example.jpg@123",
                    "source_url": "https://commons.wikimedia.org/wiki/File:Example.jpg",
                    "creator": "Example Author",
                    "licence_code": "cc-by",
                    "matched_taxon_name": "Example species",
                    "licence_url": "https://creativecommons.org/licenses/by/4.0/",
                    "matched_taxon_id": 10,
                    "match_rank": "species",
                    "source_revision": "commons-revision-123",
                    "variants": [base_variant],
                }
            ],
        }

        with self.assertRaisesRegex(ValueError, "expiring/query URL"):
            validate_media(document, {10}, set(), strict=False)

        document["assets"][0]["variants"][0]["direct_url"] = (
            "https://untrusted.example/example.jpg"
        )
        with self.assertRaisesRegex(ValueError, "unapproved host"):
            validate_media(document, {10}, set(), strict=False)

    def test_media_accepts_one_specific_and_one_family_silhouette_role(self) -> None:
        def silhouette(asset_id: str, match_rank: str) -> dict:
            return {
                "asset_id": asset_id,
                "taxon_id": 10,
                "media_type": "silhouette",
                "provider": "phylopic",
                "provider_asset_id": asset_id,
                "source_url": f"https://www.phylopic.org/images/{asset_id}",
                "creator": None,
                "licence_code": "cc0",
                "licence_url": "https://creativecommons.org/publicdomain/zero/1.0/",
                "matched_taxon_name": "Example taxon",
                "matched_taxon_id": 10 if match_rank == "species" else None,
                "match_rank": match_rank,
                "source_revision": "build-7",
                "variants": [{
                    "variant": "detail",
                    "direct_url": f"https://images.phylopic.org/images/{asset_id}.png",
                    "mime_type": "image/png",
                    "width": 1024,
                    "height": 768,
                    "expected_bytes": 1234,
                    "content_sha256": "c" * 64,
                }],
            }

        assets, _, _ = validate_media(
            {"schema_version": 3, "assets": [
                silhouette("specific", "species"),
                silhouette("family", "family"),
            ], "waivers": []},
            {10},
            set(),
            strict=True,
        )
        self.assertEqual({"species", "family"}, {row["match_rank"] for row in assets})

    def test_media_rejects_two_assignments_for_the_same_silhouette_role(self) -> None:
        base = {
            "taxon_id": 10,
            "media_type": "silhouette",
            "provider": "phylopic",
            "source_url": "https://www.phylopic.org/images/example",
            "creator": None,
            "licence_code": "cc0",
            "licence_url": "https://creativecommons.org/publicdomain/zero/1.0/",
            "matched_taxon_name": "Example taxon",
            "matched_taxon_id": None,
            "source_revision": "build-7",
            "variants": [{
                "variant": "detail",
                "direct_url": "https://images.phylopic.org/images/example.png",
                "mime_type": "image/png",
                "width": 1024,
                "height": 768,
                "expected_bytes": 1234,
                "content_sha256": "c" * 64,
            }],
        }
        assets = [
            dict(base, asset_id="species", provider_asset_id="species", match_rank="species", matched_taxon_id=10),
            dict(base, asset_id="genus", provider_asset_id="genus", match_rank="genus"),
        ]
        with self.assertRaisesRegex(ValueError, "duplicate specific silhouette assignments"):
            validate_media(
                {"schema_version": 3, "assets": assets, "waivers": []},
                {10},
                set(),
                strict=True,
            )

    def test_taxon_change_cycles_are_rejected(self) -> None:
        document = {
            "schema_version": 1,
            "changes": [
                {
                    "previous_taxon_id": 1,
                    "accepted_taxon_id": 2,
                    "change_type": "swap",
                    "source_revision": "one",
                },
                {
                    "previous_taxon_id": 2,
                    "accepted_taxon_id": 1,
                    "change_type": "swap",
                    "source_revision": "two",
                },
            ],
        }

        with self.assertRaisesRegex(ValueError, "cycle"):
            validate_taxon_changes(document, {1, 2})

    def test_global_taxa_must_point_directly_to_a_canonical_identity(self) -> None:
        taxa = [
            {"taxon_id": 1, "rank": "species", "scientific_name": "One", "accepted_taxon_id": 2, "common_names": {"en": "One"}, "taxonomy": {}, "source_url": "https://www.inaturalist.org/taxa/1"},
            {"taxon_id": 2, "rank": "species", "scientific_name": "Two", "accepted_taxon_id": 3, "common_names": {"en": "Two"}, "taxonomy": {}, "source_url": "https://www.inaturalist.org/taxa/2"},
            {"taxon_id": 3, "rank": "species", "scientific_name": "Three", "accepted_taxon_id": 3, "common_names": {"en": "Three"}, "taxonomy": {}, "source_url": "https://www.inaturalist.org/taxa/3"},
        ]
        with self.assertRaisesRegex(ValueError, "directly to a canonical"):
            validate_taxa(taxa)

    def test_descriptions_require_compatible_text_attribution(self) -> None:
        document = {
            "schema_version": 1,
            "descriptions": [
                {
                    "taxon_id": 10,
                    "locale": "en",
                    "summary": "A sourced field-guide summary.",
                    "source_url": "https://en.wikipedia.org/wiki/Example",
                    "attribution": "Wikipedia contributors",
                    "licence_code": "cc-by-sa",
                    "licence_url": "https://creativecommons.org/licenses/by-sa/4.0/",
                    "retrieved_at": "2026-08-24",
                }
            ],
            "waivers": [],
        }

        coverage = validate_descriptions(document, {10})
        self.assertEqual(frozenset({10}), coverage.covered_taxon_ids)

        document["descriptions"][0]["licence_code"] = "All rights reserved"
        with self.assertRaisesRegex(ValueError, "incompatible licence"):
            validate_descriptions(document, {10})


class PublishedContentScaleTest(unittest.TestCase):
    TAXA = 30_000
    REGIONS = 25
    MEMBERSHIPS_PER_REGION = 2_000

    def _database(self, reverse: bool = False) -> sqlite3.Connection:
        database = sqlite3.connect(":memory:")
        create_schema(database)
        taxon_ids = range(self.TAXA, 0, -1) if reverse else range(1, self.TAXA + 1)
        database.executemany(
            "INSERT INTO taxon VALUES (?, ?, ?, ?, ?, ?, ?)",
            (
                (
                    taxon_id,
                    "species",
                    f"Synthetic taxon {taxon_id}",
                    taxon_id,
                    '{"class":"Aves"}',
                    f"https://www.inaturalist.org/taxa/{taxon_id}",
                    "synthetic-v1",
                )
                for taxon_id in taxon_ids
            ),
        )
        database.executemany(
            "INSERT INTO taxon_name VALUES (?, ?, ?, ?)",
            ((taxon_id, "en", f"Synthetic species {taxon_id}", 1) for taxon_id in taxon_ids),
        )
        region_ids = range(self.REGIONS, 0, -1) if reverse else range(1, self.REGIONS + 1)
        for region_number in region_ids:
            region_key = f"synthetic_{region_number:02d}"
            database.execute(
                "INSERT INTO region VALUES (?, ?, ?)",
                (region_key, region_number, '{"en":"Synthetic"}'),
            )
            database.execute(
                "INSERT INTO catalogue_version VALUES (?, ?, ?, ?)",
                (region_key, "v1", "frozen", "synthetic-v1"),
            )
            entries = range(self.MEMBERSHIPS_PER_REGION, 0, -1) if reverse else range(
                1, self.MEMBERSHIPS_PER_REGION + 1
            )
            database.executemany(
                "INSERT INTO regional_taxon VALUES (?, ?, ?, ?, ?, ?, ?)",
                (
                    (
                        region_key,
                        "v1",
                        ((region_number * 997 + position) % self.TAXA) + 1,
                        "common",
                        '{"en":"year-round"}',
                        position,
                        "synthetic scale fixture",
                    )
                    for position in entries
                ),
            )
        database.commit()
        return database

    def test_schema_handles_25_regions_and_overlapping_global_taxa(self) -> None:
        database = self._database()
        try:
            self.assertEqual(self.TAXA, database.execute("SELECT COUNT(*) FROM taxon").fetchone()[0])
            self.assertEqual(self.REGIONS, database.execute("SELECT COUNT(*) FROM region").fetchone()[0])
            self.assertEqual(
                self.REGIONS * self.MEMBERSHIPS_PER_REGION,
                database.execute("SELECT COUNT(*) FROM regional_taxon").fetchone()[0],
            )
            self.assertEqual([], database.execute("PRAGMA foreign_key_check").fetchall())
            plan = " ".join(
                str(row)
                for row in database.execute(
                    "EXPLAIN QUERY PLAN SELECT taxon_id FROM regional_taxon "
                    "WHERE region_key=? AND catalogue_version=? ORDER BY sort_order",
                    ("synthetic_25", "v1"),
                )
            )
            self.assertIn("regional_taxon_order", plan)
            with self.assertRaises(sqlite3.IntegrityError):
                database.execute(
                    "INSERT INTO catalogue_version VALUES (?, ?, ?, ?)",
                    ("synthetic_25", "v2", "frozen", "synthetic-v2"),
                )
        finally:
            database.close()

    def test_logical_digest_is_independent_of_insertion_order(self) -> None:
        forward = self._database(reverse=False)
        reverse = self._database(reverse=True)
        try:
            self.assertEqual(logical_database_digest(forward), logical_database_digest(reverse))
        finally:
            forward.close()
            reverse.close()


if __name__ == "__main__":
    unittest.main()
