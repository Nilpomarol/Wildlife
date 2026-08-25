from __future__ import annotations

import sys
import tempfile
import unittest
import urllib.parse
from io import BytesIO
from pathlib import Path

from PIL import Image


TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

from catalogue_content import validate_media  # noqa: E402
from apply_content_review import apply_decisions, source_policy_decisions  # noqa: E402
from refresh_catalogue_content import apply_taxonomy_evidence, media_resolution_inputs  # noqa: E402
from content_refresh import (  # noqa: E402
    CachedHttpClient,
    INaturalistResolver,
    INaturalistMediaResolver,
    PhyloPicResolver,
    RemoteRequestError,
    ResponseBytes,
    WikimediaResolver,
    commons_title,
    conservation_candidate,
    description_candidate,
    image_dimensions,
    inaturalist_direct_url,
    normalise_licence,
    stable_commons_direct_url,
    verified_variant,
)
from reference_media_rejections import normalized_source_key  # noqa: E402


def png(width: int = 3, height: int = 2) -> bytes:
    output = BytesIO()
    Image.new("RGB", (width, height), "green").save(output, format="PNG")
    return output.getvalue()


class ImageValidationTest(unittest.TestCase):
    def test_fully_decodes_supported_images_and_hashes_exact_bytes(self) -> None:
        payload = png()
        self.assertEqual(("image/png", 3, 2), image_dimensions(payload))
        result = verified_variant(
            "https://upload.wikimedia.org/example.png",
            lambda url: ResponseBytes(payload, url, "image/png"),
            "2026-08-24",
        )
        self.assertEqual(3, result["width"])
        self.assertEqual(len(payload), result["expected_bytes"])
        self.assertEqual(64, len(result["content_sha256"]))

    def test_rejects_corrupt_images_and_mime_mismatches(self) -> None:
        with self.assertRaisesRegex(ValueError, "decodable"):
            image_dimensions(b"\x89PNG\r\n\x1a\ncorrupt")
        with self.assertRaisesRegex(ValueError, "MIME"):
            verified_variant(
                "https://upload.wikimedia.org/example.png",
                lambda url: ResponseBytes(png(), url, "image/jpeg"),
                "2026-08-24",
            )

    def test_cached_client_refuses_unapproved_or_query_urls_before_network(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            client = CachedHttpClient(Path(directory), offline=True, refresh=False)
            with self.assertRaisesRegex(ValueError, "stable approved"):
                client.validate_image("https://upload.wikimedia.org/example.jpg?token=x", "2026-08-24")
            with self.assertRaisesRegex(ValueError, "stable approved"):
                client.validate_image("https://images.example/example.jpg", "2026-08-24")


class CandidateExtractionTest(unittest.TestCase):
    def test_inaturalist_cache_keeps_only_publishable_review_evidence(self) -> None:
        normalized = INaturalistResolver._normalise_response({
            "total_results": 1,
            "results": [{
                "id": 10,
                "name": "Example species",
                "wikipedia_summary": "Summary",
                "default_photo": {
                    "id": 99,
                    "license_code": "cc-by",
                    "url": "https://inaturalist-open-data.s3.amazonaws.com/example.jpg",
                    "irrelevant": "drop",
                },
                "listed_taxa": ["large unrelated payload"],
            }],
        })
        self.assertNotIn("listed_taxa", normalized["results"][0])
        self.assertNotIn("irrelevant", normalized["results"][0]["default_photo"])
        self.assertEqual(99, normalized["results"][0]["default_photo"]["id"])

    def test_extracts_review_only_description_and_conservation_candidates(self) -> None:
        taxon = {
            "id": 10,
            "updated_at": "2026-08-20T00:00:00Z",
            "wikipedia_summary": "A <b>short</b> summary (<i>Example</i> species) .",
            "wikipedia_url": "https://en.wikipedia.org/wiki/Example",
            "conservation_status": {"status": "EN", "authority": "IUCN Red List"},
        }
        description = description_candidate(taxon, "2026-08-24")
        conservation = conservation_candidate(taxon, "2026-08-24")
        self.assertEqual("A short summary (Example species).", description["summary"])
        self.assertEqual("cc-by-sa", description["licence_code"])
        self.assertEqual("EN", conservation["status"])
        self.assertEqual("https://www.inaturalist.org/taxa/10", conservation["source_url"])

    def test_commons_source_and_licence_normalisation_are_strict(self) -> None:
        self.assertEqual(
            "File:Example animal.jpg",
            commons_title("https://commons.wikimedia.org/wiki/File:Example_animal.jpg"),
        )
        self.assertEqual("cc-by-sa", normalise_licence("CC BY-SA 4.0"))
        self.assertEqual(
            "https://upload.wikimedia.org/example.jpg",
            stable_commons_direct_url(
                "https://upload.wikimedia.org/example.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo"
            ),
        )
        self.assertIsNone(normalise_licence("All Rights Reserved"))
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            commons_title("https://example.org/wiki/File:Example.jpg")

    def test_inaturalist_photo_urls_replace_only_the_documented_size(self) -> None:
        self.assertEqual(
            "https://static.inaturalist.org/photos/99/large.jpeg",
            inaturalist_direct_url(
                "https://static.inaturalist.org/photos/99/square.jpeg", "large"
            ),
        )
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            inaturalist_direct_url("https://images.example/photos/99/square.jpeg", "large")

    def test_applies_only_matching_active_taxonomy_evidence(self) -> None:
        document = {"schema_version": 1, "taxa": [{
            "taxon_id": 10,
            "rank": "species",
            "scientific_name": "Example species",
            "common_names": {"en": "Old name"},
            "taxonomy": {"class": "OldClass"},
            "source_url": "https://www.inaturalist.org/taxa/10",
        }]}
        evidence = {10: {
            "id": 10,
            "rank": "species",
            "name": "Example species",
            "preferred_common_name": "Example animal",
            "is_active": True,
            "ancestor_ids": [2, 3],
        }}
        result = apply_taxonomy_evidence(
            document,
            {10},
            evidence,
            {
                2: {"rank": "class", "name": "Aves"},
                3: {"rank": "family", "name": "Exampleidae"},
            },
        )
        self.assertEqual(
            {"class": "Aves", "family": "Exampleidae"},
            result["taxa"][0]["taxonomy"],
        )
        self.assertEqual("Example animal", result["taxa"][0]["common_names"]["en"])


class FakeINaturalistMediaClient:
    def validate_image(self, direct_url: str, retrieved_at: str) -> dict:
        width = 240 if "/small." in direct_url else 1000
        return {
            "direct_url": direct_url,
            "mime_type": "image/jpeg",
            "width": width,
            "height": width // 2,
            "expected_bytes": width * 10,
            "content_sha256": "b" * 64,
            "validated_at": retrieved_at,
        }


class INaturalistMediaResolutionTest(unittest.TestCase):
    def test_resolves_only_compatible_exact_taxon_defaults(self) -> None:
        taxa = [{
            "id": 10,
            "name": "Example species",
            "default_photo": {
                "id": 99,
                "license_code": "cc-by",
                "attribution": "(c) Example Creator, some rights reserved (CC BY)",
                "url": "https://static.inaturalist.org/photos/99/square.jpeg",
                "original_dimensions": {"width": 2000, "height": 1000},
            },
        }, {
            "id": 11,
            "default_photo": {
                "id": 100,
                "license_code": "cc-by-nc",
                "attribution": "Incompatible Creator",
                "url": "https://static.inaturalist.org/photos/100/square.jpeg",
            },
        }]
        assets, failures = INaturalistMediaResolver(FakeINaturalistMediaClient()).resolve(
            taxa, "2026-08-24"
        )
        self.assertEqual([], failures)
        self.assertEqual([10], [row["taxon_id"] for row in assets])
        self.assertEqual("inaturalist:99", assets[0]["asset_id"])
        validate_media(
            {"schema_version": 3, "assets": assets, "waivers": []},
            {10, 11},
            set(),
            strict=True,
        )

    def test_rejected_inaturalist_default_is_not_downloaded_or_selected(self) -> None:
        class NoDownloadClient:
            def validate_image(self, direct_url: str, retrieved_at: str) -> dict:
                raise AssertionError(f"Rejected image was downloaded: {direct_url}")

        source = "https://www.inaturalist.org/photos/99"
        assets, failures = INaturalistMediaResolver(NoDownloadClient()).resolve(
            [{
                "id": 10,
                "name": "Example species",
                "default_photo": {
                    "id": 99,
                    "license_code": "cc-by",
                    "attribution": "Example Creator (CC BY)",
                    "url": "https://static.inaturalist.org/photos/99/square.jpeg",
                },
            }],
            "2026-08-24",
            excluded_source_urls={10: {normalized_source_key(source)}},
        )

        self.assertEqual([], assets)
        self.assertEqual([], failures)


class FakePhyloPicClient:
    def __init__(self):
        self.image_validations = 0

    def json(self, namespace: str, key: str, url: str, normalizer=None) -> dict:
        del namespace, key, normalizer
        parsed = urllib.parse.urlparse(url)
        query = urllib.parse.parse_qs(parsed.query)
        if parsed.path == "/images" and "build" not in query:
            return {"build": 7, "totalItems": 1, "totalPages": 1}
        if parsed.path == "/images" and query.get("page") == ["0"]:
            return {"_embedded": {"items": [{
                "uuid": "family-bear",
                "_links": {
                    "self": {"title": "Ursidae"},
                    "specificNode": {"title": "Ursidae"},
                    "nodes": [{"title": "Ursidae"}],
                    "license": {"href": "https://creativecommons.org/publicdomain/zero/1.0/"},
                    "rasterFiles": [{"href": "https://images.phylopic.org/images/family-bear.png"}],
                },
            }]}}
        raise AssertionError(url)

    def validate_image(self, direct_url: str, retrieved_at: str) -> dict:
        self.image_validations += 1
        return {
            "direct_url": direct_url,
            "mime_type": "image/png",
            "width": 1024,
            "height": 768,
            "expected_bytes": 1234,
            "content_sha256": "c" * 64,
            "validated_at": retrieved_at,
        }


class PhyloPicResolutionTest(unittest.TestCase):
    def test_freezes_shared_family_match_as_distinct_taxon_assignments(self) -> None:
        taxa = [
            {"taxon_id": 10, "scientific_name": "Ursus arctos", "taxonomy": {"family": "Ursidae"}},
            {"taxon_id": 11, "scientific_name": "Ursus maritimus", "taxonomy": {"family": "Ursidae"}},
        ]

        client = FakePhyloPicClient()
        assets, failures = PhyloPicResolver(client).resolve(taxa, "2026-08-24")

        self.assertEqual([], failures)
        self.assertEqual(2, len(assets))
        self.assertEqual(
            {"phylopic:family-bear:10:family", "phylopic:family-bear:11:family"},
            {row["asset_id"] for row in assets},
        )
        self.assertEqual({"family"}, {row["match_rank"] for row in assets})
        self.assertEqual({"Ursidae"}, {row["matched_taxon_name"] for row in assets})
        self.assertEqual({None}, {row["matched_taxon_id"] for row in assets})
        self.assertEqual({None}, {row["creator"] for row in assets})
        self.assertEqual(1, client.image_validations)
        validate_media({"schema_version": 3, "assets": assets, "waivers": []}, {10, 11}, set(), strict=True)

    def test_family_first_policy_skips_unneeded_species_and_genus_queries(self) -> None:
        taxa = [
            {"taxon_id": 10, "scientific_name": "Ursus arctos", "taxonomy": {"family": "Ursidae"}},
        ]
        assets, failures = PhyloPicResolver(FakePhyloPicClient()).resolve(
            taxa,
            "2026-08-24",
            ("family", "order"),
        )
        self.assertEqual([], failures)
        self.assertEqual("family", assets[0]["match_rank"])

    def test_provider_outage_opens_circuit_after_builtin_retries(self) -> None:
        class FailingClient:
            def __init__(self):
                self.calls = 0

            def json(self, namespace, key, url, normalizer=None):
                self.calls += 1
                raise ValueError("Remote request failed after retries: https://api.phylopic.org")

        client = FailingClient()
        taxa = [
            {"taxon_id": 10, "scientific_name": "Ursus arctos", "taxonomy": {"family": "Ursidae"}},
            {"taxon_id": 11, "scientific_name": "Ursus maritimus", "taxonomy": {"family": "Ursidae"}},
        ]
        assets, failures = PhyloPicResolver(client).resolve(taxa, "2026-08-24", ("family",))
        self.assertEqual([], assets)
        self.assertEqual(1, client.calls)
        self.assertIn("circuit opened", failures[-1]["error"])

    def test_image_index_requests_explicit_page_and_embedded_items(self) -> None:
        class CollectionClient:
            def __init__(self):
                self.collection_urls = []

            def json(self, namespace, key, url, normalizer=None):
                del namespace, key, normalizer
                parsed = urllib.parse.urlparse(url)
                query = urllib.parse.parse_qs(parsed.query)
                if parsed.path == "/images" and "build" not in query:
                    return {"build": 7, "totalItems": 1, "totalPages": 1}
                if parsed.path == "/images" and "build" in query:
                    self.collection_urls.append(url)
                    self.assert_query(query)
                    return {"_embedded": {"items": [{
                        "uuid": "bird-image",
                        "_links": {
                            "self": {"title": "Pavo cristatus"},
                            "specificNode": {"title": "Pavo cristatus"},
                            "nodes": [{"title": "Pavo cristatus"}],
                            "license": {"href": "https://creativecommons.org/publicdomain/zero/1.0/"},
                            "rasterFiles": [{"href": "https://images.phylopic.org/images/bird.png"}],
                        },
                    }]}}
                raise AssertionError(url)

            @staticmethod
            def assert_query(query):
                if query.get("page") != ["0"] or query.get("embed_items") != ["true"]:
                    raise AssertionError(query)

            def validate_image(self, direct_url, retrieved_at):
                return {
                    "direct_url": direct_url,
                    "mime_type": "image/png",
                    "width": 800,
                    "height": 600,
                    "expected_bytes": 123,
                    "content_sha256": "d" * 64,
                    "validated_at": retrieved_at,
                }

        client = CollectionClient()
        assets, failures = PhyloPicResolver(client).resolve(
            [{"taxon_id": 1, "scientific_name": "Pavo cristatus", "taxonomy": {}}],
            "2026-08-25",
            ("species",),
        )

        self.assertEqual([], failures)
        self.assertEqual(1, len(assets))
        self.assertEqual(1, len(client.collection_urls))

    def test_permanent_provider_rejection_does_not_open_global_circuit(self) -> None:
        class RejectingClient:
            def __init__(self):
                self.calls = 0

            def json(self, namespace, key, url, normalizer=None):
                del namespace, key, normalizer
                self.calls += 1
                raise RemoteRequestError(url, ValueError("HTTP Error 400"), retryable=False)

        client = RejectingClient()
        taxa = [
            {"taxon_id": 10, "scientific_name": "First species", "taxonomy": {}},
            {"taxon_id": 11, "scientific_name": "Second species", "taxonomy": {}},
        ]

        assets, failures = PhyloPicResolver(client).resolve(taxa, "2026-08-25", ("species",))

        self.assertEqual([], assets)
        self.assertEqual(1, client.calls)
        self.assertEqual(2, len(failures))
        self.assertNotIn("circuit opened", " ".join(row["error"] for row in failures))


class FakeCommonsClient:
    def __init__(self, *, remote_licence: str = "CC BY-SA 4.0"):
        self.remote_licence = remote_licence

    def json(self, namespace: str, key: str, url: str) -> dict:
        query = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
        width = int(query["iiurlwidth"][0])
        include_metadata = "extmetadata" in query["iiprop"][0]
        info = {
            "url": "https://upload.wikimedia.org/original.jpg",
            "descriptionurl": "https://commons.wikimedia.org/wiki/File:Example.jpg",
            "thumburl": f"https://upload.wikimedia.org/{width}px-example.png",
            "thumbwidth": width,
            "thumbheight": width // 2,
            "width": 2400,
            "height": 1200,
            "mime": "image/png",
            "sha1": "abc123",
            "timestamp": "2026-08-20T00:00:00Z",
        }
        if include_metadata:
            info["extmetadata"] = {
                "Artist": {"value": "<b>Example Creator</b>"},
                "LicenseShortName": {"value": self.remote_licence},
                "LicenseUrl": {"value": "https://creativecommons.org/licenses/by-sa/4.0/"},
            }
        return {
            "query": {
                "pages": [{"pageid": 123, "title": "File:Example.jpg", "imageinfo": [info]}]
            }
        }

    def validate_image(self, direct_url: str, retrieved_at: str) -> dict:
        width = int(Path(urllib.parse.urlparse(direct_url).path).name.split("px-")[0])
        return {
            "direct_url": direct_url,
            "mime_type": "image/png",
            "width": width,
            "height": width // 2,
            "expected_bytes": width * 10,
            "content_sha256": "a" * 64,
            "validated_at": retrieved_at,
        }


class WikimediaResolutionTest(unittest.TestCase):
    LEGACY = [{
        "taxon_id": 10,
        "scientific_name": "Example species",
        "source_url": "https://commons.wikimedia.org/wiki/File:Example.jpg",
        "creator": "Example Creator",
        "licence_code": "cc-by-sa",
        "selection_note": "Reviewed exact species.",
    }]

    def test_resolves_reviewed_source_to_two_validated_direct_variants(self) -> None:
        assets, failures = WikimediaResolver(FakeCommonsClient()).resolve(self.LEGACY, "2026-08-24")
        self.assertEqual([], failures)
        self.assertEqual("commons:123", assets[0]["asset_id"])
        self.assertEqual({"thumbnail", "detail"}, {row["variant"] for row in assets[0]["variants"]})
        validate_media(
            {"schema_version": 3, "assets": assets, "waivers": []},
            {10},
            {10},
            strict=True,
        )

    def test_reports_licence_drift_without_publishing_asset(self) -> None:
        assets, failures = WikimediaResolver(
            FakeCommonsClient(remote_licence="CC BY-NC 4.0")
        ).resolve(self.LEGACY, "2026-08-24")
        self.assertEqual([], assets)
        self.assertRegex(failures[0]["error"], "licence changed")

    def test_schema_v2_assets_can_be_revalidated_from_their_source_pages(self) -> None:
        inputs = media_resolution_inputs({
            "schema_version": 2,
            "assets": [{
                "taxon_id": 10,
                "provider": "wikimedia_commons",
                "source_url": "https://commons.wikimedia.org/wiki/File:Example.jpg",
                "creator": "Example Creator",
                "licence_code": "cc-by-sa",
                "licence_url": "https://creativecommons.org/licenses/by-sa/4.0/",
                "assessment": "Reviewed exact species.",
            }],
            "waivers": [],
        })
        self.assertEqual("4.0", inputs[0]["licence_version"])
        assets, failures = WikimediaResolver(FakeCommonsClient()).resolve(inputs, "2026-08-24")
        self.assertEqual([], failures)
        self.assertEqual("commons:123", assets[0]["asset_id"])

    def test_media_waiver_requires_reviewer_date_and_reason(self) -> None:
        document = {
            "schema_version": 2,
            "assets": [],
            "waivers": [{
                "taxon_id": 10,
                "reason": "No compatible licensed reference image found.",
                "reviewed_by": "Content reviewer",
                "reviewed_at": "2026-08-24",
            }],
        }
        validate_media(document, {10}, {10}, strict=True)
        del document["waivers"][0]["reviewed_at"]
        with self.assertRaisesRegex(ValueError, "reviewed_at"):
            validate_media(document, {10}, {10}, strict=True)


class ReviewedPromotionTest(unittest.TestCase):
    def test_bulk_source_policy_approval_is_explicit_and_snapshot_bound(self) -> None:
        snapshot = {
            "snapshot_digest": "snapshot-one",
            "description_candidates": [{"taxon_id": 10}],
            "conservation_candidates": [{"taxon_id": 11}],
        }
        decisions = source_policy_decisions(snapshot, "Product owner", "2026-08-24")
        self.assertEqual("snapshot-one", decisions["candidate_snapshot_digest"])
        self.assertEqual(2, len(decisions["decisions"]))
        self.assertIn("not individually edited", decisions["decisions"][0]["reason"])

    def test_applies_only_snapshot_bound_reviewed_decisions(self) -> None:
        description = {
            "taxon_id": 10,
            "locale": "en",
            "summary": "Reviewed summary.",
            "source_url": "https://en.wikipedia.org/wiki/Example",
            "attribution": "Wikipedia contributors",
            "licence_code": "cc-by-sa",
            "licence_url": "https://creativecommons.org/licenses/by-sa/4.0/",
            "retrieved_at": "2026-08-24",
        }
        snapshot = {
            "snapshot_digest": "snapshot-one",
            "description_candidates": [description],
            "conservation_candidates": [],
            "media_candidates": [],
        }
        review = {
            "schema_version": 1,
            "candidate_snapshot_digest": "snapshot-one",
            "decisions": [
                {
                    "kind": "description",
                    "taxon_id": 10,
                    "decision": "approve",
                    "reviewed_by": "Reviewer",
                    "reviewed_at": "2026-08-24",
                    "reason": "Text checked against the source.",
                },
                {
                    "kind": "conservation",
                    "taxon_id": 10,
                    "decision": "waive",
                    "reviewed_by": "Reviewer",
                    "reviewed_at": "2026-08-24",
                    "reason": "No global status is present in the source snapshot.",
                },
            ],
        }
        descriptions, conservation, media, counts = apply_decisions(
            snapshot,
            review,
            {"schema_version": 1, "descriptions": [], "waivers": []},
            {"schema_version": 1, "conservation": [], "waivers": []},
            {"schema_version": 2, "assets": [], "waivers": []},
            {10},
            set(),
        )
        self.assertEqual([description], descriptions["descriptions"])
        self.assertEqual(10, conservation["waivers"][0]["taxon_id"])
        self.assertEqual([], media["assets"])
        self.assertEqual({"approved": 1, "waived": 1, "rejected": 0}, counts)

        review["candidate_snapshot_digest"] = "stale"
        with self.assertRaisesRegex(ValueError, "current candidate snapshot"):
            apply_decisions(
                snapshot,
                review,
                {"schema_version": 1, "descriptions": [], "waivers": []},
                {"schema_version": 1, "conservation": [], "waivers": []},
                {"schema_version": 2, "assets": [], "waivers": []},
                {10},
                set(),
            )


if __name__ == "__main__":
    unittest.main()
