#!/usr/bin/env python3
"""Stage the immutable PB12 upload set after cleanup has completed."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", required=True, type=Path)
    parser.add_argument("--artifact-dir", required=True, type=Path)
    args = parser.parse_args()
    source = args.run_dir.resolve()
    target = args.artifact_dir.resolve()
    if not source.is_dir():
        raise SystemExit(f"run directory does not exist: {source}")
    if target == source or target.is_relative_to(source) is False:
        raise SystemExit("artifact directory must be inside run directory")
    if (source / "auth-tmp").exists():
        raise SystemExit("AUTH_TMP still exists after cleanup")
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    for path in sorted(source.rglob("*")):
        if not path.is_file() or target in path.parents:
            continue
        if path.name in {"pb12-artifact-manifest.json", "pb12-secret-scan-report.json"}:
            continue
        relative = path.relative_to(source)
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
