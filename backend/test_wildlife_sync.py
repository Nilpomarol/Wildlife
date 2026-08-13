import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from wildlife_sync import (
    INaturalistAdapter,
    PhyloPicAdapter,
    WildlifeRepository,
    normalize_catalogue_species,
    normalize_observation,
    normalize_taxon_detail,
    phylopic_license_code,
    silhouette_candidates,
)


NOW = datetime(2026, 8, 12, 10, 0, tzinfo=timezone.utc)


def raw_observation(
    inat_id=1,
    inat_uuid="uuid-1",
    taxon_id=42,
    taxon_rank="species",
    rank_level=10,
    parent_id=41,
):
    return {
        "id": inat_id,
        "uuid": inat_uuid,
        "species_guess": "European robin",
        "time_observed_at": "2026-08-12T09:00:00Z",
        "created_at": "2026-08-12T09:10:00Z",
        "updated_at": "2026-08-12T09:12:00Z",
        "geojson": {"coordinates": [2.17, 41.38]},
        "quality_grade": "needs_id",
        "taxon": {
            "id": taxon_id,
            "name": "Erithacus rubecula",
            "rank": taxon_rank,
            "rank_level": rank_level,
            "parent_id": parent_id,
        },
    }


class FakeAdapter:
    pages = []
    requested_since = []
    catalogue = []
    catalogue_calls = 0
    taxa_batches = []
    reference_photos = {}

    def __init__(self, _connection):
        pass

    def observations(self, _user_id, updated_since=None):
        self.requested_since.append(updated_since)
        yield from self.pages.pop(0)

    def regional_species(self, _place_id, limit=800, locale="ca"):
        type(self).catalogue_calls += 1
        yield from self.catalogue[:limit]

    def taxa(self, taxon_ids, locale="en", preferred_place_id=12997):
        type(self).taxa_batches.append(list(taxon_ids))
        by_id = {
            int(item["taxon"]["id"]): {
                **item["taxon"],
                "ancestors": [{"id": 900, "rank": "family", "name": "Muscicapidae"}],
                "wikipedia_summary": "A small &amp; familiar bird.",
                "wikipedia_url": "https://en.wikipedia.org/wiki/European_robin",
            }
            for item in self.catalogue
        }
        return [by_id[value] for value in taxon_ids if value in by_id]

    def reference_photo(self, taxon_id, place_id=12997):
        return self.reference_photos.get(taxon_id)


class FakePhyloPic:
    def resolve(self, taxon_name, match_rank):
        return {
            "silhouette_url": f"https://example.test/{taxon_name}.png",
            "silhouette_source_url": "https://www.phylopic.org/images/example",
            "silhouette_attribution": "Example artist",
            "silhouette_license_code": "cc-by-3.0",
            "silhouette_license_url": "https://creativecommons.org/licenses/by/3.0/",
            "silhouette_image_uuid": "image-uuid",
            "silhouette_node_uuid": "node-uuid",
            "silhouette_taxon_name": taxon_name,
            "silhouette_match_rank": match_rank,
            "phylopic_build": 548,
        }


class FamilyOnlyPhyloPic(FakePhyloPic):
    requested = []

    def resolve(self, taxon_name, match_rank):
        type(self).requested.append((taxon_name, match_rank))
        return super().resolve(taxon_name, match_rank) if match_rank == "family" else None


class ReferencePhotoAdapter(INaturalistAdapter):
    def __init__(self, results):
        self.results = results

    def _get_json(self, _endpoint, _params):
        return {"results": self.results}


class FakeRegionalAdapter(INaturalistAdapter):
    def __init__(self):
        self.calls = []

    def _get_json(self, _endpoint, params):
        self.calls.append(dict(params))
        start = (params["page"] - 1) * params["per_page"] + 1
        results = [
            {"taxon": {"id": value, "name": f"Species {value}"}}
            for value in range(start, start + params["per_page"])
        ]
        return {"results": results}


