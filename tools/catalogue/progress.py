from __future__ import annotations

import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path


STAGE_LABELS = {
    "regional-candidates": "Regional species evidence",
    "selection-taxonomy-rarity": "Taxonomy, selection and rarity",
    "metadata": "Names, descriptions and conservation",
    "photos-silhouettes": "Reference photos and silhouettes",
    "validate-build-publish": "Build and atomic publication",
    "freshness-verification": "Final integrity verification",
}
STAGE_ORDER = tuple(STAGE_LABELS)


class NullProgressReporter:
    def stage_started(self, name: str) -> None:
        pass

    def stage_finished(self, name: str, status: str, elapsed: float) -> None:
        pass

    def task_started(self, label: str, total: int, current: int = 0) -> None:
        pass

    def task_updated(
        self,
        label: str,
        current: int,
        total: int,
        *,
        found: int | None = None,
        failed: int | None = None,
        cache_hits: int | None = None,
        network_requests: int | None = None,
        force: bool = False,
    ) -> None:
        pass

    def note(self, message: str) -> None:
        pass

    def close(self) -> None:
        pass


@dataclass
class _ActiveProgress:
    stage: str = "Starting"
    task: str | None = None
    current: int = 0
    total: int = 0
    changed_at: float = 0.0


class ConsoleProgressReporter(NullProgressReporter):
    """Line-oriented progress suitable for PowerShell, CI logs and redirected output."""

    def __init__(self, *, heartbeat_seconds: float = 15.0, stream=None):
        self.stream = stream or sys.stderr
        self.heartbeat_seconds = heartbeat_seconds
        self.started_at = time.monotonic()
        self._active = _ActiveProgress(changed_at=self.started_at)
        self._last_update_at = 0.0
        self._last_update_value = -1
        self._task_started_at = self.started_at
        self._task_started_value = 0
        self._lock = threading.Lock()
        self._closed = threading.Event()
        self._heartbeat = threading.Thread(target=self._heartbeat_loop, daemon=True)
        self._heartbeat.start()

    @staticmethod
    def _duration(seconds: float) -> str:
        value = max(0, round(seconds))
        minutes, seconds = divmod(value, 60)
        hours, minutes = divmod(minutes, 60)
        if hours:
            return f"{hours}h {minutes:02d}m {seconds:02d}s"
        if minutes:
            return f"{minutes}m {seconds:02d}s"
        return f"{seconds}s"

    def _write(self, message: str) -> None:
        with self._lock:
            print(message, file=self.stream, flush=True)

    def stage_started(self, name: str) -> None:
        label = STAGE_LABELS.get(name, name)
        with self._lock:
            self._active = _ActiveProgress(stage=label, changed_at=time.monotonic())
        prefix = f"[{STAGE_ORDER.index(name) + 1}/{len(STAGE_ORDER)}] " if name in STAGE_ORDER else ""
        self._write(f"\n==> {prefix}{label}")

    def stage_finished(self, name: str, status: str, elapsed: float) -> None:
        label = STAGE_LABELS.get(name, name)
        marker = "complete" if status == "complete" else status
        self._write(f"    {marker} ({self._duration(elapsed)})")
        with self._lock:
            self._active.task = None

    def task_started(self, label: str, total: int, current: int = 0) -> None:
        with self._lock:
            self._active.task = label
            self._active.current = current
            self._active.total = total
            self._active.changed_at = time.monotonic()
            self._last_update_at = 0.0
            self._last_update_value = -1
            self._task_started_at = time.monotonic()
            self._task_started_value = current
        self.task_updated(label, current, total, force=True)

    def task_updated(
        self,
        label: str,
        current: int,
        total: int,
        *,
        found: int | None = None,
        failed: int | None = None,
        cache_hits: int | None = None,
        network_requests: int | None = None,
        force: bool = False,
    ) -> None:
        now = time.monotonic()
        minimum_step = max(1, total // 10) if total else 1
        if not force and current < total:
            if now - self._last_update_at < 5 and current - self._last_update_value < minimum_step:
                with self._lock:
                    self._active.current = current
                    self._active.total = total
                    self._active.changed_at = now
                return
        parts = [f"    {label}: {current:,} / {total:,}" if total else f"    {label}: {current:,}"]
        if total:
            parts.append(f"({current * 100 // max(1, total)}%)")
        if found is not None:
            parts.append(f"found {found:,}")
        if failed:
            parts.append(f"issues {failed:,}")
        if cache_hits is not None or network_requests is not None:
            parts.append(f"cache {cache_hits or 0:,} | network {network_requests or 0:,}")
        elapsed = now - self._task_started_at
        completed = current - self._task_started_value
        if total and 0 < current < total and elapsed >= 5 and completed > 0:
            rate = completed / elapsed
            remaining = (total - current) / rate
            rate_text = f"{rate:.1f}/s" if rate >= 1 else f"{rate * 60:.1f}/min"
            parts.append(f"rate {rate_text} | ETA {self._duration(remaining)}")
        self._write(" | ".join(parts))
        with self._lock:
            self._active.task = label
            self._active.current = current
            self._active.total = total
            self._active.changed_at = now
            self._last_update_at = now
            self._last_update_value = current

    def note(self, message: str) -> None:
        self._write(f"    {message}")

    def _heartbeat_loop(self) -> None:
        while not self._closed.wait(self.heartbeat_seconds):
            with self._lock:
                active = _ActiveProgress(**vars(self._active))
            idle = time.monotonic() - active.changed_at
            if idle < self.heartbeat_seconds:
                continue
            suffix = (
                f" - {active.task} {active.current:,}/{active.total:,}"
                if active.task and active.total
                else f" - {active.task}" if active.task else ""
            )
            self._write(
                f"    still working ({self._duration(time.monotonic() - self.started_at)} elapsed)"
                f"{suffix}"
            )

    def summary(self, result: dict, report_path: Path | None) -> None:
        build = result.get("build") or {}
        media = result.get("media") or ({
            "photos": build.get("published_taxa_with_photos", 0),
            "specific_silhouettes": build.get("published_taxa_with_specific_silhouettes", 0),
            "family_silhouettes": build.get("published_taxa_with_family_silhouettes", 0),
        } if build else {})
        provider = result.get("provider_io") or {}
        self._write("\nCatalogue pipeline finished")
        self._write(f"    Region: {result.get('region', 'all')}")
        if build:
            self._write(
                f"    Generation: {build.get('generation_id', 'unknown')} | "
                f"{build.get('published_taxa', 0):,} published taxa"
            )
        if media:
            self._write(
                f"    Media: {media.get('photos', 0):,} photos | "
                f"{media.get('specific_silhouettes', 0):,} specific silhouettes | "
                f"{media.get('family_silhouettes', 0):,} family silhouettes"
            )
        self._write(
            f"    Provider work: {provider.get('cache_hits', 0):,} cached | "
            f"{provider.get('network_requests', 0):,} network requests"
        )
        if result.get("failures"):
            self._write(
                f"    Provider issues: {len(result['failures']):,} "
                "(details are in the report)"
            )
        if report_path is not None:
            self._write(f"    Detailed report: {report_path}")
        self._write(f"    Total time: {self._duration(time.monotonic() - self.started_at)}")

    def close(self) -> None:
        self._closed.set()
        self._heartbeat.join(timeout=1)
