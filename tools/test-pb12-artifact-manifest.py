#!/usr/bin/env python3
"""Executable negative tests for the PB12 artifact manifest contract."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "tools" / "build-pb12-artifact-manifest.py"
VERIFY = ROOT / "tools" / "verify-pb12-artifact-manifest.py"


def invoke(script: Path, directory: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(script),
            "--artifact-dir",
            str(directory),
            "--manifest",
            str(directory / "pb12-artifact-manifest.json"),
            "--scan-report",
            str(directory / "pb12-secret-scan-report.json"),
        ],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )


def prepare(content: str = "safe evidence\n") -> tuple[Path, Path, Path]:
    directory = Path(tempfile.mkdtemp(prefix="pb12-manifest-test-"))
    (directory / "evidence.txt").write_text(content, encoding="utf-8")
    build = invoke(BUILD, directory)
    if build.returncode != 0:
        raise AssertionError(build.stderr or build.stdout)
    return directory, directory / "pb12-artifact-manifest.json", directory / "pb12-secret-scan-report.json"


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def save(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def expect_failure(label: str, directory: Path) -> None:
    result = invoke(VERIFY, directory)
    if result.returncode == 0:
        raise AssertionError(f"{label}: verifier unexpectedly passed")


def main() -> int:
    cases = {
        "JWT": "token eyJ" + "A" * 24 + "." + "B" * 12 + "." + "C" * 12,
        "Authorization Bearer": "Authorization: Bearer " + "A" * 24,
        "Redis password": "REDIS_PASSWORD=ephemeral-secret-value",
    }
    for label, content in cases.items():
        directory, _, _ = prepare(content)
        expect_failure(label, directory)

    directory, manifest, report = prepare()
    data = load(manifest)
    data["files"] = []
    save(manifest, data)
    expect_failure("file omitted from manifest", directory)

    directory, manifest, _ = prepare()
    data = load(manifest)
    data["files"][0]["sha256"] = "0" * 64
    save(manifest, data)
    expect_failure("SHA altered", directory)

    directory, manifest, _ = prepare()
    data = load(manifest)
    data["files"][0]["sizeBytes"] += 1
    save(manifest, data)
    expect_failure("size altered", directory)

    directory, manifest, report = prepare("Authorization: Bearer " + "A" * 24)
    data = load(manifest)
    data["files"][0]["secretScan"] = {"scanned": True, "matches": 0, "passed": True}
    data["files"][0]["containsSecrets"] = False
    save(manifest, data)
    expect_failure("containsSecrets false with matches", directory)

    directory, manifest, _ = prepare()
    (directory / "added-after-manifest.txt").write_text("late file\n", encoding="utf-8")
    expect_failure("file added after manifest", directory)

    directory, manifest, _ = prepare()
    data = load(manifest)
    data["containsSecrets"] = False
    save(manifest, data)
    expect_failure("manifest altered", directory)

    directory, _, report = prepare()
    data = load(report)
    data["files"][0]["matches"] = 1
    save(report, data)
    expect_failure("secret scan report altered", directory)

    print("PB12 artifact manifest negative tests passed: 10 cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