class RecordingPhyloPic(PhyloPicAdapter):
    def __init__(self):
        super().__init__()
        self.urls = []

    def _get_json(self, url):
        self.urls.append(url)
        return {"build": 548, "totalItems": 0}


class WildlifeRepositoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repository = WildlifeRepository(Path(self.temp.name) / "test.db")
        FakeAdapter.pages = []
        FakeAdapter.requested_since = []
        FakeAdapter.catalogue = []
        FakeAdapter.catalogue_calls = 0
        FakeAdapter.taxa_batches = []
        FakeAdapter.reference_photos = {}
        FamilyOnlyPhyloPic.requested = []

    def tearDown(self):
        self.repository.close()
        self.temp.cleanup()

    def test_full_then_incremental_sync_uses_overlap(self):
        FakeAdapter.pages = [[raw_observation()], [raw_observation()]]
        first = self.repository.sync(7, "user", FakeAdapter, NOW)
        second = self.repository.sync(7, "user", FakeAdapter, NOW + timedelta(minutes=10))

        self.assertTrue(first["full_reconciliation"])
        self.assertFalse(second["full_reconciliation"])
        self.assertIsNone(FakeAdapter.requested_since[0])
        self.assertEqual(NOW - timedelta(minutes=5), FakeAdapter.requested_since[1])
        self.assertEqual(1, len(second["observations"]))

    def test_confirm_is_idempotent_and_first_species_is_awarded_once(self):
        FakeAdapter.pages = [[raw_observation()]]
        self.repository.sync(7, "user", FakeAdapter, NOW)

        first = self.repository.confirm(7, "uuid-1", NOW)
        second = self.repository.confirm(7, "uuid-1", NOW)

        self.assertEqual(510, first.xp_awarded)
        self.assertEqual(0, second.xp_awarded)
        self.assertEqual(510, second.total_xp)
        self.assertEqual(1, second.confirmed_count)

    def test_second_observation_of_species_only_gets_base_xp(self):
        FakeAdapter.pages = [[
            raw_observation(),
            raw_observation(2, "uuid-2", 43, "subspecies", 5, 42),
        ]]
        self.repository.sync(7, "user", FakeAdapter, NOW)
        self.repository.confirm(7, "uuid-1", NOW)

        result = self.repository.confirm(7, "uuid-2", NOW)

        self.assertEqual(10, result.xp_awarded)
        self.assertEqual(520, result.total_xp)

    def test_infraspecies_groups_to_parent_but_genus_remains_pending(self):
        subspecies = normalize_observation(
            raw_observation(2, "subspecies", 43, "subspecies", 5, 42)
        )
        genus = normalize_observation(raw_observation(3, "genus", 99, "genus", 20, 10))

        self.assertEqual(42, subspecies["collection_taxon_id"])
        self.assertEqual("species", subspecies["collection_taxon_rank"])
        self.assertEqual(99, genus["collection_taxon_id"])
        self.assertEqual("genus", genus["collection_taxon_rank"])

    def test_catalogue_is_versioned_and_reused_within_refresh_window(self):
        FakeAdapter.catalogue = [{
            "count": 123,
            "taxon": {
                "id": 42,
                "name": "Erithacus rubecula",
                "rank": "species",
                "iconic_taxon_name": "Aves",
                "preferred_common_name": "Pit-roig",
                "default_photo": {
                    "medium_url": "https://example.test/robin.jpg",
                    "attribution": "CC photographer",
                    "license_code": "cc-by",
                },
            },
        }]

        first = self.repository.sync_catalogue(
            adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic, now=NOW
        )
        second = self.repository.sync_catalogue(
            adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic,
            now=NOW + timedelta(days=1)
        )

        self.assertFalse(first["cached"])
        self.assertTrue(second["cached"])
        self.assertEqual("scope-v2-inat-12997-2026-08-12", first["version"])
        self.assertEqual("Pit-roig", first["species"][0]["common_name"])
        self.assertEqual("Aves", first["species"][0]["taxon_group"])
        self.assertEqual("Muscicapidae", first["species"][0]["family_name"])
        self.assertEqual("group", first["species"][0]["silhouette_match_rank"])
        self.assertEqual(1, FakeAdapter.catalogue_calls)

    def test_exact_silhouette_survives_catalogue_taxon_refresh(self):
        FakeAdapter.catalogue = [{
            "count": 1,
            "_wildlife_scope": "Aves",
            "taxon": {"id": 42, "name": "Erithacus rubecula", "rank": "species"},
        }]
        self.repository.sync_catalogue(
            adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic, now=NOW
        )
        exact = self.repository.sync_taxon_detail(
            42, adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic,
            now=NOW, force=True,
        )
        self.repository.sync_catalogue(
            adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic,
            now=NOW + timedelta(days=8), force=True,
        )

        refreshed = self.repository.taxon_detail_snapshot(42)
        self.assertEqual("species", exact["silhouette_match_rank"])
        self.assertEqual("species", refreshed["silhouette_match_rank"])

    def test_taxon_detail_falls_back_to_family_silhouette(self):
        FakeAdapter.catalogue = [{
            "count": 1,
            "_wildlife_scope": "Aves",
            "taxon": {"id": 42, "name": "Erithacus rubecula", "rank": "species"},
        }]

        detail = self.repository.sync_taxon_detail(
            42, adapter_factory=FakeAdapter, phylopic_factory=FamilyOnlyPhyloPic,
            now=NOW, force=True,
        )

        self.assertEqual("family", detail["silhouette_match_rank"])
        self.assertEqual("Muscicapidae", detail["silhouette_taxon_name"])
        self.assertEqual(
            [("Erithacus rubecula", "species"), ("Muscicapidae", "family")],
            FamilyOnlyPhyloPic.requested,
        )

    def test_taxon_detail_recovers_compatible_observation_photo(self):
        FakeAdapter.catalogue = [{
            "count": 1,
            "_wildlife_scope": "Aves",
            "taxon": {
                "id": 42,
                "name": "Erithacus rubecula",
                "rank": "species",
                "default_photo": {
                    "medium_url": "https://example.test/restricted.jpg",
                    "license_code": "cc-by-nc",
                    "attribution": "Restricted photographer",
                },
            },
        }]
        FakeAdapter.reference_photos = {42: {
            "photo_url": "https://example.test/open.jpg",
            "photo_attribution": "Open photographer",
            "photo_license_code": "cc-by",
            "photo_source_url": "https://www.inaturalist.org/photos/7",
            "photo_recovery_status": "recovered_observation",
        }}

        detail = self.repository.sync_taxon_detail(
            42, adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic,
            now=NOW, force=True,
        )

        self.assertEqual("https://example.test/open.jpg", detail["photo_url"])
        self.assertEqual("recovered_observation", detail["photo_recovery_status"])
        self.assertEqual("https://www.inaturalist.org/photos/7", detail["photo_source_url"])

        self.repository.sync_catalogue(
            adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic,
            now=NOW + timedelta(days=8), force=True,
        )
        refreshed = self.repository.taxon_detail_snapshot(42)
        self.assertEqual("https://example.test/open.jpg", refreshed["photo_url"])
        self.assertEqual("recovered_observation", refreshed["photo_recovery_status"])

    def test_catalogue_taxon_metadata_respects_inaturalist_batch_limit(self):
        FakeAdapter.catalogue = [
            {
                "count": 100 - taxon_id,
                "_wildlife_scope": "Aves",
                "taxon": {
                    "id": taxon_id,
                    "name": f"Species {taxon_id}",
                    "rank": "species",
                    "iconic_taxon_name": "Aves",
                },
            }
            for taxon_id in range(1, 32)
        ]

        result = self.repository.sync_catalogue(
            adapter_factory=FakeAdapter, phylopic_factory=FakePhyloPic, now=NOW
        )

        self.assertEqual(31, len(result["species"]))
        self.assertEqual([30, 1], [len(batch) for batch in FakeAdapter.taxa_batches])

    def test_regional_scope_keeps_a_stable_page_size(self):
        adapter = FakeRegionalAdapter()

        results = list(adapter._regional_species_scope(12997, "ca", 250, {"iconic_taxa": "Aves"}))

        self.assertEqual(250, len(results))
        self.assertEqual([200, 200], [call["per_page"] for call in adapter.calls])
        self.assertEqual(201, results[200]["taxon"]["id"])

    def test_phylopic_search_normalizes_taxon_name_to_lowercase(self):
        adapter = RecordingPhyloPic()

        self.assertIsNone(adapter._find_node("Erithacus rubecula"))

        self.assertIn("filter_name=erithacus+rubecula", adapter.urls[0])

    def test_silhouette_candidates_use_nearest_supported_taxonomic_ranks(self):
        candidates = silhouette_candidates({
            "name": "Erithacus rubecula",
            "rank": "species",
            "ancestors": [
                {"name": "Passeriformes", "rank": "order"},
                {"name": "Muscicapidae", "rank": "family"},
                {"name": "Erithacus", "rank": "genus"},
            ],
        })

        self.assertEqual([
            ("Erithacus rubecula", "species"),
            ("Erithacus", "genus"),
            ("Muscicapidae", "family"),
            ("Passeriformes", "order"),
        ], candidates)

    def test_reference_photo_skips_incompatible_licences(self):
        adapter = ReferencePhotoAdapter([{
            "uuid": "observation",
            "photos": [
                {"id": 1, "url": "https://example.test/closed-square.jpg", "license_code": "cc-by-nc"},
                {
                    "id": 2,
                    "url": "https://example.test/open-square.jpg",
                    "license_code": "cc-by",
                    "attribution": "Open photographer",
                },
            ],
        }])

        photo = adapter.reference_photo(42)

        self.assertEqual("https://example.test/open-medium.jpg", photo["photo_url"])
        self.assertEqual("https://www.inaturalist.org/photos/2", photo["photo_source_url"])

    def test_taxon_detail_uses_global_iucn_and_sanitizes_summary(self):
        detail = normalize_taxon_detail({
            "id": 42,
            "name": "Erithacus rubecula",
            "rank": "species",
            "iconic_taxon_name": "Aves",
            "wikipedia_summary": "<p>A small &amp; familiar <b>bird</b>.</p>",
            "conservation_statuses": [
                {"status": "nt", "authority": "Local list", "place": {"id": 1}},
                {"status": "lc", "authority": "IUCN Red List", "place": None, "iucn": 1},
            ],
        }, NOW)

        self.assertEqual("A small & familiar bird .", detail["wikipedia_summary"])
        self.assertEqual("lc", detail["conservation_status"])
        self.assertEqual("https://www.iucnredlist.org/species/1", detail["conservation_url"])

    def test_phylopic_license_policy_accepts_only_pdm_cc0_and_attribution(self):
        self.assertEqual("pdm", phylopic_license_code(
            "https://creativecommons.org/publicdomain/mark/1.0/"
        ))
        self.assertEqual("cc0", phylopic_license_code(
            "https://creativecommons.org/publicdomain/zero/1.0/"
        ))
        self.assertEqual("cc-by-3.0", phylopic_license_code(
            "https://creativecommons.org/licenses/by/3.0/"
        ))
        self.assertIsNone(phylopic_license_code(
            "https://creativecommons.org/licenses/by-sa/4.0/"
        ))

    def test_unlicensed_catalogue_photo_is_not_cached(self):
        item = normalize_catalogue_species({
            "count": 1,
            "taxon": {
                "id": 42,
                "name": "Erithacus rubecula",
                "rank": "species",
                "default_photo": {"medium_url": "https://example.test/photo.jpg"},
            },
        }, 1)

        self.assertIsNone(item["photo_url"])
        self.assertIsNone(item["photo_attribution"])


if __name__ == "__main__":
    unittest.main()
