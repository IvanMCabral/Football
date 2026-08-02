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
- A SHA-256 artifact manifest is generated and uploaded.
- A negative hygiene test proves a simulated auth-temp deletion failure cannot produce PASS.

## Local validation

- `bash -n tools/run-pb12-docker-smoke.sh`: PASS.
- Workflow YAML parse: PASS.
- Backend test compile: PASS.
- Backend full suite: `2,572 tests`, `0 failures`, `0 errors`, `4 skipped`.
- Frontend encoding guard, development build, production build and artifact inspection: PASS.
- Frontend ChromeHeadless: `1,029 SUCCESS`, `0 failures`, `2 skipped`.
- Docker is unavailable on the workstation; no local image result is claimed.

## Remote closure gate

The final remote workflow must run on the HEAD containing these changes. Closure requires image build, lifecycle, auth/career, Flyway, graceful stop, cleanup, final artifact scan, manifest verification and `CRITICAL=0` / `HIGH=0`. The historical run #22 is not reused as final evidence because it predates this remediation.

## Cloud boundary

Cloud Run/Firebase, managed PostgreSQL/Redis, TLS, backup/restore and deployment remain PB1.2.3 and are not performed here.
