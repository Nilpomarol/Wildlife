from __future__ import annotations

import json
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
PAGE_SIZE = 200
OVERLAP = timedelta(minutes=5)
FULL_RECONCILIATION_INTERVAL = timedelta(hours=24)
MIN_SYNC_INTERVAL_SECONDS = 30


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


@dataclass(frozen=True)
class ConfirmationResult:
    xp_awarded: int
    total_xp: int
    confirmed_count: int


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
            CREATE INDEX IF NOT EXISTS idx_api_requests_time ON api_requests(requested_at);
            CREATE INDEX IF NOT EXISTS idx_observations_user_updated ON observations(user_id, updated_at);
            """
        )
        self._add_column("observations", "taxon_rank", "TEXT")
        self._add_column("observations", "collection_taxon_id", "INTEGER")
        self._add_column("observations", "collection_taxon_rank", "TEXT")

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
