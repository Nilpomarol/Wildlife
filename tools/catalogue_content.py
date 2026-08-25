"""Published Wildlife content schema and offline validation helpers."""

from __future__ import annotations

import hashlib
import json
import re
import sqlite3
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import date
from urllib.parse import urlparse


CONTENT_SCHEMA_VERSION = 4
SOURCE_SCHEMA_VERSION = 1
COMPATIBLE_LICENCES = {"pdm", "cc0", "cc-by", "cc-by-sa"}
MEDIA_TYPES = {"photo", "silhouette"}
MEDIA_VARIANTS = {"thumbnail", "detail"}
MEDIA_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
MATCH_RANKS = {"species", "genus", "family", "order", "group"}
PROVIDER_HOSTS = {
    "wikimedia_commons": {"upload.wikimedia.org"},
    "inaturalist": {
        "inaturalist-open-data.s3.amazonaws.com",
        "static.inaturalist.org",
    },
    "phylopic": {"api.phylopic.org", "images.phylopic.org"},
}


def fail(message: str) -> None:
    raise ValueError(f"Catalogue validation failed: {message}")


def require_schema(document: dict, label: str) -> None:
    if document.get("schema_version") != SOURCE_SCHEMA_VERSION:
        fail(f"{label} requires schema_version {SOURCE_SCHEMA_VERSION}")


def required_string(row: dict, key: str, label: str) -> str:
    value = row.get(key)
    if not isinstance(value, str) or not value.strip():
        fail(f"{label} requires {key}")
    return value.strip()


def positive_integer(row: dict, key: str, label: str) -> int:
    value = row.get(key)
    if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
        fail(f"{label} requires a positive integer {key}")
    return value


def https_url(value: str, label: str, *, allow_query: bool = True) -> str:
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        fail(f"{label} must be an absolute HTTPS URL")
    if parsed.fragment:
        fail(f"{label} must not contain a fragment")
    if not allow_query and parsed.query:
        fail(f"{label} must not contain an expiring/query URL")
    return value


def validate_taxa(taxa: list[dict]) -> dict[int, dict]:
    by_id: dict[int, dict] = {}
    for taxon in taxa:
        taxon_id = positive_integer(taxon, "taxon_id", "every global taxon")
        if taxon_id in by_id:
            fail(f"duplicate global taxon_id {taxon_id}")
        required_string(taxon, "rank", f"taxon {taxon_id}")
        required_string(taxon, "scientific_name", f"taxon {taxon_id}")
        source_url = required_string(taxon, "source_url", f"taxon {taxon_id}")
        https_url(source_url, f"taxon {taxon_id} source_url")
        common_names = taxon.get("common_names", {})
        if not isinstance(common_names, dict):
            fail(f"taxon {taxon_id} common_names must be an object when supplied")
        for locale, name in common_names.items():
            if not isinstance(locale, str) or not locale.strip() or not isinstance(name, str) or not name.strip():
                fail(f"taxon {taxon_id} has an invalid common name")
        taxonomy = taxon.get("taxonomy")
        if not isinstance(taxonomy, dict):
            fail(f"taxon {taxon_id} requires taxonomy")
        accepted_id = taxon.get("accepted_taxon_id", taxon_id)
        if not isinstance(accepted_id, int) or accepted_id <= 0:
            fail(f"taxon {taxon_id} has an invalid accepted_taxon_id")
        by_id[taxon_id] = {**taxon, "common_names": common_names, "accepted_taxon_id": accepted_id}
    for taxon_id, taxon in by_id.items():
        if taxon["accepted_taxon_id"] not in by_id:
            fail(f"taxon {taxon_id} points to unknown accepted taxon {taxon['accepted_taxon_id']}")
        accepted = by_id[taxon["accepted_taxon_id"]]
        if accepted["accepted_taxon_id"] != accepted["taxon_id"]:
            fail(f"taxon {taxon_id} must point directly to a canonical accepted taxon")
    return by_id


@dataclass(frozen=True)
class CoverageRows:
    rows: list[dict]
    covered_taxon_ids: frozenset[int]
    waived_taxon_ids: frozenset[int]

    def missing(self, published_taxon_ids: set[int]) -> set[int]:
        return published_taxon_ids - set(self.covered_taxon_ids) - set(self.waived_taxon_ids)


