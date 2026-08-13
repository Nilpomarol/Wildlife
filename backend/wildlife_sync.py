from __future__ import annotations

import json
import html
import re
import sqlite3
import threading
import time
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable


INATURALIST_OBSERVATIONS_URL = "https://api.inaturalist.org/v1/observations"
INATURALIST_SPECIES_COUNTS_URL = (
    "https://api.inaturalist.org/v1/observations/species_counts"
)
INATURALIST_TAXA_URL = "https://api.inaturalist.org/v1/taxa"
PHYLOPIC_API_URL = "https://api.phylopic.org"
PAGE_SIZE = 200
TAXA_BATCH_SIZE = 30
OVERLAP = timedelta(minutes=5)
FULL_RECONCILIATION_INTERVAL = timedelta(hours=24)
MIN_SYNC_INTERVAL_SECONDS = 30
CATALONIA_PLACE_ID = 12997
CATALONIA_REGION_KEY = "catalonia"
CATALOGUE_LIMIT = 580
CATALOGUE_REFRESH_INTERVAL = timedelta(days=7)
TAXON_REFRESH_INTERVAL = timedelta(days=30)
MEDIA_PIPELINE_VERSION = 2
BUTTERFLIES_TAXON_ID = 47224
ODONATA_TAXON_ID = 47792

# The provisional catalogue is intentionally biased toward taxa a general user
# can realistically observe and photograph. Fish require a reviewed taxon-ID
# allowlist because the public API has no reliable "large/recognizable" field.
CATALOGUE_SCOPES: tuple[tuple[str, int, dict[str, Any]], ...] = (
    ("Aves", 250, {"iconic_taxa": "Aves"}),
    ("Mammalia", 80, {"iconic_taxa": "Mammalia"}),
    ("Reptilia", 50, {"iconic_taxa": "Reptilia"}),
    ("Amphibia", 30, {"iconic_taxa": "Amphibia"}),
    ("Papilionoidea", 120, {"taxon_id": BUTTERFLIES_TAXON_ID}),
    ("Odonata", 50, {"taxon_id": ODONATA_TAXON_ID}),
)
CURATED_FISH_TAXON_IDS: tuple[int, ...] = ()
SCOPE_SEARCH_NAMES = {
    "Aves": ("Aves", "Erithacus rubecula"),
    "Mammalia": ("Mammalia",),
    "Reptilia": ("Reptilia",),
    "Amphibia": ("Amphibia", "Rana temporaria"),
    "Papilionoidea": ("Papilionoidea", "Papilio machaon"),
    "Odonata": ("Odonata",),
}

ALLOWED_CATALOGUE_GROUPS = frozenset(SCOPE_SEARCH_NAMES)
OPEN_PHOTO_LICENSES = frozenset({"cc0", "cc-by", "cc-by-sa"})
SILHOUETTE_FALLBACK_RANKS = ("species", "genus", "family", "order")
SILHOUETTE_COLUMNS = frozenset({
    "silhouette_url", "silhouette_source_url", "silhouette_attribution",
    "silhouette_license_code", "silhouette_license_url", "silhouette_image_uuid",
    "silhouette_node_uuid", "silhouette_taxon_name", "silhouette_match_rank",
    "phylopic_build",
})
PHOTO_MEDIA_COLUMNS = frozenset({
    "photo_url", "photo_attribution", "photo_license_code", "photo_source_url",
    "photo_recovery_status",
})


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError:
        return None


def phylopic_license_code(href: str | None) -> str | None:
    """Return only the PhyloPic licences accepted by Wildlife."""
    if not href:
        return None
    normalized = href.casefold().rstrip("/")
    if "/publicdomain/mark/" in normalized:
        return "pdm"
    if "/publicdomain/zero/" in normalized:
        return "cc0"
    match = re.search(r"/licenses/by/([0-9.]+)$", normalized)
    return f"cc-by-{match.group(1)}" if match else None


def normalized_photo_license(value: str | None) -> str | None:
    if not value:
        return None
    normalized = value.strip().casefold()
    return normalized if normalized in OPEN_PHOTO_LICENSES else None


def silhouette_candidates(raw: dict[str, Any]) -> list[tuple[str, str]]:
    """Return the closest useful taxonomic names, never jumping straight to class."""
    lineage = list(raw.get("ancestors") or []) + [raw]
    by_rank = {
        str(item.get("rank") or "").casefold(): str(item.get("name") or "").strip()
        for item in lineage
        if item.get("name")
    }
    result: list[tuple[str, str]] = []
    seen: set[str] = set()
    for rank in SILHOUETTE_FALLBACK_RANKS:
        name = by_rank.get(rank)
        if name and name.casefold() not in seen:
            result.append((name, rank))
            seen.add(name.casefold())
    return result


class UpstreamError(RuntimeError):
    pass


class RequestBudgetExceeded(UpstreamError):
    pass


