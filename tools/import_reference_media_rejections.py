from __future__ import annotations

import argparse
from pathlib import Path

from tools.reference_media_rejections import import_export


ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Import an owner-supplied Wildlife local-data export into the photo denylist.",
    )
    parser.add_argument("export", type=Path, help="Path to wildlife-local-data-*.json")
    args = parser.parse_args()
    result = import_export(
        args.export.resolve(),
        ROOT / "catalogues" / "media_rejections.yaml",
    )
    print(
        f"Imported {result['imported']} new reference-image rejections "
        f"({result['total']} total)."
    )
    print(f"Updated: {result['destination']}")


if __name__ == "__main__":
    main()