def _validated_waivers(document: dict, taxon_ids: set[int], label: str) -> frozenset[int]:
    waived: set[int] = set()
    for waiver in document.get("waivers", []):
        taxon_id = positive_integer(waiver, "taxon_id", f"{label} waiver")
        if taxon_id not in taxon_ids:
            fail(f"{label} waiver references unknown taxon {taxon_id}")
        required_string(waiver, "reason", f"{label} waiver for {taxon_id}")
        required_string(waiver, "reviewed_by", f"{label} waiver for {taxon_id}")
        reviewed_at = required_string(waiver, "reviewed_at", f"{label} waiver for {taxon_id}")
        try:
            date.fromisoformat(reviewed_at)
        except ValueError as error:
            fail(f"{label} waiver for {taxon_id} has an invalid reviewed_at date")
        if taxon_id in waived:
            fail(f"duplicate {label} waiver for taxon {taxon_id}")
        waived.add(taxon_id)
    return frozenset(waived)


def validate_descriptions(document: dict, taxon_ids: set[int]) -> CoverageRows:
    require_schema(document, "taxon descriptions")
    rows: list[dict] = []
    seen: set[tuple[int, str]] = set()
    covered: set[int] = set()
    for row in document.get("descriptions", []):
        taxon_id = positive_integer(row, "taxon_id", "taxon description")
        if taxon_id not in taxon_ids:
            fail(f"description references unknown taxon {taxon_id}")
        locale = required_string(row, "locale", f"description for {taxon_id}")
        key = (taxon_id, locale)
        if key in seen:
            fail(f"duplicate {locale} description for taxon {taxon_id}")
        seen.add(key)
        summary = required_string(row, "summary", f"description for {taxon_id}/{locale}")
        if len(summary) > 1_200:
            fail(f"description for {taxon_id}/{locale} exceeds 1200 characters")
        source_url = required_string(row, "source_url", f"description for {taxon_id}/{locale}")
        https_url(source_url, f"description for {taxon_id}/{locale} source_url")
        required_string(row, "attribution", f"description for {taxon_id}/{locale}")
        licence = required_string(row, "licence_code", f"description for {taxon_id}/{locale}")
        if licence not in COMPATIBLE_LICENCES:
            fail(f"description for {taxon_id}/{locale} has an incompatible licence")
        licence_url = required_string(row, "licence_url", f"description for {taxon_id}/{locale}")
        https_url(licence_url, f"description for {taxon_id}/{locale} licence_url")
        required_string(row, "retrieved_at", f"description for {taxon_id}/{locale}")
        rows.append(row)
        if locale == "en":
            covered.add(taxon_id)
    waived = _validated_waivers(document, taxon_ids, "description")
    if covered.intersection(waived):
        fail("a taxon cannot have both an English description and a description waiver")
    return CoverageRows(rows, frozenset(covered), waived)


def validate_conservation(document: dict, taxon_ids: set[int]) -> CoverageRows:
    require_schema(document, "taxon conservation")
    rows: list[dict] = []
    covered: set[int] = set()
    for row in document.get("conservation", []):
        taxon_id = positive_integer(row, "taxon_id", "taxon conservation")
        if taxon_id not in taxon_ids:
            fail(f"conservation references unknown taxon {taxon_id}")
        if taxon_id in covered:
            fail(f"duplicate conservation record for taxon {taxon_id}")
        required_string(row, "status", f"conservation for {taxon_id}")
        required_string(row, "authority", f"conservation for {taxon_id}")
        source_url = required_string(row, "source_url", f"conservation for {taxon_id}")
        https_url(source_url, f"conservation for {taxon_id} source_url")
        required_string(row, "retrieved_at", f"conservation for {taxon_id}")
        rows.append(row)
        covered.add(taxon_id)
    waived = _validated_waivers(document, taxon_ids, "conservation")
    if covered.intersection(waived):
        fail("a taxon cannot have both conservation data and a conservation waiver")
    return CoverageRows(rows, frozenset(covered), waived)


