#!/usr/bin/env python3
"""Build a fail-closed inventory and per-file secret-scan report.

The manifest deliberately excludes its own hash from the file list.  Its
metadata hash is a canonical hash with the self-hash field set to null; this
avoids a circular digest while still making tampering detectable.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any


SECRET_PATTERNS = (
    re.compile(r"Authorization\s*:\s*Bearer\s+[A-Za-z0-9._~-]{20,}", re.IGNORECASE),
    re.compile(r"\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
    re.compile(r"\b(?:JWT_SECRET|REDIS_PASSWORD|DB_PASSWORD)\s*[=:]\s*[^\s\"']+", re.IGNORECASE),
    re.compile(r"\b(?:postgres(?:ql)?|redis)://[^\s:@]+:[^\s@]+@", re.IGNORECASE),
    re.compile(r"(^|[/\\])(?:\.env|[^/\\]*\.cookie)(?:$|[/\\])", re.IGNORECASE),
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def scan(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        return {"scanned": False, "matches": 1, "passed": False, "error": str(exc)}
    matches = sum(len(pattern.findall(text)) for pattern in SECRET_PATTERNS)
    return {"scanned": True, "matches": matches, "passed": matches == 0}


def relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def canonical_manifest(manifest: dict[str, Any]) -> bytes:
    copy = json.loads(json.dumps(manifest))
    copy.setdefault("metadataFiles", {}).setdefault("manifest", {})["sha256"] = None
    return (json.dumps(copy, indent=2, sort_keys=False) + "\n").encode("utf-8")


def serialize_manifest(manifest: dict[str, Any]) -> bytes:
    return (json.dumps(manifest, indent=2, sort_keys=False) + "\n").encode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--scan-report", required=True, type=Path)
    args = parser.parse_args()
    root = args.artifact_dir.resolve()
    manifest = args.manifest.resolve()
    report = args.scan_report.resolve()
    if not root.is_dir():
        raise SystemExit(f"artifact directory does not exist: {root}")
    if manifest.parent != root or report.parent != root:
        raise SystemExit("manifest and scan report must be direct artifact children")

    source_files = sorted(
        path for path in root.rglob("*")
        if path.is_file() and path.resolve() not in {manifest, report}
    )
    scans: list[dict[str, Any]] = []
    entries: list[dict[str, Any]] = []
    for path in source_files:
        scan_result = scan(path)
        rel = relative(path, root)
        scans.append({"path": rel, **scan_result})
        matches = int(scan_result.get("matches", 0))
        passed = bool(scan_result.get("passed", False))
        entries.append(
            {
                "path": rel,
                "sizeBytes": path.stat().st_size,
                "sha256": sha256(path),
                "type": path.suffix.lstrip(".") or "text",
                "secretScan": {
                    "scanned": bool(scan_result.get("scanned", False)),
                    "matches": matches,
                    "passed": passed,
                },
                "containsSecrets": matches != 0 or not passed,
            }
        )

    scan_report_data: dict[str, Any] = {
        "schemaVersion": 1,
        "scope": "artifact source files before metadata generation",
        "files": scans,
        "filesUnscanned": sum(not bool(item.get("scanned")) for item in scans),
        "filesWithSecretMatches": sum(int(item.get("matches", 0)) > 0 for item in scans),
    }
    report.write_text(json.dumps(scan_report_data, indent=2) + "\n", encoding="utf-8")
    report_scan = scan(report)
    report_entry = {
        "path": relative(report, root),
        "sha256": sha256(report),
        "secretScanPassed": bool(report_scan["passed"]),
        "matches": int(report_scan["matches"]),
    }

    manifest_data: dict[str, Any] = {
        "schemaVersion": 1,
        "inventoryPolicy": "files lists source files; metadataFiles lists the manifest and scan report separately",
        "selfHashPolicy": "metadataFiles.manifest.sha256 hashes the canonical manifest with that field set to null",
        "files": entries,
        "metadataFiles": {
            "manifest": {
                "path": relative(manifest, root),
                "sha256": None,
                "secretScanPassed": False,
                "matches": 0,
                "selfHashExcludedFromCanonicalInput": True,
            },
            "secretScanReport": report_entry,
        },
        "containsSecrets": all(not item["containsSecrets"] for item in entries),
    }
    manifest.write_bytes(serialize_manifest(manifest_data))
    manifest_scan = scan(manifest)
    manifest_data["metadataFiles"]["manifest"]["secretScanPassed"] = bool(manifest_scan["passed"])
    manifest_data["metadataFiles"]["manifest"]["matches"] = int(manifest_scan["matches"])
    manifest_data["metadataFiles"]["manifest"]["sha256"] = hashlib.sha256(canonical_manifest(manifest_data)).hexdigest()
    manifest.write_bytes(serialize_manifest(manifest_data))

    # The metadata values are intentionally reported after the final writes;
    # the verifier is the authority for the final hashes and exact inventory.
    print(json.dumps({
        "files": len(entries),
        "filesUnscanned": scan_report_data["filesUnscanned"],
        "filesWithSecretMatches": scan_report_data["filesWithSecretMatches"],
        "manifestSecretScanPassed": manifest_scan["passed"],
        "scanReportSecretScanPassed": report_scan["passed"],
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