class INaturalistAdapter:
    def __init__(self, connection: sqlite3.Connection, user_agent: str = "Wildlife/0.1"):
        self.connection = connection
        self.user_agent = user_agent

    def observations(self, user_id: int, updated_since: datetime | None = None):
        cursor: int | None = None
        while True:
            params: dict[str, Any] = {
                "user_id": user_id,
                "order_by": "id",
                "order": "asc",
                "per_page": PAGE_SIZE,
            }
            if cursor is not None:
                params["id_above"] = cursor
            if updated_since is not None:
                params["updated_since"] = iso(updated_since)
            page = self._get_json(INATURALIST_OBSERVATIONS_URL, params)
            results = page.get("results") or []
            for item in results:
                yield item
            if len(results) < PAGE_SIZE:
                break
            next_cursor = max(int(item["id"]) for item in results if item.get("id"))
            if cursor == next_cursor:
                raise UpstreamError("iNaturalist pagination cursor did not advance")
            cursor = next_cursor

    def regional_species(
        self,
        place_id: int,
        limit: int = CATALOGUE_LIMIT,
        locale: str = "ca",
    ):
        merged: dict[int, dict[str, Any]] = {}
        for scope_key, scope_limit, scope_params in CATALOGUE_SCOPES:
            for raw in self._regional_species_scope(
                place_id, locale, scope_limit, scope_params
            ):
                taxon_id = int((raw.get("taxon") or {}).get("id") or 0)
                if taxon_id <= 0:
                    continue
                item = dict(raw)
                item["_wildlife_scope"] = scope_key
                existing = merged.get(taxon_id)
                if existing is None or int(item.get("count") or 0) > int(existing.get("count") or 0):
                    merged[taxon_id] = item
        for raw in sorted(
            merged.values(), key=lambda item: int(item.get("count") or 0), reverse=True
        )[:limit]:
            yield raw

    def _regional_species_scope(
        self,
        place_id: int,
        locale: str,
        limit: int,
        scope_params: dict[str, Any],
    ):
        page = 1
        fetched = 0
        per_page = min(PAGE_SIZE, limit)
        while fetched < limit:
            payload = self._get_json(
                INATURALIST_SPECIES_COUNTS_URL,
                {
                    "place_id": place_id,
                    "quality_grade": "research",
                    "hrank": "species",
                    "lrank": "species",
                    "locale": locale,
                    "per_page": per_page,
                    "page": page,
                    **scope_params,
                },
            )
            results = payload.get("results") or []
            accepted = results[:limit - fetched]
            yield from accepted
            fetched += len(accepted)
            if len(results) < per_page:
                return
            page += 1

    def taxa(
        self,
        taxon_ids: list[int],
        locale: str = "en",
        preferred_place_id: int = CATALONIA_PLACE_ID,
    ) -> list[dict[str, Any]]:
        if not taxon_ids:
            return []
        payload = self._get_json(
            f"{INATURALIST_TAXA_URL}/" + ",".join(str(value) for value in taxon_ids),
            {"locale": locale, "preferred_place_id": preferred_place_id},
        )
        return list(payload.get("results") or [])

    def reference_photo(
        self,
        taxon_id: int,
        place_id: int = CATALONIA_PLACE_ID,
    ) -> dict[str, Any] | None:
        """Find a reusable research-grade observation photo when the default is unusable."""
        payload = self._get_json(
            INATURALIST_OBSERVATIONS_URL,
            {
                "taxon_id": taxon_id,
                "place_id": place_id,
                "quality_grade": "research",
                "photos": "true",
                "order_by": "votes",
                "order": "desc",
                "per_page": 30,
            },
        )
        for observation in payload.get("results") or []:
            for photo in observation.get("photos") or []:
                license_code = normalized_photo_license(photo.get("license_code"))
                raw_url = photo.get("url") or photo.get("medium_url")
                attribution = str(photo.get("attribution") or "").strip()
                if not license_code or not raw_url:
                    continue
                if license_code != "cc0" and not attribution:
                    continue
                photo_id = photo.get("id")
                return {
                    "photo_url": str(raw_url).replace("square", "medium"),
                    "photo_attribution": attribution or "CC0",
                    "photo_license_code": license_code,
                    "photo_source_url": (
                        f"https://www.inaturalist.org/photos/{photo_id}"
                        if photo_id else
                        f"https://www.inaturalist.org/observations/{observation.get('uuid')}"
                    ),
                    "photo_recovery_status": "recovered_observation",
                }
        return None

    def _get_json(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        self._reserve_request()
        url = endpoint + "?" + urllib.parse.urlencode(params)
        request = urllib.request.Request(url, headers={"User-Agent": self.user_agent})
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception as error:
            raise UpstreamError(f"iNaturalist request failed: {error}") from error

    def _reserve_request(self) -> None:
        now = int(time.time())
        with self.connection:
            self.connection.execute("DELETE FROM api_requests WHERE requested_at < ?", (now - 86400,))
            minute_count = self.connection.execute(
                "SELECT COUNT(*) FROM api_requests WHERE requested_at >= ?", (now - 60,)
            ).fetchone()[0]
            day_count = self.connection.execute("SELECT COUNT(*) FROM api_requests").fetchone()[0]
            if minute_count >= 50 or day_count >= 8000:
                raise RequestBudgetExceeded("Wildlife's shared iNaturalist request budget is exhausted")
            self.connection.execute("INSERT INTO api_requests(requested_at) VALUES (?)", (now,))


class PhyloPicAdapter:
    def __init__(self, user_agent: str = "Wildlife/0.1"):
        self.user_agent = user_agent
        self.build: int | None = None

    def resolve(self, taxon_name: str, match_rank: str) -> dict[str, Any] | None:
        node = self._find_node(taxon_name)
        primary = ((node or {}).get("_links") or {}).get("primaryImage")
        if not isinstance(primary, dict) or not primary.get("href"):
            return None
        image = self._get_json(self._absolute(primary["href"]))
        license_href = (((image.get("_links") or {}).get("license") or {}).get("href"))
        license_code = phylopic_license_code(license_href)
        if license_code is None:
            return None
        raster_files = (image.get("_links") or {}).get("rasterFiles") or []
        raster_url = next(
            (item.get("href") for item in reversed(raster_files) if item.get("href")),
            None,
        )
        image_uuid = image.get("uuid")
        if not raster_url or not image_uuid:
            return None
        return {
            "silhouette_url": self._absolute(raster_url),
            "silhouette_source_url": f"https://www.phylopic.org/images/{image_uuid}",
            "silhouette_attribution": image.get("attribution"),
            "silhouette_license_code": license_code,
            "silhouette_license_url": self._absolute(license_href),
            "silhouette_image_uuid": image_uuid,
            "silhouette_node_uuid": node.get("uuid"),
            "silhouette_taxon_name": ((node.get("_links") or {}).get("self") or {}).get("title")
                or taxon_name,
            "silhouette_match_rank": match_rank,
            "phylopic_build": image.get("build") or self.build,
        }

    def _find_node(self, taxon_name: str) -> dict[str, Any] | None:
        search_name = re.sub(r"[^a-z\s]", "", taxon_name.casefold())
        search_name = re.sub(r"\s+", " ", search_name).strip()
        if not search_name:
            return None
        meta = self._get_json(
            f"{PHYLOPIC_API_URL}/nodes?" + urllib.parse.urlencode({"filter_name": search_name})
        )
        self.build = int(meta.get("build") or 0) or self.build
        if not meta.get("totalItems") or self.build is None:
            return None
        page = self._get_json(
            f"{PHYLOPIC_API_URL}/nodes?" + urllib.parse.urlencode({
                "build": self.build,
                "embed_items": "true",
                "filter_name": search_name,
                "page": 0,
            })
        )
        items = ((page.get("_embedded") or {}).get("items") or [])
        exact = next(
            (
                item for item in items
                if str((((item.get("_links") or {}).get("self") or {}).get("title") or ""))
                .casefold() == search_name
            ),
            None,
        )
        return exact or (items[0] if items else None)

    def _get_json(self, url: str) -> dict[str, Any]:
        request = urllib.request.Request(
            url,
            headers={
                "User-Agent": self.user_agent,
                "Accept": "application/vnd.phylopic.v2+json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception as error:
            raise UpstreamError(f"PhyloPic request failed: {error}") from error

    @staticmethod
    def _absolute(href: str) -> str:
        return urllib.parse.urljoin(PHYLOPIC_API_URL, href)


@dataclass(frozen=True)
class ConfirmationResult:
    xp_awarded: int
    total_xp: int
    confirmed_count: int


@dataclass(frozen=True)
class CatalogueSyncResult:
    region_key: str
    cached: bool
    version: str | None
    updated_at: str | None
    species: list[dict[str, Any]]


class WildlifeRepository:
    def __init__(self, database_path: str | Path):
        self.connection = sqlite3.connect(str(database_path), check_same_thread=False)
        self.connection.row_factory = sqlite3.Row
        self.lock = threading.RLock()
        self._migrate()

    def close(self) -> None:
        self.connection.close()

    def _migrate(self) -> None:
        self.connection.executescript(
            """
            PRAGMA journal_mode=WAL;
            PRAGMA foreign_keys=ON;
            CREATE TABLE IF NOT EXISTS sync_state (
                user_id INTEGER PRIMARY KEY,
                login TEXT,
                last_synced_at TEXT,
                last_full_sync_at TEXT
            );
            CREATE TABLE IF NOT EXISTS observations (
                user_id INTEGER NOT NULL,
                inat_uuid TEXT NOT NULL,
                inat_id INTEGER NOT NULL,
                taxon_id INTEGER,
                label TEXT NOT NULL,
                observed_at TEXT,
                created_at TEXT,
                updated_at TEXT,
                latitude REAL,
                longitude REAL,
                obscured INTEGER NOT NULL DEFAULT 0,
                quality_grade TEXT NOT NULL,
                photo_url TEXT,
                seen_run TEXT,
                missing INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(user_id, inat_uuid),
                UNIQUE(user_id, inat_id)
            );
            CREATE TABLE IF NOT EXISTS confirmations (
                user_id INTEGER NOT NULL,
                inat_uuid TEXT NOT NULL,
                confirmed_at TEXT NOT NULL,
                PRIMARY KEY(user_id, inat_uuid)
            );
            CREATE TABLE IF NOT EXISTS xp_events (
                user_id INTEGER NOT NULL,
                event_key TEXT NOT NULL,
                inat_uuid TEXT NOT NULL,
                points INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                PRIMARY KEY(user_id, event_key)
            );
            CREATE TABLE IF NOT EXISTS api_requests (requested_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS catalogue_state (
                region_key TEXT PRIMARY KEY,
                place_id INTEGER NOT NULL,
                version TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                source_total INTEGER,
                provisional INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE IF NOT EXISTS regional_catalogue (
                region_key TEXT NOT NULL,
                taxon_id INTEGER NOT NULL,
                scientific_name TEXT NOT NULL,
                common_name TEXT,
                taxon_rank TEXT NOT NULL,
                taxon_group TEXT,
                observation_count INTEGER NOT NULL,
                position INTEGER NOT NULL,
                photo_url TEXT,
                photo_attribution TEXT,
                photo_license_code TEXT,
                PRIMARY KEY(region_key, taxon_id)
            );
            CREATE TABLE IF NOT EXISTS taxon_details (
                taxon_id INTEGER PRIMARY KEY,
                scientific_name TEXT NOT NULL,
                common_name TEXT,
                taxon_rank TEXT NOT NULL,
                taxon_group TEXT,
                family_name TEXT,
                wikipedia_summary TEXT,
                wikipedia_url TEXT,
                conservation_status TEXT,
                conservation_authority TEXT,
                conservation_url TEXT,
                photo_url TEXT,
                photo_attribution TEXT,
                photo_license_code TEXT,
                photo_source_url TEXT,
                photo_recovery_status TEXT,
                silhouette_url TEXT,
                silhouette_source_url TEXT,
                silhouette_attribution TEXT,
                silhouette_license_code TEXT,
                silhouette_license_url TEXT,
                silhouette_image_uuid TEXT,
                silhouette_node_uuid TEXT,
                silhouette_taxon_name TEXT,
                silhouette_match_rank TEXT,
                phylopic_build INTEGER,
                media_pipeline_version INTEGER,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS silhouette_assets (
                scope_key TEXT PRIMARY KEY,
                silhouette_url TEXT NOT NULL,
                silhouette_source_url TEXT NOT NULL,
                silhouette_attribution TEXT,
                silhouette_license_code TEXT NOT NULL,
                silhouette_license_url TEXT NOT NULL,
                silhouette_image_uuid TEXT NOT NULL,
                silhouette_node_uuid TEXT NOT NULL,
                silhouette_taxon_name TEXT NOT NULL,
                silhouette_match_rank TEXT NOT NULL,
                phylopic_build INTEGER,
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_api_requests_time ON api_requests(requested_at);
            CREATE INDEX IF NOT EXISTS idx_observations_user_updated ON observations(user_id, updated_at);
            CREATE INDEX IF NOT EXISTS idx_catalogue_region_position
                ON regional_catalogue(region_key, position);
            """
        )
        self._add_column("observations", "taxon_rank", "TEXT")
        self._add_column("observations", "collection_taxon_id", "INTEGER")
        self._add_column("observations", "collection_taxon_rank", "TEXT")
        self._add_column("regional_catalogue", "taxon_group", "TEXT")
        self._add_column("taxon_details", "photo_source_url", "TEXT")
        self._add_column("taxon_details", "photo_recovery_status", "TEXT")
        self._add_column("taxon_details", "media_pipeline_version", "INTEGER")
        with self.connection:
            self.connection.execute(
                """
                UPDATE taxon_details SET silhouette_match_rank = NULL
                WHERE silhouette_match_rank = 'unavailable' AND silhouette_url IS NULL
                """
            )

    def _add_column(self, table: str, column: str, definition: str) -> None:
        columns = {row["name"] for row in self.connection.execute(f"PRAGMA table_info({table})")}
        if column not in columns:
            self.connection.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")

    def sync(
        self,
        user_id: int,
        login: str,
        adapter_factory: Callable[[sqlite3.Connection], INaturalistAdapter] = INaturalistAdapter,
        now: datetime | None = None,
    ) -> dict[str, Any]:
        with self.lock:
            return self._sync_locked(user_id, login, adapter_factory, now)

    def _sync_locked(
        self,
        user_id: int,
        login: str,
        adapter_factory: Callable[[sqlite3.Connection], INaturalistAdapter],
        now: datetime | None,
    ) -> dict[str, Any]:
        now = now or utc_now()
        state = self.connection.execute(
            "SELECT * FROM sync_state WHERE user_id = ?", (user_id,)
        ).fetchone()
        needs_taxonomy_backfill = self.connection.execute(
            """
            SELECT 1 FROM observations
            WHERE user_id = ? AND taxon_id IS NOT NULL AND collection_taxon_id IS NULL LIMIT 1
            """,
            (user_id,),
        ).fetchone() is not None
        last_sync = parse_time(state["last_synced_at"]) if state else None
        if (
            not needs_taxonomy_backfill
            and last_sync
            and (now - last_sync).total_seconds() < MIN_SYNC_INTERVAL_SECONDS
        ):
            return self.snapshot(user_id, cached=True)

        last_full = parse_time(state["last_full_sync_at"]) if state else None
        full = (
            needs_taxonomy_backfill
            or last_full is None
            or now - last_full >= FULL_RECONCILIATION_INTERVAL
        )
        updated_since = None if full or last_sync is None else last_sync - OVERLAP
        run_id = str(uuid.uuid4())
        adapter = adapter_factory(self.connection)
        count = 0
        with self.connection:
            for raw in adapter.observations(user_id, updated_since):
                observation = normalize_observation(raw)
                if observation is None:
                    continue
                self._upsert_observation(user_id, observation, run_id)
                count += 1
            if full:
                self.connection.execute(
                    "UPDATE observations SET missing = 1 WHERE user_id = ? AND seen_run != ?",
                    (user_id, run_id),
                )
            self.connection.execute(
                """
                INSERT INTO sync_state(user_id, login, last_synced_at, last_full_sync_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    login=excluded.login,
                    last_synced_at=excluded.last_synced_at,
                    last_full_sync_at=CASE
                        WHEN excluded.last_full_sync_at IS NULL THEN sync_state.last_full_sync_at
                        ELSE excluded.last_full_sync_at
                    END
                """,
                (user_id, login, iso(now), iso(now) if full else None),
            )
        result = self.snapshot(user_id, cached=False)
        result["fetched"] = count
        result["full_reconciliation"] = full
        return result

    def _upsert_observation(self, user_id: int, value: dict[str, Any], run_id: str) -> None:
        self.connection.execute(
            """
            INSERT INTO observations(
                user_id, inat_uuid, inat_id, taxon_id, taxon_rank,
                collection_taxon_id, collection_taxon_rank, label, observed_at, created_at,
                updated_at, latitude, longitude, obscured, quality_grade, photo_url, seen_run, missing
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT(user_id, inat_uuid) DO UPDATE SET
                inat_id=excluded.inat_id, taxon_id=excluded.taxon_id,
                taxon_rank=excluded.taxon_rank,
                collection_taxon_id=excluded.collection_taxon_id,
                collection_taxon_rank=excluded.collection_taxon_rank,
                label=excluded.label,
                observed_at=excluded.observed_at, created_at=excluded.created_at,
                updated_at=excluded.updated_at, latitude=excluded.latitude,
                longitude=excluded.longitude, obscured=excluded.obscured,
                quality_grade=excluded.quality_grade, photo_url=excluded.photo_url,
                seen_run=excluded.seen_run, missing=0
            """,
            (
                user_id, value["uuid"], value["id"], value["taxon_id"], value["taxon_rank"],
                value["collection_taxon_id"], value["collection_taxon_rank"], value["label"],
                value["observed_at"], value["created_at"], value["updated_at"], value["latitude"],
                value["longitude"], int(value["obscured"]), value["quality_grade"],
                value["photo_url"], run_id,
            ),
        )

    def confirm(self, user_id: int, inat_uuid: str, now: datetime | None = None) -> ConfirmationResult:
        with self.lock:
            return self._confirm_locked(user_id, inat_uuid, now)

    def _confirm_locked(
        self, user_id: int, inat_uuid: str, now: datetime | None = None
    ) -> ConfirmationResult:
        now = now or utc_now()
        observation = self.connection.execute(
            """
            SELECT taxon_id, collection_taxon_id FROM observations
            WHERE user_id = ? AND inat_uuid = ? AND missing = 0
            """,
            (user_id, inat_uuid),
        ).fetchone()
        if observation is None:
            raise LookupError("The observation is not present in this verified user's cache")
        already_confirmed = self.connection.execute(
            "SELECT 1 FROM confirmations WHERE user_id = ? AND inat_uuid = ?", (user_id, inat_uuid)
        ).fetchone() is not None
        xp_awarded = 0
        with self.connection:
            self.connection.execute(
                "INSERT OR IGNORE INTO confirmations(user_id, inat_uuid, confirmed_at) VALUES (?, ?, ?)",
                (user_id, inat_uuid, iso(now)),
            )
            if not already_confirmed:
                xp_awarded += self._award(user_id, f"observation:{inat_uuid}", inat_uuid, 10, now)
                taxon_id = observation["collection_taxon_id"] or observation["taxon_id"]
                if taxon_id is not None:
                    prior_species = self.connection.execute(
                        """
                        SELECT 1 FROM confirmations c
                        JOIN observations o ON o.user_id = c.user_id AND o.inat_uuid = c.inat_uuid
                        WHERE c.user_id = ?
                          AND COALESCE(o.collection_taxon_id, o.taxon_id) = ?
                          AND c.inat_uuid != ? LIMIT 1
                        """,
                        (user_id, taxon_id, inat_uuid),
                    ).fetchone()
                    if prior_species is None:
                        xp_awarded += self._award(
                            user_id, f"first-species:{taxon_id}", inat_uuid, 500, now
                        )
        return ConfirmationResult(xp_awarded, self.total_xp(user_id), self.confirmed_count(user_id))

    def _award(
        self, user_id: int, event_key: str, inat_uuid: str, points: int, now: datetime
    ) -> int:
        cursor = self.connection.execute(
            "INSERT OR IGNORE INTO xp_events(user_id, event_key, inat_uuid, points, created_at) VALUES (?, ?, ?, ?, ?)",
            (user_id, event_key, inat_uuid, points, iso(now)),
        )
        return points if cursor.rowcount == 1 else 0

    def total_xp(self, user_id: int) -> int:
        return int(self.connection.execute(
            "SELECT COALESCE(SUM(points), 0) FROM xp_events WHERE user_id = ?", (user_id,)
        ).fetchone()[0])

    def confirmed_count(self, user_id: int) -> int:
        return int(self.connection.execute(
            "SELECT COUNT(*) FROM confirmations WHERE user_id = ?", (user_id,)
        ).fetchone()[0])

    def snapshot(self, user_id: int, cached: bool = True) -> dict[str, Any]:
        with self.lock:
            rows = self.connection.execute(
                """
                SELECT o.*, CASE WHEN c.inat_uuid IS NULL THEN 0 ELSE 1 END AS confirmed
                FROM observations o
                LEFT JOIN confirmations c ON c.user_id = o.user_id AND c.inat_uuid = o.inat_uuid
                WHERE o.user_id = ? AND o.missing = 0 ORDER BY o.inat_id ASC
                """,
                (user_id,),
            ).fetchall()
            state = self.connection.execute(
                "SELECT last_synced_at FROM sync_state WHERE user_id = ?", (user_id,)
            ).fetchone()
            return {
                "user_id": user_id,
                "cached": cached,
                "last_synced_at": state["last_synced_at"] if state else None,
                "confirmed_count": self.confirmed_count(user_id),
                "total_xp": self.total_xp(user_id),
                "observations": [dict(row) for row in rows],
            }

    def sync_catalogue(
        self,
        region_key: str = CATALONIA_REGION_KEY,
        place_id: int = CATALONIA_PLACE_ID,
        adapter_factory: Callable[[sqlite3.Connection], INaturalistAdapter] = INaturalistAdapter,
        phylopic_factory: Callable[[], PhyloPicAdapter] = PhyloPicAdapter,
        now: datetime | None = None,
        force: bool = False,
    ) -> dict[str, Any]:
        with self.lock:
            now = now or utc_now()
            state = self.connection.execute(
                "SELECT * FROM catalogue_state WHERE region_key = ?", (region_key,)
            ).fetchone()
            updated_at = parse_time(state["updated_at"]) if state else None
            has_taxon_groups = self.connection.execute(
                """
                SELECT 1 FROM regional_catalogue
                WHERE region_key = ? AND taxon_group IS NOT NULL
                LIMIT 1
                """,
                (region_key,),
            ).fetchone() is not None
            if (
                not force
                and updated_at is not None
                and now - updated_at < CATALOGUE_REFRESH_INTERVAL
                and has_taxon_groups
                and str(state["version"]).startswith("scope-v2-")
            ):
                self._ensure_scope_silhouettes(phylopic_factory(), now)
                return self.catalogue_snapshot(region_key, cached=True)

            adapter = adapter_factory(self.connection)
            normalized = []
            for position, raw in enumerate(adapter.regional_species(place_id), start=1):
                item = normalize_catalogue_species(raw, position)
                if item is not None:
                    normalized.append(item)
            taxon_details = []
            taxon_ids = [item["taxon_id"] for item in normalized]
            for start in range(0, len(taxon_ids), TAXA_BATCH_SIZE):
                for raw in adapter.taxa(taxon_ids[start:start + TAXA_BATCH_SIZE]):
                    detail = normalize_taxon_detail(raw, now)
                    if detail is not None:
                        taxon_details.append(detail)
            version = f"scope-v2-inat-{place_id}-{now.date().isoformat()}"
            with self.connection:
                self.connection.execute(
                    "DELETE FROM regional_catalogue WHERE region_key = ?", (region_key,)
                )
                self.connection.executemany(
                    """
                    INSERT INTO regional_catalogue(
                        region_key, taxon_id, scientific_name, common_name, taxon_rank,
                        taxon_group, observation_count, position, photo_url, photo_attribution,
                        photo_license_code
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    [
                        (
                            region_key, item["taxon_id"], item["scientific_name"],
                            item["common_name"], item["taxon_rank"],
                            item["taxon_group"],
                            item["observation_count"], item["position"], item["photo_url"],
                            item["photo_attribution"], item["photo_license_code"],
                        )
                        for item in normalized
                    ],
                )
                self.connection.execute(
                    """
                    INSERT INTO catalogue_state(
                        region_key, place_id, version, updated_at, source_total, provisional
                    ) VALUES (?, ?, ?, ?, ?, 1)
                    ON CONFLICT(region_key) DO UPDATE SET
                        place_id=excluded.place_id, version=excluded.version,
                        updated_at=excluded.updated_at, source_total=excluded.source_total,
                        provisional=1
                    """,
                    (region_key, place_id, version, iso(now), len(normalized)),
                )
                self._upsert_taxon_details(taxon_details)
            self._ensure_scope_silhouettes(phylopic_factory(), now)
            result = self.catalogue_snapshot(region_key, cached=False)
            result["fetched"] = len(normalized)
            return result

    def catalogue_snapshot(
        self,
        region_key: str = CATALONIA_REGION_KEY,
        cached: bool = True,
    ) -> dict[str, Any]:
        with self.lock:
            state = self.connection.execute(
                "SELECT * FROM catalogue_state WHERE region_key = ?", (region_key,)
            ).fetchone()
            rows = self.connection.execute(
                """
                SELECT rc.*,
                    td.family_name, td.wikipedia_summary, td.wikipedia_url,
                    td.conservation_status, td.conservation_authority,
                    td.conservation_url,
                    COALESCE(td.silhouette_url, sa.silhouette_url) AS silhouette_url,
                    COALESCE(td.silhouette_source_url, sa.silhouette_source_url)
                        AS silhouette_source_url,
                    COALESCE(td.silhouette_attribution, sa.silhouette_attribution)
                        AS silhouette_attribution,
                    COALESCE(td.silhouette_license_code, sa.silhouette_license_code)
                        AS silhouette_license_code,
                    COALESCE(td.silhouette_license_url, sa.silhouette_license_url)
                        AS silhouette_license_url,
                    COALESCE(td.silhouette_image_uuid, sa.silhouette_image_uuid)
                        AS silhouette_image_uuid,
                    COALESCE(td.silhouette_node_uuid, sa.silhouette_node_uuid)
                        AS silhouette_node_uuid,
                    COALESCE(td.silhouette_taxon_name, sa.silhouette_taxon_name)
                        AS silhouette_taxon_name,
                    COALESCE(td.silhouette_match_rank, sa.silhouette_match_rank)
                        AS silhouette_match_rank,
                    COALESCE(td.phylopic_build, sa.phylopic_build) AS phylopic_build
                FROM regional_catalogue rc
                LEFT JOIN taxon_details td ON td.taxon_id = rc.taxon_id
                LEFT JOIN silhouette_assets sa ON sa.scope_key = rc.taxon_group
                WHERE rc.region_key = ? ORDER BY rc.position ASC
                """,
                (region_key,),
            ).fetchall()
            return {
                "region_key": region_key,
                "place_id": state["place_id"] if state else None,
                "version": state["version"] if state else None,
                "updated_at": state["updated_at"] if state else None,
                "provisional": bool(state["provisional"]) if state else True,
                "cached": cached,
                "species": [dict(row) for row in rows],
            }

    def sync_taxon_detail(
        self,
        taxon_id: int,
        adapter_factory: Callable[[sqlite3.Connection], INaturalistAdapter] = INaturalistAdapter,
        phylopic_factory: Callable[[], PhyloPicAdapter] = PhyloPicAdapter,
        now: datetime | None = None,
        force: bool = False,
    ) -> dict[str, Any]:
        with self.lock:
            now = now or utc_now()
            existing = self.connection.execute(
                "SELECT updated_at, media_pipeline_version FROM taxon_details WHERE taxon_id = ?",
                (taxon_id,),
            ).fetchone()
            updated_at = parse_time(existing["updated_at"]) if existing else None
            media_current = bool(
                existing and existing["media_pipeline_version"] == MEDIA_PIPELINE_VERSION
            )
            if (
                not force and updated_at and now - updated_at < TAXON_REFRESH_INTERVAL
                and media_current
            ):
                cached = self.taxon_detail_snapshot(taxon_id)
                if cached is not None:
                    cached["cached"] = True
                    return cached
            adapter = adapter_factory(self.connection)
            results = adapter.taxa([taxon_id])
            if not results:
                raise LookupError(f"Unknown iNaturalist taxon {taxon_id}")
            raw_taxon = results[0]
            detail = normalize_taxon_detail(raw_taxon, now)
            if detail is None:
                raise LookupError(f"Incomplete iNaturalist taxon {taxon_id}")
            if normalized_photo_license(detail.get("photo_license_code")) is None:
                try:
                    recovered_photo = adapter.reference_photo(taxon_id)
                except (AttributeError, UpstreamError):
                    recovered_photo = None
                    detail["photo_recovery_status"] = "recovery_failed"
                if recovered_photo is not None:
                    detail.update(recovered_photo)
                elif detail["photo_recovery_status"] != "recovery_failed":
                    detail["photo_recovery_status"] = "no_compatible_photo"

            silhouette = None
            phylopic = phylopic_factory()
            for taxon_name, match_rank in silhouette_candidates(raw_taxon):
                try:
                    silhouette = phylopic.resolve(taxon_name, match_rank)
                except UpstreamError:
                    continue
                if silhouette is not None:
                    break
            if silhouette is not None:
                detail.update(silhouette)
            detail["media_pipeline_version"] = MEDIA_PIPELINE_VERSION
            with self.connection:
                self._upsert_taxon_details([detail])
            result = self.taxon_detail_snapshot(taxon_id)
            if result is None:
                raise LookupError(f"Taxon {taxon_id} was not cached")
            result["cached"] = False
            return result

    def taxon_detail_snapshot(self, taxon_id: int) -> dict[str, Any] | None:
        with self.lock:
            row = self.connection.execute(
                """
                SELECT td.*,
                    COALESCE(td.silhouette_url, sa.silhouette_url) AS resolved_silhouette_url,
                    COALESCE(td.silhouette_source_url, sa.silhouette_source_url)
                        AS resolved_silhouette_source_url,
                    COALESCE(td.silhouette_attribution, sa.silhouette_attribution)
                        AS resolved_silhouette_attribution,
                    COALESCE(td.silhouette_license_code, sa.silhouette_license_code)
                        AS resolved_silhouette_license_code,
                    COALESCE(td.silhouette_license_url, sa.silhouette_license_url)
                        AS resolved_silhouette_license_url,
                    COALESCE(td.silhouette_image_uuid, sa.silhouette_image_uuid)
                        AS resolved_silhouette_image_uuid,
                    COALESCE(td.silhouette_node_uuid, sa.silhouette_node_uuid)
                        AS resolved_silhouette_node_uuid,
                    COALESCE(td.silhouette_taxon_name, sa.silhouette_taxon_name)
                        AS resolved_silhouette_taxon_name,
                    COALESCE(td.silhouette_match_rank, sa.silhouette_match_rank)
                        AS resolved_silhouette_match_rank,
                    COALESCE(td.phylopic_build, sa.phylopic_build) AS resolved_phylopic_build
                FROM taxon_details td
                LEFT JOIN silhouette_assets sa ON sa.scope_key = td.taxon_group
                WHERE td.taxon_id = ?
                """,
                (taxon_id,),
            ).fetchone()
            if row is None:
                return None
            result = dict(row)
            for name in (
                "url", "source_url", "attribution", "license_code", "license_url",
                "image_uuid", "node_uuid", "taxon_name", "match_rank",
            ):
                result[f"silhouette_{name}"] = result.pop(f"resolved_silhouette_{name}")
            result["phylopic_build"] = result.pop("resolved_phylopic_build")
            return result

    def _upsert_taxon_details(self, details: list[dict[str, Any]]) -> None:
        if not details:
            return
        columns = (
            "taxon_id", "scientific_name", "common_name", "taxon_rank", "taxon_group",
            "family_name", "wikipedia_summary", "wikipedia_url", "conservation_status",
            "conservation_authority", "conservation_url", "photo_url", "photo_attribution",
            "photo_license_code", "photo_source_url", "photo_recovery_status",
            "silhouette_url", "silhouette_source_url",
            "silhouette_attribution", "silhouette_license_code", "silhouette_license_url",
            "silhouette_image_uuid", "silhouette_node_uuid", "silhouette_taxon_name",
            "silhouette_match_rank", "phylopic_build", "media_pipeline_version", "updated_at",
        )
        assignments = ",".join(
            (
                f"{column}=COALESCE(excluded.{column},taxon_details.{column})"
                if column in SILHOUETTE_COLUMNS or column == "media_pipeline_version"
                else (
                    f"{column}=CASE WHEN excluded.media_pipeline_version IS NULL "
                    f"THEN COALESCE(taxon_details.{column},excluded.{column}) "
                    f"ELSE excluded.{column} END"
                    if column in PHOTO_MEDIA_COLUMNS
                    else f"{column}=excluded.{column}"
                )
            )
            for column in columns if column != "taxon_id"
        )
        self.connection.executemany(
            f"""
            INSERT INTO taxon_details({','.join(columns)})
            VALUES ({','.join('?' for _ in columns)})
            ON CONFLICT(taxon_id) DO UPDATE SET {assignments}
            """,
            [tuple(detail.get(column) for column in columns) for detail in details],
        )

    def _ensure_scope_silhouettes(self, adapter: PhyloPicAdapter, now: datetime) -> None:
        for scope_key, search_names in SCOPE_SEARCH_NAMES.items():
            if self.connection.execute(
                "SELECT 1 FROM silhouette_assets WHERE scope_key = ?", (scope_key,)
            ).fetchone():
                continue
            silhouette = None
            for search_name in search_names:
                try:
                    silhouette = adapter.resolve(search_name, "group")
                except UpstreamError:
                    continue
                if silhouette is not None:
                    break
            if silhouette is None:
                continue
            columns = ("scope_key",) + tuple(silhouette.keys()) + ("updated_at",)
            values = (scope_key,) + tuple(silhouette.values()) + (iso(now),)
            with self.connection:
                self.connection.execute(
                    f"INSERT OR REPLACE INTO silhouette_assets({','.join(columns)}) "
                    f"VALUES ({','.join('?' for _ in columns)})",
                    values,
                )


def normalize_observation(raw: dict[str, Any]) -> dict[str, Any] | None:
    inat_id = raw.get("id")
    inat_uuid = raw.get("uuid")
    if not inat_id or not inat_uuid:
        return None
    taxon = raw.get("taxon") or {}
    taxon_rank = taxon.get("rank")
    rank_level = taxon.get("rank_level")
    taxon_id = taxon.get("id")
    collection_taxon_id = taxon_id
    collection_taxon_rank = taxon_rank
    is_infraspecies = taxon_rank in {"subspecies", "variety", "form"}
    if taxon_id is not None and (
        is_infraspecies or (rank_level is not None and float(rank_level) < 10)
    ):
        collection_taxon_id = taxon.get("parent_id") or taxon_id
        collection_taxon_rank = "species"
    label = (
        raw.get("species_guess")
        or taxon.get("preferred_common_name")
        or taxon.get("name")
        or "Unidentified"
    )
    coordinates = ((raw.get("geojson") or {}).get("coordinates") or [None, None])
    photos = raw.get("photos") or []
    photo_url = photos[0].get("url") if photos else None
    if photo_url:
        photo_url = photo_url.replace("square", "medium")
    return {
        "id": int(inat_id),
        "uuid": str(inat_uuid),
        "taxon_id": taxon_id,
        "taxon_rank": taxon_rank,
        "collection_taxon_id": collection_taxon_id,
        "collection_taxon_rank": collection_taxon_rank,
        "label": str(label),
        "observed_at": raw.get("time_observed_at") or raw.get("observed_on"),
        "created_at": raw.get("created_at"),
        "updated_at": raw.get("updated_at"),
        "longitude": coordinates[0] if len(coordinates) > 0 else None,
        "latitude": coordinates[1] if len(coordinates) > 1 else None,
        "obscured": bool(raw.get("obscured") or raw.get("geoprivacy") == "obscured"),
        "quality_grade": raw.get("quality_grade") or "unknown",
        "photo_url": photo_url,
    }


def normalize_catalogue_species(raw: dict[str, Any], position: int) -> dict[str, Any] | None:
    taxon = raw.get("taxon") or {}
    taxon_id = taxon.get("id")
    scientific_name = taxon.get("name")
    if not taxon_id or not scientific_name:
        return None
    photo = taxon.get("default_photo") or {}
    license_code = photo.get("license_code")
    photo_url = photo.get("medium_url") if license_code else None
    attribution = photo.get("attribution") if license_code else None
    return {
        "taxon_id": int(taxon_id),
        "scientific_name": str(scientific_name),
        "common_name": taxon.get("preferred_common_name"),
        "taxon_rank": taxon.get("rank") or "species",
        "taxon_group": raw.get("_wildlife_scope") or taxon.get("iconic_taxon_name"),
        "observation_count": int(raw.get("count") or 0),
        "position": position,
        "photo_url": photo_url,
        "photo_attribution": attribution,
        "photo_license_code": license_code,
    }


def normalize_taxon_detail(
    raw: dict[str, Any], now: datetime | None = None
) -> dict[str, Any] | None:
    taxon_id = raw.get("id")
    scientific_name = raw.get("name")
    if not taxon_id or not scientific_name:
        return None
    ancestors = list(raw.get("ancestors") or [])
    lineage = ancestors + [raw]
    family = next((item for item in lineage if item.get("rank") == "family"), None)
    lineage_ids = {int(item["id"]) for item in lineage if item.get("id")}
    iconic_group = raw.get("iconic_taxon_name")
    if BUTTERFLIES_TAXON_ID in lineage_ids:
        taxon_group = "Papilionoidea"
    elif ODONATA_TAXON_ID in lineage_ids:
        taxon_group = "Odonata"
    elif iconic_group in ALLOWED_CATALOGUE_GROUPS:
        taxon_group = iconic_group
    elif int(taxon_id) in CURATED_FISH_TAXON_IDS:
        taxon_group = "Actinopterygii"
    else:
        taxon_group = iconic_group

    summary = html.unescape(re.sub(r"<[^>]+>", " ", raw.get("wikipedia_summary") or ""))
    summary = re.sub(r"\s+", " ", summary).strip() or None
    statuses = raw.get("conservation_statuses") or []
    global_iucn = next(
        (
            item for item in statuses
            if item.get("authority") == "IUCN Red List" and item.get("place") is None
        ),
        None,
    )
    photo = raw.get("default_photo") or {}
    photo_license = photo.get("license_code")
    compatible_photo_license = normalized_photo_license(photo_license)
    photo_attribution = str(photo.get("attribution") or "").strip()
    photo_url = photo.get("medium_url") if (
        compatible_photo_license and
        (compatible_photo_license == "cc0" or photo_attribution)
    ) else None
    if photo_url:
        photo_recovery_status = "default_licensed"
    elif not photo:
        photo_recovery_status = "no_default_photo"
    elif not photo_license:
        photo_recovery_status = "default_missing_license"
    else:
        photo_recovery_status = "default_incompatible_license"
    return {
        "taxon_id": int(taxon_id),
        "scientific_name": str(scientific_name),
        "common_name": raw.get("preferred_common_name"),
        "taxon_rank": raw.get("rank") or "species",
        "taxon_group": taxon_group,
        "family_name": family.get("name") if family else None,
        "wikipedia_summary": summary,
        "wikipedia_url": raw.get("wikipedia_url"),
        "conservation_status": global_iucn.get("status") if global_iucn else None,
        "conservation_authority": global_iucn.get("authority") if global_iucn else None,
        "conservation_url": (
            f"https://www.iucnredlist.org/species/{global_iucn['iucn']}"
            if global_iucn and global_iucn.get("iucn") else None
        ),
        "photo_url": photo_url,
        "photo_attribution": photo_attribution if photo_url else None,
        "photo_license_code": compatible_photo_license if photo_url else None,
        "photo_source_url": f"https://www.inaturalist.org/taxa/{taxon_id}",
        "photo_recovery_status": photo_recovery_status,
        "silhouette_url": None,
        "silhouette_source_url": None,
        "silhouette_attribution": None,
        "silhouette_license_code": None,
        "silhouette_license_url": None,
        "silhouette_image_uuid": None,
        "silhouette_node_uuid": None,
        "silhouette_taxon_name": None,
        "silhouette_match_rank": None,
        "phylopic_build": None,
        "updated_at": iso(now or utc_now()),
    }
