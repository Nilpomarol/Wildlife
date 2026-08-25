"""Networked authoring helpers for the immutable Wildlife content pack.

This module is intentionally absent from the Android build path. Remote evidence is frozen under
``catalogues/review/content_refresh`` and only reviewed source manifests feed the offline generator.
"""

from __future__ import annotations

import hashlib
import html
import gzip
import json
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor, as_completed
from io import BytesIO
from pathlib import Path
from typing import Callable

from PIL import Image, UnidentifiedImageError
try:
    from tools.reference_media_rejections import normalized_source_key
except ModuleNotFoundError:
    from reference_media_rejections import normalized_source_key


USER_AGENT = "WildlifeContentAuthoring/2.0 (read-only; https://www.inaturalist.org/)"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
INATURALIST_API = "https://api.inaturalist.org/v1/taxa"
PHYLOPIC_API = "https://api.phylopic.org"
ALLOWED_IMAGE_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
ALLOWED_JSON_MIME_TYPES = {"application/json", "application/vnd.phylopic.v2+json"}
ALLOWED_MEDIA_HOSTS = {
    "upload.wikimedia.org",
    "inaturalist-open-data.s3.amazonaws.com",
    "static.inaturalist.org",
    "api.phylopic.org",
    "images.phylopic.org",
}
MAX_JSON_BYTES = 20 * 1024 * 1024
MAX_IMAGE_BYTES = 24 * 1024 * 1024
THUMBNAIL_WIDTH = 360
DETAIL_WIDTH = 1600
PHYLOPIC_VALIDATION_WORKERS = 4
LICENCE_CODES = {
    "cc0": "cc0",
    "cc0-1.0": "cc0",
    "cc by": "cc-by",
    "cc-by": "cc-by",
    "cc by-sa": "cc-by-sa",
    "cc-by-sa": "cc-by-sa",
    "public domain": "pdm",
    "pd": "pdm",
    "pdm": "pdm",
}


def stable_commons_direct_url(value: str) -> str:
    """Remove only MediaWiki's documented tracking decoration from an upload URL."""
    parsed = urllib.parse.urlparse(value)
    query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
    if parsed.scheme != "https" or parsed.hostname != "upload.wikimedia.org":
        raise ValueError(f"Commons returned an unapproved image URL: {value}")
    if query and not set(query).issubset({"utm_source", "utm_campaign", "utm_content"}):
        raise ValueError(f"Commons returned a non-tracking query URL: {value}")
    return urllib.parse.urlunparse(parsed._replace(query="", fragment=""))


