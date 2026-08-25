from __future__ import annotations

import argparse
import json
from pathlib import Path

from .pipeline import CataloguePipeline
from .progress import ConsoleProgressReporter
from tools.reference_media_rejections import import_export


ROOT = Path(__file__).resolve().parents[2]


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="python -m tools.catalogue",
        description="Unattended Wildlife catalogue authoring, validation and publication pipeline.",
    )
    parser.add_argument(
        "command",
        choices=("run", "build", "verify", "import-rejections"),
        nargs="?",
        default="run",
    )
    parser.add_argument("--offline", action="store_true", help="Use only frozen provider evidence.")
    parser.add_argument("--refresh", action="store_true", help="Replace cached provider evidence.")
    parser.add_argument("--release", action="store_true", help="Require and publish frozen release content.")
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print the complete machine-readable result after the human progress display.",
    )
    parser.add_argument(
        "--region",
        help="Refresh one canonical region key; the final pack is still validated and published as a whole.",
    )
    parser.add_argument("--input", type=Path, help="Wildlife local-data export to import.")
    args = parser.parse_args()
    if args.offline and args.refresh:
        parser.error("--offline and --refresh cannot be combined")
    if args.command == "import-rejections":
        if args.input is None:
            parser.error("import-rejections requires --input")
        result = import_export(
            args.input.resolve(),
            ROOT / "catalogues" / "media_rejections.yaml",
        )
        if args.json:
            print(json.dumps(result, indent=2, sort_keys=True))
        else:
            print(
                f"Imported {result['imported']} new reference-image rejections "
                f"({result['total']} total)."
            )
            print(f"Updated: {result['destination']}")
            print("Run the catalogue pipeline to select and publish replacement images.")
        return
    if args.input is not None:
        parser.error("--input applies only to import-rejections")
    reporter = ConsoleProgressReporter()
    pipeline = CataloguePipeline(ROOT, offline=args.offline, refresh=args.refresh, progress=reporter)
    try:
        if args.command == "run":
            result = pipeline.run(release=args.release, region_key=args.region)
        elif args.command == "build":
            if args.region:
                parser.error("--region applies only to the run command")
            result = pipeline._stage("validate-build-publish", lambda: pipeline.build(release=args.release))
            result = {"region": "all", "build": result, "provider_io": pipeline.client.statistics()}
        else:
            if args.region:
                parser.error("--region applies only to the run command")
            verification = pipeline._stage("freshness-verification", pipeline.verify)
            result = {"region": "all", "verification": verification, "provider_io": pipeline.client.statistics()}
        if args.json:
            print(json.dumps(result, indent=2, sort_keys=True))
        else:
            report_path = (
                ROOT / "catalogues" / "review" / "catalogue_pipeline" / "last-run.json"
                if args.command == "run" else None
            )
            reporter.summary(result, report_path)
    except KeyboardInterrupt:
        reporter.note("Interrupted safely. Cached provider results were retained; rerun the same command to resume.")
        raise SystemExit(130)
    except Exception as error:
        reporter.note(f"Pipeline stopped: {error}")
        reporter.note("The previous verified catalogue remains active.")
        raise
    finally:
        reporter.close()
