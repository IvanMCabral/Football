# PB1.2.2 Docker Runtime P1 Remediation Report

Date: 2026-08-02

## Scope

This report closes the P1 backlog from `PB12_DOCKER_RUNTIME_FINAL_INDEPENDENT_AUDIT.md` without changing gameplay, simulation, datasets or functional contracts.

## Remediations implemented

- Spring Boot parent upgraded from `3.5.14` to `3.5.16`.
- BOM-managed Spring Data Commons upgraded from `3.5.11` to `3.5.13`.
- BOM-managed Spring Expression upgraded from `6.2.18` to `6.2.19`.
- BOM-managed Spring WebFlux upgraded from `6.2.18` to `6.2.19`.
- Docker build and runtime base images pinned to verified linux/amd64 digests.
- GitHub Actions checkout, SBOM, Trivy and artifact actions pinned to full commit SHAs with release comments.
- `AUTH_TMP` moved below the run workspace, restricted to mode 700, removed during cleanup and verified absent.
- Cleanup now fails the result if temporary auth data remains or force cleanup is needed.
- A final secret scan runs after final container logs and cleanup, before artifact upload.
- The result JSON records `authTempExists`, `authTempCleanupVerified`, `finalArtifactSecretScanPassed` and `secretLeaksDetected`.
- The artifact is staged into one immutable directory; the versioned Python builder performs a scan for every source file, derives `containsSecrets` from `matches == 0 && passed == true`, and emits a per-file scan report.
- The versioned verifier checks exact inventory, paths, sizes, SHA-256 values, scan evidence, manifest metadata and post-generation additions before upload.
- The manifest and scan report are scanned again as metadata files. The manifest self-hash uses a canonical representation with its own hash field set to `null`, avoiding a circular digest.
- A negative hygiene test proves a simulated auth-temp deletion failure cannot produce PASS.
- Nine executable negative manifest tests cover JWT, bearer, Redis password, omitted files, altered SHA/size, hardcoded `containsSecrets`, post-manifest additions and metadata tampering.

## Local validation

- `bash -n tools/run-pb12-docker-smoke.sh`: PASS.
- Workflow YAML parse: PASS.
- Backend test compile: PASS.
- Backend full suite before this manifest-only change: `2,572 tests`, `0 failures`, `0 errors`, `4 skipped` (summed from Surefire text reports; XML parameterized containers are not double-counted).
- Frontend encoding guard, development build, production build and artifact inspection: PASS.
- Frontend ChromeHeadless: `1,029 SUCCESS`, `0 failures`, `2 skipped`.
- Docker is unavailable on the workstation; no local image result is claimed.

## Previous remote evidence

Run [#24](https://github.com/IvanMCabral/Football/actions/runs/30769400337) is historical evidence from HEAD `87e28c8f`; it passed image build, lifecycle, auth/career, Flyway, graceful stop, cleanup, final artifact scan and `CRITICAL=0` / `HIGH=0`. Its artifact digest is `sha256:70d557c0309a5f0bc64daa8c71ba160f843cceb21d99e7cc7dfd76afd0947bd0`. It is not the final evidence for the current HEAD.

The current remediation requires a new workflow run after the final commit. The artifact ZIP from #24 could not be downloaded locally because the unauthenticated GitHub API returned HTTP 401; no local hash claim is made for that historical artifact.

## Cloud boundary

Cloud Run/Firebase, managed PostgreSQL/Redis, TLS, backup/restore and deployment remain PB1.2.3 and are not performed here.