def validate_taxon_changes(document: dict, taxon_ids: set[int]) -> list[dict]:
    require_schema(document, "taxon changes")
    changes = document.get("changes", [])
    previous_ids: set[int] = set()
    targets: dict[int, int] = {}
    for row in changes:
        previous = positive_integer(row, "previous_taxon_id", "taxon change")
        accepted = positive_integer(row, "accepted_taxon_id", f"taxon change {previous}")
        if previous == accepted:
            fail(f"taxon change {previous} points to itself")
        if previous in previous_ids:
            fail(f"duplicate taxon change for {previous}")
        if accepted not in taxon_ids:
            fail(f"taxon change {previous} points to unknown accepted taxon {accepted}")
        required_string(row, "change_type", f"taxon change {previous}")
        required_string(row, "source_revision", f"taxon change {previous}")
        previous_ids.add(previous)
        targets[previous] = accepted
    for start in targets:
        visited: set[int] = set()
        current = start
        while current in targets:
            if current in visited:
                fail(f"taxon changes contain a cycle starting at {start}")
            visited.add(current)
            current = targets[current]
    return changes


def _provider_for_source(source_url: str) -> str:
    host = urlparse(source_url).hostname or ""
    if host.endswith("wikimedia.org"):
        return "wikimedia_commons"
    if host.endswith("inaturalist.org"):
        return "inaturalist"
    if host.endswith("phylopic.org"):
        return "phylopic"
    return "legacy_unknown"


def _legacy_asset(item: dict) -> dict:
    source_url = item["source_url"]
    fingerprint = hashlib.sha256(source_url.encode()).hexdigest()[:16]
    provider = _provider_for_source(source_url)
    return {
        **item,
        "asset_id": f"legacy:{item['taxon_id']}:{fingerprint}",
        "media_type": "photo",
        "provider": provider,
        "provider_asset_id": source_url.rsplit("/", 1)[-1],
        "licence_url": item.get("licence_url"),
        "assessment": item.get("assessment"),
        "matched_taxon_id": item["taxon_id"],
        "match_rank": "species",
        "source_revision": "legacy-provenance-v1",
        "variants": [],
        "legacy": True,
    }