def compatible_licence_url(code: str, version: str | None) -> str | None:
    if code == "cc0":
        return "https://creativecommons.org/publicdomain/zero/1.0/"
    if code == "pdm":
        return "https://creativecommons.org/publicdomain/mark/1.0/"
    if code in {"cc-by", "cc-by-sa"} and version:
        slug = "by" if code == "cc-by" else "by-sa"
        return f"https://creativecommons.org/licenses/{slug}/{version}/"
    return None


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def atomic_write(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_bytes(payload)
    temporary.replace(path)


def atomic_write_json(path: Path, value: object) -> None:
    atomic_write(path, canonical_json_bytes(value))


def read_json(path: Path) -> dict:
    payload = gzip.decompress(path.read_bytes()) if path.suffix == ".gz" else path.read_bytes()
    value = json.loads(payload.decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object in {path}")
    return value


def snapshot_digest(value: object) -> str:
    return hashlib.sha256(canonical_json_bytes(value)).hexdigest()


def chunks(values: list, size: int) -> list[list]:
    return [values[index:index + size] for index in range(0, len(values), size)]


def clean_html(value: str | None) -> str:
    if not value:
        return ""
    without_tags = re.sub(r"<[^>]+>", " ", value)
    cleaned = " ".join(html.unescape(without_tags).split())
    cleaned = re.sub(r"\(\s+", "(", cleaned)
    cleaned = re.sub(r"\s+\)", ")", cleaned)
    return re.sub(r"\s+([,.;:!?])", r"\1", cleaned)


def normalise_licence(value: str | None) -> str | None:
    if not value:
        return None
    cleaned = re.sub(r"(?:[ -])\d+(?:\.\d+)*$", "", value.strip().lower())
    return LICENCE_CODES.get(cleaned)


def commons_title(source_url: str) -> str:
    parsed = urllib.parse.urlparse(source_url)
    if parsed.scheme != "https" or parsed.hostname != "commons.wikimedia.org":
        raise ValueError(f"Unsupported Commons source URL: {source_url}")
    prefix = "/wiki/"
    if not parsed.path.startswith(prefix):
        raise ValueError(f"Commons source URL has no file title: {source_url}")
    title = urllib.parse.unquote(parsed.path[len(prefix):]).replace("_", " ")
    if not title.casefold().startswith("file:"):
        raise ValueError(f"Commons source URL is not a file page: {source_url}")
    return "File:" + title.split(":", 1)[1]


def image_dimensions(payload: bytes) -> tuple[str, int, int]:
    try:
        with Image.open(BytesIO(payload)) as image:
            image.verify()
        with Image.open(BytesIO(payload)) as image:
            image.load()
            format_name = image.format
            width, height = image.size
    except (OSError, UnidentifiedImageError) as error:
        raise ValueError("Remote payload is not a supported decodable image") from error
    mime_type = Image.MIME.get(format_name or "")
    if mime_type not in ALLOWED_IMAGE_MIME_TYPES or width <= 0 or height <= 0:
        raise ValueError("Remote payload is not a supported decodable image")
    return mime_type, width, height


@dataclass(frozen=True)
class ResponseBytes:
    payload: bytes
    final_url: str
    content_type: str


class RemoteRequestError(ValueError):
    """A provider request failure with an explicit retry/circuit-breaker classification."""

    def __init__(self, url: str, error: Exception, *, retryable: bool):
        self.url = url
        self.retryable = retryable
        disposition = "failed after retries" if retryable else "was rejected"
        super().__init__(f"Remote request {disposition}: {url}: {error}")


class CachedHttpClient:
    def __init__(self, cache_root: Path, *, offline: bool, refresh: bool, delay: float = 1.05):
        self.cache_root = cache_root
        self.offline = offline
        self.refresh = refresh
        self.delay = delay
        self._last_request_at = 0.0
        self.cache_hits = 0
        self.network_requests = 0
        self._rate_lock = threading.Lock()
        self._stats_lock = threading.Lock()
        self._validation_locks_guard = threading.Lock()
        self._validation_locks: dict[str, threading.Lock] = {}

    def _record_cache_hit(self) -> None:
        with self._stats_lock:
            self.cache_hits += 1

    def _validation_lock(self, key: str) -> threading.Lock:
        with self._validation_locks_guard:
            return self._validation_locks.setdefault(key, threading.Lock())

    def _wait_for_request_slot(self) -> None:
        with self._rate_lock:
            elapsed = time.monotonic() - self._last_request_at
            if elapsed < self.delay:
                time.sleep(self.delay - elapsed)
            self._last_request_at = time.monotonic()
            with self._stats_lock:
                self.network_requests += 1

    def _request(self, url: str, maximum_bytes: int) -> ResponseBytes:
        if self.offline:
            raise ValueError(f"Offline refresh is missing cached evidence for {url}")
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
        for attempt in range(4):
            try:
                self._wait_for_request_slot()
                with urllib.request.urlopen(request, timeout=45) as response:
                    declared = response.headers.get("Content-Length")
                    if declared and int(declared) > maximum_bytes:
                        raise ValueError(f"Remote payload exceeds {maximum_bytes} bytes: {url}")
                    payload = response.read(maximum_bytes + 1)
                    if len(payload) > maximum_bytes:
                        raise ValueError(f"Remote payload exceeds {maximum_bytes} bytes: {url}")
                    return ResponseBytes(
                        payload=payload,
                        final_url=response.geturl(),
                        content_type=response.headers.get_content_type(),
                    )
            except urllib.error.HTTPError as error:
                retryable = error.code >= 500 or error.code in {408, 425, 429}
                if not retryable:
                    raise RemoteRequestError(url, error, retryable=False) from error
                if attempt == 3:
                    raise RemoteRequestError(url, error, retryable=True) from error
                time.sleep(2 ** attempt)
            except (urllib.error.URLError, TimeoutError) as error:
                if attempt == 3:
                    raise RemoteRequestError(url, error, retryable=True) from error
                time.sleep(2 ** attempt)
        raise AssertionError("unreachable")

    def json(
        self,
        namespace: str,
        key: str,
        url: str,
        normalizer: Callable[[dict], dict] | None = None,
    ) -> dict:
        cache = self.cache_root / "raw" / namespace / f"{key}.json.gz"
        legacy_cache = cache.with_suffix("")
        if cache.exists() and not self.refresh:
            self._record_cache_hit()
            value = read_json(cache)
            normalized = normalizer(value) if normalizer else value
            if normalized != value:
                atomic_write(cache, gzip.compress(canonical_json_bytes(normalized), compresslevel=9, mtime=0))
            return normalized
        if legacy_cache.exists() and not self.refresh:
            self._record_cache_hit()
            value = read_json(legacy_cache)
            value = normalizer(value) if normalizer else value
            atomic_write(cache, gzip.compress(canonical_json_bytes(value), compresslevel=9, mtime=0))
            legacy_cache.unlink()
            return value
        response = self._request(url, MAX_JSON_BYTES)
        if response.content_type not in ALLOWED_JSON_MIME_TYPES:
            raise ValueError(f"Expected JSON from {url}, received {response.content_type}")
        value = json.loads(response.payload)
        if not isinstance(value, dict):
            raise ValueError(f"Expected a JSON object from {url}")
        value = normalizer(value) if normalizer else value
        atomic_write(cache, gzip.compress(canonical_json_bytes(value), compresslevel=9, mtime=0))
        return value

    def validate_image(self, direct_url: str, retrieved_at: str) -> dict:
        parsed = urllib.parse.urlparse(direct_url)
        if (
            parsed.scheme != "https"
            or parsed.hostname not in ALLOWED_MEDIA_HOSTS
            or parsed.query
            or parsed.fragment
        ):
            raise ValueError(f"Image URL is not a stable approved direct URL: {direct_url}")
        key = hashlib.sha256(direct_url.encode()).hexdigest()
        cache = self.cache_root / "validated" / f"{key}.json"
        with self._validation_lock(key):
            if cache.exists() and not self.refresh:
                self._record_cache_hit()
                snapshot = read_json(cache)
                if snapshot.get("direct_url") != direct_url:
                    raise ValueError(f"Image validation cache collision for {direct_url}")
                return snapshot
            response = self._request(direct_url, MAX_IMAGE_BYTES)
            final = urllib.parse.urlparse(response.final_url)
            if final.hostname not in ALLOWED_MEDIA_HOSTS or final.query or final.fragment:
                raise ValueError(f"Image redirected to an unapproved or unstable URL: {response.final_url}")
            mime_type, width, height = image_dimensions(response.payload)
            if response.content_type not in ALLOWED_IMAGE_MIME_TYPES or response.content_type != mime_type:
                raise ValueError(
                    f"Image content type mismatch for {direct_url}: {response.content_type} versus {mime_type}"
                )
            snapshot = {
                "schema_version": 1,
                "direct_url": direct_url,
                "mime_type": mime_type,
                "width": width,
                "height": height,
                "expected_bytes": len(response.payload),
                "content_sha256": hashlib.sha256(response.payload).hexdigest(),
                "validated_at": retrieved_at,
            }
            atomic_write_json(cache, snapshot)
            return snapshot

    def statistics(self) -> dict[str, int]:
        with self._stats_lock:
            return {
                "cache_hits": self.cache_hits,
                "network_requests": self.network_requests,
            }


def _api_url(base: str, params: dict[str, str | int]) -> str:
    return f"{base}?{urllib.parse.urlencode(params)}"


class WikimediaResolver:
    def __init__(self, client: CachedHttpClient):
        self.client = client

    def _query(self, titles: list[str], width: int, include_metadata: bool) -> dict[str, dict]:
        params: dict[str, str | int] = {
            "action": "query",
            "format": "json",
            "formatversion": 2,
            "redirects": 1,
            "prop": "imageinfo",
            "titles": "|".join(titles),
            "iilimit": 1,
            "iiprop": "timestamp|user|url|size|mime|sha1|thumbmime" + ("|extmetadata" if include_metadata else ""),
            "iiurlwidth": width,
        }
        if include_metadata:
            params["iiextmetadatafilter"] = "Artist|Credit|LicenseShortName|LicenseUrl"
            params["iiextmetadatalanguage"] = "en"
        key = hashlib.sha256(canonical_json_bytes(params)).hexdigest()[:20]
        response = self.client.json("wikimedia", f"{width}-{key}", _api_url(COMMONS_API, params))
        pages = response.get("query", {}).get("pages", [])
        if not isinstance(pages, list):
            raise ValueError("Commons response has no page list")
        result: dict[str, dict] = {}
        for page in pages:
            title = page.get("title")
            if not isinstance(title, str) or page.get("missing") is not None:
                continue
            result[title.replace("_", " ").casefold()] = page
        return result

    def resolve(self, legacy_items: list[dict], retrieved_at: str) -> tuple[list[dict], list[dict]]:
        titles = sorted({commons_title(item["source_url"]) for item in legacy_items}, key=str.casefold)
        metadata: dict[str, dict] = {}
        thumbnails: dict[str, dict] = {}
        details: dict[str, dict] = {}
        for batch in chunks(titles, 25):
            metadata.update(self._query(batch, THUMBNAIL_WIDTH, True))
            thumbnails.update(self._query(batch, THUMBNAIL_WIDTH, False))
            details.update(self._query(batch, DETAIL_WIDTH, False))

        assets: list[dict] = []
        failures: list[dict] = []
        for item in sorted(legacy_items, key=lambda value: value["taxon_id"]):
            title = commons_title(item["source_url"])
            key = title.replace("_", " ").casefold()
            try:
                metadata_page = metadata[key]
                thumb_page = thumbnails[key]
                detail_page = details[key]
                info = metadata_page["imageinfo"][0]
                ext = info.get("extmetadata", {})
                remote_licence = normalise_licence(ext.get("LicenseShortName", {}).get("value"))
                curated_licence = normalise_licence(item.get("licence_code"))
                if not remote_licence or remote_licence != curated_licence:
                    raise ValueError(
                        f"licence changed: curated={item.get('licence_code')} remote={ext.get('LicenseShortName', {}).get('value')}"
                    )
                licence_url = compatible_licence_url(remote_licence, item.get("licence_version"))
                if not licence_url:
                    remote_licence_url = ext.get("LicenseUrl", {}).get("value")
                    if isinstance(remote_licence_url, str) and remote_licence_url.startswith("https://"):
                        licence_url = remote_licence_url
                if not isinstance(licence_url, str) or not licence_url.startswith("https://"):
                    raise ValueError("Commons metadata has no HTTPS licence URL")
                variants = []
                original_width = info.get("width")
                original_height = info.get("height")
                if not isinstance(original_width, int) or not isinstance(original_height, int):
                    raise ValueError("Commons metadata has no original image dimensions")
                for variant_name, requested_width, page in (
                    ("thumbnail", THUMBNAIL_WIDTH, thumb_page),
                    ("detail", DETAIL_WIDTH, detail_page),
                ):
                    variant_info = page["imageinfo"][0]
                    returned_url = variant_info.get("thumburl") or variant_info.get("url")
                    if not isinstance(returned_url, str):
                        raise ValueError(f"Commons returned no {variant_name} URL")
                    direct_url = stable_commons_direct_url(returned_url)
                    validated = self.client.validate_image(direct_url, retrieved_at)
                    minimum_width = min(requested_width, original_width)
                    maximum_width = min(original_width, requested_width * 2)
                    actual_ratio = validated["width"] / validated["height"]
                    original_ratio = original_width / original_height
                    if not minimum_width <= validated["width"] <= maximum_width:
                        raise ValueError(
                            f"{variant_name} width {validated['width']} is outside the approved "
                            f"Commons range {minimum_width}..{maximum_width}"
                        )
                    if abs(actual_ratio - original_ratio) / original_ratio > 0.01:
                        raise ValueError(f"{variant_name} aspect ratio differs from the Commons original")
                    variants.append({"variant": variant_name, **{
                        name: validated[name]
                        for name in ("direct_url", "mime_type", "width", "height", "expected_bytes", "content_sha256")
                    }})
                page_id = metadata_page.get("pageid")
                if not isinstance(page_id, int) or page_id <= 0:
                    raise ValueError("Commons response has no stable page ID")
                remote_creator = clean_html(ext.get("Artist", {}).get("value")) or clean_html(
                    ext.get("Credit", {}).get("value")
                )
                assets.append({
                    "asset_id": f"commons:{page_id}",
                    "taxon_id": item["taxon_id"],
                    "media_type": "photo",
                    "provider": "wikimedia_commons",
                    "provider_asset_id": str(page_id),
                    "source_url": info.get("descriptionurl") or item["source_url"],
                    "creator": item["creator"],
                    "licence_code": remote_licence,
                    "licence_url": licence_url,
                    "assessment": item.get("selection_note", "Reviewed exact-species reference photo."),
                    "matched_taxon_id": item["taxon_id"],
                    "matched_taxon_name": item["scientific_name"],
                    "match_rank": "species",
                    "source_revision": f"commons:{info.get('sha1')}:{info.get('timestamp')}",
                    "resolved_creator": remote_creator,
                    "variants": variants,
                })
            except (KeyError, IndexError, TypeError, ValueError) as error:
                failures.append({
                    "taxon_id": item["taxon_id"],
                    "source_url": item["source_url"],
                    "error": str(error),
                })
        return assets, failures


class INaturalistResolver:
    def __init__(self, client: CachedHttpClient):
        self.client = client

    @staticmethod
    def _normalise_response(response: dict) -> dict:
        taxon_fields = {
            "id", "name", "rank", "preferred_common_name", "iconic_taxon_name", "ancestor_ids",
            "is_active", "observations_count", "updated_at", "wikipedia_url", "wikipedia_summary",
            "conservation_status",
        }
        photo_fields = {"id", "license_code", "attribution", "url", "original_dimensions"}
        results = []
        for raw in response.get("results", []):
            if not isinstance(raw, dict):
                continue
            taxon = {key: raw[key] for key in sorted(taxon_fields) if key in raw}
            photo = raw.get("default_photo")
            if isinstance(photo, dict):
                taxon["default_photo"] = {
                    key: photo[key] for key in sorted(photo_fields) if key in photo
                }
            results.append(taxon)
        return {
            "total_results": response.get("total_results", len(results)),
            "results": sorted(results, key=lambda row: row.get("id", 0)),
        }

    def _cached_taxa(self) -> dict[int, dict]:
        cached: dict[int, dict] = {}
        cache_directory = self.client.cache_root / "raw" / "inaturalist"
        for path in sorted(cache_directory.glob("*.json.gz")):
            response = read_json(path)
            normalized = self._normalise_response(response)
            if normalized != response:
                atomic_write(path, gzip.compress(canonical_json_bytes(normalized), compresslevel=9, mtime=0))
            for taxon in normalized["results"]:
                taxon_id = taxon.get("id")
                if not isinstance(taxon_id, int):
                    continue
                current = cached.get(taxon_id)
                if current is None or str(taxon.get("updated_at", "")) >= str(current.get("updated_at", "")):
                    cached[taxon_id] = taxon
        return cached

    def resolve(
        self,
        taxon_ids: list[int],
        progress: Callable[[int, int], None] | None = None,
    ) -> tuple[dict[int, dict], list[int]]:
        requested = set(taxon_ids)
        taxa = {} if self.client.refresh else {
            taxon_id: taxon
            for taxon_id, taxon in self._cached_taxa().items()
            if taxon_id in requested
        }
        pending = sorted(requested) if self.client.refresh else sorted(requested - set(taxa))
        if progress:
            progress(len(taxa), len(requested))
        for batch in chunks(pending, 30):
            joined = ",".join(map(str, batch))
            key = hashlib.sha256(joined.encode()).hexdigest()[:20]
            response = self.client.json(
                "inaturalist",
                key,
                f"{INATURALIST_API}/{joined}",
                self._normalise_response,
            )
            for taxon in response.get("results", []):
                if isinstance(taxon, dict) and isinstance(taxon.get("id"), int):
                    taxa[taxon["id"]] = taxon
            if progress:
                progress(min(len(requested), len(taxa)), len(requested))
        return taxa, sorted(requested - set(taxa))


def inaturalist_direct_url(value: str, size: str) -> str:
    if size not in {"small", "medium", "large"}:
        raise ValueError(f"Unsupported iNaturalist image size: {size}")
    parsed = urllib.parse.urlparse(value)
    if (
        parsed.scheme != "https"
        or parsed.hostname not in ALLOWED_MEDIA_HOSTS - {"upload.wikimedia.org"}
        or parsed.query
        or parsed.fragment
    ):
        raise ValueError(f"Unsupported iNaturalist photo URL: {value}")
    name = parsed.path.rsplit("/", 1)[-1]
    if "." not in name or name.split(".", 1)[0] not in {
        "square", "small", "medium", "large", "original"
    }:
        raise ValueError(f"iNaturalist photo URL has no replaceable size: {value}")
    replacement = f"{size}.{name.split('.', 1)[1]}"
    return urllib.parse.urlunparse(parsed._replace(path=parsed.path.rsplit("/", 1)[0] + "/" + replacement))


class INaturalistMediaResolver:
    """Resolve exact-taxon, redistribution-compatible default photos into frozen variants."""

    def __init__(self, client: CachedHttpClient):
        self.client = client

    def resolve(
        self,
        taxa: list[dict],
        retrieved_at: str,
        progress: Callable[[int, int, int, int], None] | None = None,
        excluded_source_urls: dict[int, set[str]] | None = None,
    ) -> tuple[list[dict], list[dict]]:
        assets: list[dict] = []
        failures: list[dict] = []
        ordered_taxa = sorted(taxa, key=lambda row: row.get("id", 0))
        for index, taxon in enumerate(ordered_taxa, start=1):
            photo = taxon.get("default_photo")
            if not isinstance(photo, dict):
                if progress:
                    progress(index, len(ordered_taxa), len(assets), len(failures))
                continue
            licence = normalise_licence(photo.get("license_code"))
            licence_url = compatible_licence_url(licence, "4.0") if licence else None
            if licence not in {"cc0", "cc-by", "cc-by-sa", "pdm"} or not licence_url:
                if progress:
                    progress(index, len(ordered_taxa), len(assets), len(failures))
                continue
            taxon_id = taxon.get("id")
            photo_id = photo.get("id")
            creator = clean_html(photo.get("attribution"))
            source = photo.get("url")
            if not isinstance(taxon_id, int) or not isinstance(photo_id, int):
                if progress:
                    progress(index, len(ordered_taxa), len(assets), len(failures))
                continue
            source_page = f"https://www.inaturalist.org/photos/{photo_id}"
            if normalized_source_key(source_page) in (excluded_source_urls or {}).get(taxon_id, set()):
                if progress:
                    progress(index, len(ordered_taxa), len(assets), len(failures))
                continue
            try:
                if not creator:
                    raise ValueError("licensed iNaturalist photo has no attribution")
                if not isinstance(source, str):
                    raise ValueError("licensed iNaturalist photo has no direct URL")
                original = photo.get("original_dimensions")
                original_ratio = None
                if isinstance(original, dict):
                    width, height = original.get("width"), original.get("height")
                    if isinstance(width, int) and width > 0 and isinstance(height, int) and height > 0:
                        original_ratio = width / height
                variants = []
                for variant, size in (("thumbnail", "small"), ("detail", "large")):
                    direct_url = inaturalist_direct_url(source, size)
                    validated = self.client.validate_image(direct_url, retrieved_at)
                    if original_ratio is not None:
                        actual_ratio = validated["width"] / validated["height"]
                        if abs(actual_ratio - original_ratio) / original_ratio > 0.01:
                            raise ValueError(f"{variant} aspect ratio differs from the iNaturalist original")
                    variants.append({"variant": variant, **{
                        name: validated[name]
                        for name in ("direct_url", "mime_type", "width", "height", "expected_bytes", "content_sha256")
                    }})
                assets.append({
                    "asset_id": f"inaturalist:{photo_id}",
                    "taxon_id": taxon_id,
                    "media_type": "photo",
                    "provider": "inaturalist",
                    "provider_asset_id": str(photo_id),
                    "source_url": source_page,
                    "creator": creator,
                    "licence_code": licence,
                    "licence_url": licence_url,
                    "assessment": "Exact-taxon licensed iNaturalist default photo selected by the frozen fallback policy.",
                    "matched_taxon_id": taxon_id,
                    "matched_taxon_name": taxon.get("name"),
                    "match_rank": "species",
                    "source_revision": f"inaturalist-photo:{photo_id}",
                    "variants": variants,
                })
            except (KeyError, TypeError, ValueError) as error:
                failures.append({
                    "taxon_id": taxon_id,
                    "source_url": f"https://www.inaturalist.org/photos/{photo_id}",
                    "error": str(error),
                })
            finally:
                if progress:
                    progress(index, len(ordered_taxa), len(assets), len(failures))
        return assets, failures


def _absolute_phylopic_url(value: str) -> str:
    if value.startswith("https://"):
        return value
    if value.startswith("/"):
        return f"{PHYLOPIC_API}{value}"
    return f"{PHYLOPIC_API}/{value}"


def _phylopic_licence(value: str | None) -> str | None:
    if not value:
        return None
    normalized = value.lower().rstrip("/")
    if "/publicdomain/mark/" in normalized:
        return "pdm"
    if "/publicdomain/zero/" in normalized:
        return "cc0"
    if re.search(r"/licenses/by/\d+(?:\.\d+)*$", normalized):
        return "cc-by"
    return None


class PhyloPicResolver:
    """Freeze the most-specific exact-name PhyloPic match for each published taxon."""

    def __init__(self, client: CachedHttpClient):
        self.client = client
        self._matches: dict[str, dict | None] = {}
        self._image_index: dict[str, list[dict]] | None = None
        self._match_locks_guard = threading.Lock()
        self._match_locks: dict[str, threading.Lock] = {}

    def _json(self, url: str) -> dict:
        key = hashlib.sha256(url.encode()).hexdigest()[:20]
        return self.client.json("phylopic", key, url)

    def _image_candidate(self, image: dict, matched_name: str, retrieved_at: str) -> dict | None:
        links = image.get("_links")
        if not isinstance(links, dict):
            return None
        licence_link = links.get("license")
        licence_url = licence_link.get("href") if isinstance(licence_link, dict) else None
        licence_url = _absolute_phylopic_url(licence_url) if isinstance(licence_url, str) else None
        licence = _phylopic_licence(licence_url)
        raster_files = links.get("rasterFiles")
        if not licence or not isinstance(raster_files, list):
            return None
        raster_urls = [
            item.get("href") for item in raster_files
            if isinstance(item, dict) and isinstance(item.get("href"), str)
        ]
        uuid = image.get("uuid")
        if not raster_urls or not isinstance(uuid, str) or not uuid:
            return None
        direct_url = _absolute_phylopic_url(raster_urls[-1])
        validated = self.client.validate_image(direct_url, retrieved_at)
        variant = {
            name: validated[name]
            for name in ("direct_url", "mime_type", "width", "height", "expected_bytes", "content_sha256")
        }
        attribution = image.get("attribution")
        return {
            "provider_asset_id": uuid,
            "source_url": f"https://www.phylopic.org/images/{uuid}",
            "creator": clean_html(attribution) or None,
            "licence_code": licence,
            "licence_url": licence_url,
            "matched_taxon_name": matched_name,
            "source_revision": f"phylopic-image:{uuid}:{validated['content_sha256']}",
            "variants": [
                {"variant": "thumbnail", **variant},
                {"variant": "detail", **variant},
            ],
        }

    @staticmethod
    def _normalized_name(name: str) -> str:
        return " ".join(re.sub(r"[^a-z\s]", " ", name.lower()).split())

    @classmethod
    def _image_priority(cls, image: dict, normalized_name: str) -> tuple[int, str]:
        links = image.get("_links")
        if not isinstance(links, dict):
            return 3, str(image.get("uuid", ""))
        titles = {}
        for relation in ("self", "specificNode", "generalNode"):
            link = links.get(relation)
            title = link.get("title") if isinstance(link, dict) else None
            titles[relation] = cls._normalized_name(title) if isinstance(title, str) else ""
        priority = (
            0 if titles.get("self") == normalized_name
            else 1 if titles.get("specificNode") == normalized_name
            else 2
        )
        return priority, str(image.get("uuid", ""))

    def prepare(
        self,
        started: Callable[[int, int], None] | None = None,
        progress: Callable[[int, int, int, int], None] | None = None,
    ) -> None:
        """Build one reusable exact-name index from PhyloPic's paged image collection."""
        if self._image_index is not None:
            return
        metadata = self._json(f"{PHYLOPIC_API}/images")
        build = metadata.get("build")
        total_pages = metadata.get("totalPages")
        total_items = metadata.get("totalItems")
        if (
            not isinstance(build, int) or build <= 0
            or not isinstance(total_pages, int) or total_pages <= 0
            or not isinstance(total_items, int) or total_items < 0
        ):
            raise ValueError("PhyloPic image index metadata is incomplete")
        if started:
            started(total_pages, total_items)
        images: list[dict] = []
        for page_number in range(total_pages):
            page_url = _api_url(f"{PHYLOPIC_API}/images", {
                "build": build,
                "embed_items": "true",
                "page": page_number,
            })
            page = self._json(page_url)
            embedded = page.get("_embedded")
            items = embedded.get("items") if isinstance(embedded, dict) else None
            if not isinstance(items, list):
                raise ValueError(f"PhyloPic image index page {page_number} has no embedded items")
            images.extend(item for item in items if isinstance(item, dict))
            if progress:
                progress(page_number + 1, total_pages, len(images), 0)

        index: dict[str, list[dict]] = {}
        for image in sorted(images, key=lambda row: str(row.get("uuid", ""))):
            links = image.get("_links")
            if not isinstance(links, dict):
                continue
            node_links = links.get("nodes")
            names = {
                node.get("title")
                for node in node_links if isinstance(node_links, list) and isinstance(node, dict)
                if isinstance(node.get("title"), str)
            }
            for relation in ("self", "specificNode", "generalNode"):
                link = links.get(relation)
                if isinstance(link, dict) and isinstance(link.get("title"), str):
                    names.add(link["title"])
            for name in names:
                normalized = self._normalized_name(name)
                if normalized:
                    index.setdefault(normalized, []).append(image)
        for normalized, candidates in index.items():
            candidates.sort(key=lambda image: self._image_priority(image, normalized))
        self._image_index = index

    def _resolve_name(self, name: str, retrieved_at: str) -> dict | None:
        normalized = self._normalized_name(name)
        if not normalized:
            return None
        with self._match_locks_guard:
            match_lock = self._match_locks.setdefault(normalized, threading.Lock())
        with match_lock:
            if normalized in self._matches:
                return self._matches[normalized]
            if self._image_index is None:
                self.prepare()
            for image in self._image_index.get(normalized, []):
                try:
                    candidate = self._image_candidate(image, name, retrieved_at)
                except RemoteRequestError as error:
                    if error.retryable:
                        raise
                    continue
                except ValueError:
                    continue
                if candidate is not None:
                    self._matches[normalized] = candidate
                    return candidate
            self._matches[normalized] = None
            return None

    def _resolve_taxon(
        self,
        taxon: dict,
        retrieved_at: str,
        rank_order: tuple[str, ...],
    ) -> dict | None:
        taxon_id = taxon["taxon_id"]
        taxonomy = taxon.get("taxonomy", {})
        names = {
            "species": taxon["scientific_name"],
            "genus": taxonomy.get("genus"),
            "family": taxonomy.get("family"),
            "order": taxonomy.get("order"),
        }
        match = None
        match_rank = None
        for rank in rank_order:
            name = names[rank]
            if isinstance(name, str) and name.strip():
                match = self._resolve_name(name, retrieved_at)
                if match is not None:
                    match_rank = rank
                    break
        if match is None or match_rank is None:
            return None
        uuid = match["provider_asset_id"]
        return {
            **match,
            "asset_id": f"phylopic:{uuid}:{taxon_id}:{match_rank}",
            "taxon_id": taxon_id,
            "media_type": "silhouette",
            "provider": "phylopic",
            "assessment": (
                "Frozen exact-name PhyloPic match selected by the deterministic "
                f"{'-to-'.join(rank_order)} policy."
            ),
            "matched_taxon_id": taxon_id if match_rank == "species" else None,
            "match_rank": match_rank,
        }

    def resolve(
        self,
        taxa: list[dict],
        retrieved_at: str,
        rank_order: tuple[str, ...] = ("species", "genus", "family", "order"),
        progress: Callable[[int, int, int, int], None] | None = None,
        index_started: Callable[[int, int], None] | None = None,
        index_progress: Callable[[int, int, int, int], None] | None = None,
    ) -> tuple[list[dict], list[dict]]:
        if not rank_order or not set(rank_order).issubset({"species", "genus", "family", "order"}):
            raise ValueError(f"Invalid PhyloPic rank order: {rank_order}")
        assets: list[dict] = []
        failures: list[dict] = []
        ordered_taxa = sorted(taxa, key=lambda row: row["taxon_id"])
        if not ordered_taxa:
            return assets, failures
        try:
            self.prepare(index_started, index_progress)
        except (KeyError, TypeError, ValueError) as error:
            retryable = (
                isinstance(error, RemoteRequestError) and error.retryable
            ) or (
                not isinstance(error, RemoteRequestError)
                and "Remote request failed after retries" in str(error)
            )
            failures.append({
                "taxon_id": ordered_taxa[0]["taxon_id"] if ordered_taxa else 0,
                "source_url": f"{PHYLOPIC_API}/images",
                "error": str(error),
            })
            failures.append({
                "taxon_id": ordered_taxa[0]["taxon_id"] if ordered_taxa else 0,
                "source_url": PHYLOPIC_API,
                "error": (
                    "PhyloPic circuit opened after provider retries were exhausted; "
                    if retryable else "PhyloPic index could not be built; "
                ) + f"{len(ordered_taxa)} taxa use bundled fallback.",
            })
            if progress:
                progress(len(ordered_taxa), len(ordered_taxa), 0, len(failures))
            return assets, failures
        executor = ThreadPoolExecutor(
            max_workers=PHYLOPIC_VALIDATION_WORKERS,
            thread_name_prefix="phylopic-validation",
        )
        futures = {
            executor.submit(self._resolve_taxon, taxon, retrieved_at, rank_order): taxon
            for taxon in ordered_taxa
        }
        completed = 0
        outage = False
        outage_taxon_id = None
        try:
            for future in as_completed(futures):
                taxon = futures[future]
                taxon_id = taxon["taxon_id"]
                completed += 1
                try:
                    asset = future.result()
                    if asset is not None:
                        assets.append(asset)
                except (KeyError, TypeError, ValueError) as error:
                    failures.append({
                        "taxon_id": taxon_id,
                        "source_url": f"https://www.phylopic.org/name/{taxon['scientific_name']}",
                        "error": str(error),
                    })
                    provider_outage = (
                        isinstance(error, RemoteRequestError) and error.retryable
                    ) or (
                        not isinstance(error, RemoteRequestError)
                        and "Remote request failed after retries" in str(error)
                    )
                    if provider_outage:
                        outage = True
                        outage_taxon_id = taxon_id
                        for pending in futures:
                            pending.cancel()
                if progress:
                    progress(completed, len(ordered_taxa), len(assets), len(failures))
                if outage:
                    break
        finally:
            executor.shutdown(wait=True, cancel_futures=outage)
        if outage:
            failures.append({
                "taxon_id": outage_taxon_id,
                "source_url": "https://api.phylopic.org",
                "error": (
                    "PhyloPic circuit opened after provider retries were exhausted; "
                    f"{len(ordered_taxa) - completed} remaining taxa use bundled fallback."
                ),
            })
        assets.sort(key=lambda row: row["taxon_id"])
        return assets, failures


def description_candidate(taxon: dict, retrieved_at: str) -> dict | None:
    summary = clean_html(taxon.get("wikipedia_summary"))
    source_url = taxon.get("wikipedia_url")
    if not summary or not isinstance(source_url, str) or not source_url.startswith("https://"):
        return None
    if len(summary) > 1200:
        sentence_end = summary.rfind(". ", 0, 1200)
        summary = summary[:sentence_end + 1] if sentence_end >= 240 else summary[:1199].rstrip() + "…"
    return {
        "taxon_id": taxon["id"],
        "locale": "en",
        "summary": summary,
        "source_url": source_url,
        "attribution": "Wikipedia contributors",
        "licence_code": "cc-by-sa",
        "licence_url": "https://creativecommons.org/licenses/by-sa/4.0/",
        "retrieved_at": retrieved_at,
        "source_revision": f"inaturalist-taxon:{taxon['id']}:{taxon.get('updated_at', 'unknown')}",
    }


def conservation_candidate(taxon: dict, retrieved_at: str) -> dict | None:
    status = taxon.get("conservation_status")
    if not isinstance(status, dict):
        return None
    code = status.get("status") or status.get("status_name")
    authority = status.get("authority")
    if not isinstance(code, str) or not code.strip() or not isinstance(authority, str) or not authority.strip():
        return None
    return {
        "taxon_id": taxon["id"],
        "status": code.strip(),
        "authority": authority.strip(),
        "source_url": f"https://www.inaturalist.org/taxa/{taxon['id']}",
        "retrieved_at": retrieved_at,
        "source_revision": f"inaturalist-taxon:{taxon['id']}:{taxon.get('updated_at', 'unknown')}",
    }


def verified_variant(
    direct_url: str,
    payload_fetcher: Callable[[str], ResponseBytes],
    retrieved_at: str,
) -> dict:
    """Small testable image-verification seam used by provider integrations."""
    response = payload_fetcher(direct_url)
    mime_type, width, height = image_dimensions(response.payload)
    if response.content_type != mime_type:
        raise ValueError("Image response MIME does not match its bytes")
    return {
        "direct_url": direct_url,
        "mime_type": mime_type,
        "width": width,
        "height": height,
        "expected_bytes": len(response.payload),
        "content_sha256": hashlib.sha256(response.payload).hexdigest(),
        "validated_at": retrieved_at,
    }
