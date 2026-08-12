import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from wildlife_sync import WildlifeRepository, normalize_observation


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

    def __init__(self, _connection):
        pass

    def observations(self, _user_id, updated_since=None):
        self.requested_since.append(updated_since)
        yield from self.pages.pop(0)


class WildlifeRepositoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repository = WildlifeRepository(Path(self.temp.name) / "test.db")
        FakeAdapter.pages = []
        FakeAdapter.requested_since = []

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


if __name__ == "__main__":
    unittest.main()