def validate_media(
    document: dict,
    taxa: set[int] | dict[int, dict],
    required_direct_taxa: set[int],
    strict: bool,
) -> tuple[list[dict], set[int], set[int]]:
    taxon_ids = set(taxa)
    taxon_names = {
        taxon_id: row.get("scientific_name")
        for taxon_id, row in taxa.items()
    } if isinstance(taxa, dict) else {}
    schema_version = document.get("schema_version")
    if schema_version not in {1, 2, 3}:
        fail("media manifest requires schema_version 1, 2 or 3 during the alpha transition")
    raw_items = document.get("assets") if schema_version in {2, 3} else document.get("media")
    if not isinstance(raw_items, list):
        fail("media manifest requires an asset list")
    assets: list[dict] = []
    asset_ids: set[str] = set()
    provider_ids: dict[tuple[str, str], tuple[str, str]] = {}
    silhouette_roles: set[tuple[int, str]] = set()
    taxon_with_provenance: set[int] = set()
    taxon_with_direct_photo_variants: set[int] = set()
    waived_taxa = _validated_waivers(document, taxon_ids, "media")
    for raw in raw_items:
        if not isinstance(raw, dict):
            fail("every media asset must be an object")
        taxon_id = positive_integer(raw, "taxon_id", "media asset")
        if taxon_id not in taxon_ids:
            fail(f"media asset references unknown taxon {taxon_id}")
        source_url = required_string(raw, "source_url", f"media for {taxon_id}")
        https_url(source_url, f"media for {taxon_id} source_url")
        creator = raw.get("creator")
        licence = required_string(raw, "licence_code", f"media for {taxon_id}")
        if strict and licence not in COMPATIBLE_LICENCES:
            fail(f"media for {taxon_id} has an incompatible release licence")
        item = _legacy_asset(raw) if schema_version == 1 else dict(raw)
        asset_id = required_string(item, "asset_id", f"media for {taxon_id}")
        if asset_id in asset_ids:
            fail(f"duplicate media asset_id {asset_id}")
        asset_ids.add(asset_id)
        media_type = required_string(item, "media_type", f"media {asset_id}")
        if media_type not in MEDIA_TYPES:
            fail(f"media {asset_id} has invalid media_type {media_type}")
        if media_type == "photo" and (not isinstance(creator, str) or not creator.strip()):
            fail(f"photo media {asset_id} requires creator")
        if creator is not None and (not isinstance(creator, str) or not creator.strip()):
            fail(f"media {asset_id} has an invalid creator")
        provider = required_string(item, "provider", f"media {asset_id}")
        provider_asset_id = required_string(item, "provider_asset_id", f"media {asset_id}")
        provider_key = (provider, provider_asset_id)
        provenance = (source_url, licence)
        if provider_key in provider_ids and provider_ids[provider_key] != provenance:
            fail(f"provider asset {provider}/{provider_asset_id} has conflicting provenance")
        provider_ids[provider_key] = provenance
        match_rank = required_string(item, "match_rank", f"media {asset_id}")
        if match_rank not in MATCH_RANKS:
            fail(f"media {asset_id} has invalid match_rank {match_rank}")
        if media_type == "silhouette":
            role = silhouette_role(match_rank)
            key = (taxon_id, role)
            if key in silhouette_roles:
                fail(f"taxon {taxon_id} has duplicate {role} silhouette assignments")
            silhouette_roles.add(key)
        matched_taxon_id = item.get("matched_taxon_id")
        if matched_taxon_id is not None:
            if not isinstance(matched_taxon_id, int) or isinstance(matched_taxon_id, bool) or matched_taxon_id <= 0:
                fail(f"media {asset_id} has an invalid matched_taxon_id")
            if matched_taxon_id not in taxon_ids:
                fail(f"media {asset_id} matches unknown taxon {matched_taxon_id}")
        if match_rank == "species" and matched_taxon_id is None:
            fail(f"species media {asset_id} requires matched_taxon_id")
        matched_taxon_name = item.get("matched_taxon_name")
        if schema_version == 3:
            matched_taxon_name = required_string(item, "matched_taxon_name", f"media {asset_id}")
        elif not isinstance(matched_taxon_name, str) or not matched_taxon_name.strip():
            matched_taxon_name = taxon_names.get(matched_taxon_id)
            if not matched_taxon_name:
                fail(f"media {asset_id} requires matched_taxon_name")
            item["matched_taxon_name"] = matched_taxon_name
        required_string(item, "source_revision", f"media {asset_id}")
        if schema_version in {2, 3}:
            licence_url = required_string(item, "licence_url", f"media {asset_id}")
            https_url(licence_url, f"media {asset_id} licence_url")
        variants = item.get("variants", [])
        if not isinstance(variants, list):
            fail(f"media {asset_id} variants must be a list")
        seen_variants: set[str] = set()
        for variant in variants:
            variant_name = required_string(variant, "variant", f"media {asset_id} variant")
            if variant_name not in MEDIA_VARIANTS or variant_name in seen_variants:
                fail(f"media {asset_id} has an invalid or duplicate variant {variant_name}")
            seen_variants.add(variant_name)
            direct_url = required_string(variant, "direct_url", f"media {asset_id}/{variant_name}")
            https_url(direct_url, f"media {asset_id}/{variant_name} direct_url", allow_query=False)
            host = urlparse(direct_url).hostname or ""
            if provider not in PROVIDER_HOSTS or host not in PROVIDER_HOSTS[provider]:
                fail(f"media {asset_id}/{variant_name} uses unapproved host {host}")
            mime_type = required_string(variant, "mime_type", f"media {asset_id}/{variant_name}")
            if mime_type not in MEDIA_MIME_TYPES:
                fail(f"media {asset_id}/{variant_name} has unsupported MIME type")
            positive_integer(variant, "width", f"media {asset_id}/{variant_name}")
            positive_integer(variant, "height", f"media {asset_id}/{variant_name}")
            expected_bytes = variant.get("expected_bytes")
            content_hash = variant.get("content_sha256")
            if strict:
                if not isinstance(expected_bytes, int) or expected_bytes <= 0:
                    fail(f"media {asset_id}/{variant_name} requires expected_bytes")
                if not isinstance(content_hash, str) or not re.fullmatch(r"[a-f0-9]{64}", content_hash):
                    fail(f"media {asset_id}/{variant_name} requires content_sha256")
        if media_type == "photo" and MEDIA_VARIANTS.issubset(seen_variants):
            taxon_with_direct_photo_variants.add(taxon_id)
        taxon_with_provenance.add(taxon_id)
        assets.append(item)
    if taxon_with_direct_photo_variants.intersection(waived_taxa):
        fail("a taxon cannot have both direct photo variants and a media waiver")
    if strict:
        missing = required_direct_taxa - taxon_with_direct_photo_variants - set(waived_taxa)
        if missing:
            fail(f"achievement taxa require direct thumbnail/detail variants: {sorted(missing)}")
        legacy = [item["taxon_id"] for item in assets if item.get("legacy")]
        if legacy:
            fail(f"legacy media records cannot be released: {sorted(legacy)}")
    return assets, taxon_with_provenance, taxon_with_direct_photo_variants


