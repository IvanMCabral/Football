# PB1.2.2 Docker Runtime P1 Final Review

Date: 2026-08-02

## Current status

PB1.2.2 P1 REMEDIATION REQUIRES FINAL EXACT-HEAD RUN

The previous workflow run [#24](https://github.com/IvanMCabral/Football/actions/runs/30769400337) executed on HEAD `87e28c8f` and is retained as historical evidence. The current baseline before this remediation is `3e98abf0`; no new run is declared until the final manifest changes are pushed and executed on that exact SHA.

## Final evidence

- workflow head SHA equality is a required gate for the new final run;
- all three Spring advisories absent from Trivy;
- `authTempExists=false` and `authTempCleanupVerified=true`;
- `finalArtifactSecretScanPassed=true` and `secretLeaksDetected=0`;
- the new manifest builder derives every trust field from a per-file scan;
- the new verifier fails closed on missing/extra files, size/SHA drift, scan mismatches and metadata tampering;
- image is non-root, Java is PID 1 and health/readiness/lifecycle gates pass;
- `docker stop` is graceful and `docker kill` is false;
- no residual containers or networks;
- `CRITICAL=0` and `HIGH=0`.

## Local evidence

Backend baseline: 2,572 tests, 0 failures, 0 errors, 4 skipped (Surefire text-report sum). Frontend: 1,029 successes, 0 failures, 2 skipped. Docker is unavailable locally. A fresh suite is required after these tooling changes.

Artifact metadata from #24 is public and records `pb12-docker-smoke-30769400337`, 88.9 KB, digest `sha256:70d557c0309a5f0bc64daa8c71ba160f843cceb21d99e7cc7dfd76afd0947bd0`. The historical ZIP was not downloaded locally because GitHub returned HTTP 401 without credentials.

## Historical preservation

The independent audit remains unchanged with its historical verdict `PB1.2.2 DOCKER RUNTIME APPROVED WITH ISSUES`. This review is the remediation follow-up and does not rewrite that history.
