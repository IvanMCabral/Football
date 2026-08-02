#!/usr/bin/env python3
"""Verify PB12 artifact bytes, inventory and metadata scans fail closed."""

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
    text = path.read_text(encoding="utf-8", errors="replace")
    matches = sum(len(pattern.findall(text)) for pattern in SECRET_PATTERNS)
    return {"scanned": True, "matches": matches, "passed": matches == 0}


def canonical_manifest(manifest: dict[str, Any]) -> bytes:
    copy = json.loads(json.dumps(manifest))
    copy.setdefault("metadataFiles", {}).setdefault("manifest", {})["sha256"] = None
    return (json.dumps(copy, indent=2, sort_keys=False) + "\n").encode("utf-8")


def fail(message: str) -> None:
    raise SystemExit(f"PB12 manifest verification failed: {message}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--scan-report", required=True, type=Path)
    parser.add_argument("--verification-output", type=Path)
    args = parser.parse_args()
    root = args.artifact_dir.resolve()
    manifest_path = args.manifest.resolve()
    report_path = args.scan_report.resolve()
    if not root.is_dir() or not manifest_path.is_file() or not report_path.is_file():
        fail("artifact directory or metadata file missing")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        report = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"invalid metadata JSON: {exc}")

    entries = manifest.get("files")
    metadata = manifest.get("metadataFiles")
    if not isinstance(entries, list) or not isinstance(metadata, dict):
        fail("manifest schema is incomplete")
    entry_by_path = {entry.get("path"): entry for entry in entries if isinstance(entry, dict)}
    if len(entry_by_path) != len(entries):
        fail("duplicate or malformed file entries")
    actual_source_paths = sorted(
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file() and path.resolve() not in {manifest_path, report_path}
    )
    if sorted(entry_by_path) != actual_source_paths:
        fail("artifact inventory is not exact")

    report_by_path = {item.get("path"): item for item in report.get("files", []) if isinstance(item, dict)}
    if sorted(report_by_path) != actual_source_paths:
        fail("scan report inventory is not exact")
    if int(report.get("filesUnscanned", -1)) != 0:
        fail("scan report contains unscanned files")

    for rel in actual_source_paths:
        path = root / rel
        entry = entry_by_path[rel]
        result = scan(path)
        expected_scan = entry.get("secretScan", {})
        matches = int(result["matches"])
        passed = bool(result["passed"])
        if expected_scan.get("scanned") is not True or int(expected_scan.get("matches", -1)) != matches or expected_scan.get("passed") is not passed:
            fail(f"secret scan evidence mismatch for {rel}")
        if entry.get("containsSecrets") is not (matches != 0 or not passed):
            fail(f"containsSecrets mismatch for {rel}")
        if entry.get("sizeBytes") != path.stat().st_size:
            fail(f"size mismatch for {rel}")
        if entry.get("sha256") != sha256(path):
            fail(f"sha256 mismatch for {rel}")
        report_entry = report_by_path[rel]
        if report_entry.get("scanned") is not True or int(report_entry.get("matches", -1)) != matches or report_entry.get("passed") is not passed:
            fail(f"scan report mismatch for {rel}")
        if matches:
            fail(f"secret matches found in {rel}")

    manifest_meta = metadata.get("manifest")
    report_meta = metadata.get("secretScanReport")
    if not isinstance(manifest_meta, dict) or not isinstance(report_meta, dict):
        fail("metadataFiles entries are missing")
    if manifest_meta.get("path") != manifest_path.relative_to(root).as_posix() or manifest_meta.get("selfHashExcludedFromCanonicalInput") is not True:
        fail("manifest metadata path or self-hash policy invalid")
    canonical_hash = hashlib.sha256(canonical_manifest(manifest)).hexdigest()
    if manifest_meta.get("sha256") != canonical_hash:
        fail("manifest canonical hash mismatch")
    manifest_scan = scan(manifest_path)
    if manifest_meta.get("secretScanPassed") is not manifest_scan["passed"] or int(manifest_meta.get("matches", -1)) != manifest_scan["matches"] or not manifest_scan["passed"]:
        fail("manifest final secret scan failed")
    if report_meta.get("path") != report_path.relative_to(root).as_posix():
        fail("scan report metadata path invalid")
    if report_meta.get("sha256") != sha256(report_path):
        fail("scan report hash mismatch")
    report_scan = scan(report_path)
    if report_meta.get("secretScanPassed") is not report_scan["passed"] or int(report_meta.get("matches", -1)) != report_scan["matches"] or not report_scan["passed"]:
        fail("scan report final secret scan failed")
    if manifest.get("containsSecrets") is not all(not entry["containsSecrets"] for entry in entries):
        fail("aggregate containsSecrets mismatch")

    result = {
        "artifactManifestVerified": True,
        "artifactInventoryExact": True,
        "artifactFilesUnscanned": 0,
        "artifactFilesWithSecretMatches": 0,
        "manifestMetadataVerified": True,
        "filesInventoried": len(entries),
        "filesScanned": len(entries),
    }
    if args.verification_output:
        args.verification_output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