def silhouette_role(match_rank: str) -> str:
    if match_rank in {"species", "genus"}:
        return "specific"
    if match_rank in {"family", "order"}:
        return "family"
    if match_rank == "group":
        return "group"
    fail(f"invalid silhouette match rank {match_rank}")


def create_schema(database: sqlite3.Connection) -> None:
    database.execute("PRAGMA foreign_keys = ON")
    database.execute(f"PRAGMA user_version = {CONTENT_SCHEMA_VERSION}")
    database.executescript(
        """
        CREATE TABLE content_generation (
            generation_id TEXT PRIMARY KEY,
            generation_sequence INTEGER NOT NULL UNIQUE CHECK(generation_sequence > 0),
            schema_version INTEGER NOT NULL,
            source_digest TEXT NOT NULL,
            release_status TEXT NOT NULL CHECK(release_status IN ('draft','release')),
            generated_at TEXT NOT NULL,
            minimum_app_version INTEGER NOT NULL
        );
        CREATE TABLE taxon (
            taxon_id INTEGER PRIMARY KEY,
            rank TEXT NOT NULL,
            scientific_name TEXT NOT NULL,
            accepted_taxon_id INTEGER NOT NULL,
            taxonomy_json TEXT NOT NULL,
            source_url TEXT NOT NULL,
            source_revision TEXT,
            FOREIGN KEY(accepted_taxon_id) REFERENCES taxon(taxon_id)
                DEFERRABLE INITIALLY DEFERRED
        );
        CREATE TABLE taxon_name (
            taxon_id INTEGER NOT NULL,
            locale TEXT NOT NULL,
            common_name TEXT NOT NULL,
            is_primary INTEGER NOT NULL,
            PRIMARY KEY(taxon_id, locale),
            CHECK(is_primary IN (0,1)),
            FOREIGN KEY(taxon_id) REFERENCES taxon(taxon_id)
        );
        CREATE TABLE taxon_description (
            taxon_id INTEGER NOT NULL,
            locale TEXT NOT NULL,
            summary TEXT NOT NULL,
            source_url TEXT NOT NULL,
            attribution TEXT NOT NULL,
            licence_code TEXT NOT NULL,
            licence_url TEXT NOT NULL,
            retrieved_at TEXT NOT NULL,
            source_revision TEXT,
            PRIMARY KEY(taxon_id, locale),
            FOREIGN KEY(taxon_id) REFERENCES taxon(taxon_id)
        );
        CREATE TABLE taxon_conservation (
            taxon_id INTEGER PRIMARY KEY,
            status TEXT NOT NULL,
            authority TEXT NOT NULL,
            source_url TEXT NOT NULL,
            retrieved_at TEXT NOT NULL,
            source_revision TEXT,
            FOREIGN KEY(taxon_id) REFERENCES taxon(taxon_id)
        );
        CREATE TABLE taxon_change (
            previous_taxon_id INTEGER PRIMARY KEY,
            accepted_taxon_id INTEGER NOT NULL,
            change_type TEXT NOT NULL,
            source_revision TEXT NOT NULL,
            FOREIGN KEY(accepted_taxon_id) REFERENCES taxon(taxon_id)
        );
        CREATE TABLE region (
            region_key TEXT PRIMARY KEY,
            display_order INTEGER NOT NULL UNIQUE,
            names_json TEXT NOT NULL
        );
        CREATE TABLE catalogue_version (
            region_key TEXT PRIMARY KEY,
            catalogue_version TEXT NOT NULL,
            status TEXT NOT NULL CHECK(status IN ('draft','frozen')),
            content_rules_version TEXT NOT NULL,
            UNIQUE(region_key, catalogue_version),
            FOREIGN KEY(region_key) REFERENCES region(region_key)
        );
        CREATE TABLE regional_taxon (
            region_key TEXT NOT NULL,
            catalogue_version TEXT NOT NULL,
            taxon_id INTEGER NOT NULL,
            encounter_rarity TEXT NOT NULL CHECK(encounter_rarity IN ('unknown','common','uncommon','rare','very_rare')),
            seasonality_json TEXT NOT NULL,
            sort_order INTEGER NOT NULL,
            inclusion_provenance TEXT NOT NULL,
            PRIMARY KEY(region_key, catalogue_version, taxon_id),
            FOREIGN KEY(region_key, catalogue_version)
                REFERENCES catalogue_version(region_key, catalogue_version),
            FOREIGN KEY(taxon_id) REFERENCES taxon(taxon_id)
        );
        CREATE INDEX regional_taxon_order
            ON regional_taxon(region_key, catalogue_version, sort_order);
        CREATE INDEX regional_taxon_taxon ON regional_taxon(taxon_id);
        CREATE TABLE regional_achievement (
            region_key TEXT NOT NULL,
            catalogue_version TEXT NOT NULL,
            achievement_key TEXT NOT NULL,
            achievement_type TEXT NOT NULL CHECK(achievement_type IN ('essentials','icons')),
            PRIMARY KEY(region_key, catalogue_version, achievement_key),
            FOREIGN KEY(region_key, catalogue_version)
                REFERENCES catalogue_version(region_key, catalogue_version)
        );
        CREATE TABLE regional_achievement_taxon (
            region_key TEXT NOT NULL,
            catalogue_version TEXT NOT NULL,
            achievement_key TEXT NOT NULL,
            taxon_id INTEGER NOT NULL,
            sort_order INTEGER NOT NULL CHECK(sort_order > 0),
            PRIMARY KEY(region_key, catalogue_version, achievement_key, taxon_id),
            UNIQUE(region_key, catalogue_version, achievement_key, sort_order),
            FOREIGN KEY(region_key, catalogue_version, achievement_key)
                REFERENCES regional_achievement(region_key, catalogue_version, achievement_key)
                ON DELETE CASCADE,
            FOREIGN KEY(region_key, catalogue_version, taxon_id)
                REFERENCES regional_taxon(region_key, catalogue_version, taxon_id)
        );
        CREATE INDEX regional_achievement_taxon_order
            ON regional_achievement_taxon(region_key, catalogue_version, achievement_key, sort_order);
        CREATE TABLE media_asset (
            asset_id TEXT PRIMARY KEY,
            taxon_id INTEGER NOT NULL,
            media_type TEXT NOT NULL CHECK(media_type IN ('photo','silhouette')),
            provider TEXT NOT NULL,
            provider_asset_id TEXT NOT NULL,
            source_url TEXT NOT NULL,
            creator TEXT,
            licence_code TEXT NOT NULL,
            licence_url TEXT,
            assessment TEXT,
            matched_taxon_id INTEGER,
            matched_taxon_name TEXT NOT NULL,
            match_rank TEXT NOT NULL CHECK(match_rank IN ('species','genus','family','order','group')),
            source_revision TEXT NOT NULL,
            FOREIGN KEY(taxon_id) REFERENCES taxon(taxon_id),
            FOREIGN KEY(matched_taxon_id) REFERENCES taxon(taxon_id)
        );
        CREATE INDEX media_asset_taxon ON media_asset(taxon_id, media_type);
        CREATE TABLE media_variant (
            asset_id TEXT NOT NULL,
            variant TEXT NOT NULL CHECK(variant IN ('thumbnail','detail')),
            direct_url TEXT NOT NULL,
            mime_type TEXT NOT NULL,
            width INTEGER NOT NULL,
            height INTEGER NOT NULL,
            expected_bytes INTEGER CHECK(expected_bytes IS NULL OR expected_bytes > 0),
            content_sha256 TEXT,
            PRIMARY KEY(asset_id, variant),
            FOREIGN KEY(asset_id) REFERENCES media_asset(asset_id) ON DELETE CASCADE
        );
        """
    )


def logical_database_digest(database: sqlite3.Connection) -> str:
    """Hash schema and ordered row values without depending on SQLite file layout."""
    digest = hashlib.sha256()
    tables = [
        row[0]
        for row in database.execute(
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
        )
    ]
    for table in tables:
        digest.update(table.encode())
        schema = database.execute(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name=?", (table,)
        ).fetchone()[0]
        digest.update((schema or "").encode())
        columns = [row[1] for row in database.execute(f'PRAGMA table_info("{table}")')]
        order = ", ".join(f'"{column}"' for column in columns)
        for row in database.execute(f'SELECT * FROM "{table}" ORDER BY {order}'):
            digest.update(json.dumps(row, ensure_ascii=False, separators=(",", ":")).encode())
    return digest.hexdigest()


def published_taxon_ids(catalogues: Iterable[dict]) -> set[int]:
    return {
        entry["taxon_id"]
        for catalogue in catalogues
        for entry in catalogue.get("taxa", [])
    }
