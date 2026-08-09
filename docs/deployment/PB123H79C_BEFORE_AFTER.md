# PB1.2.3H7.9C — Before / after

## Backend and runtime identity

- Runtime branch: `feat/v25d99.20.3.1-runtime-fixes`.
- Code commits: `7010074f`, then `8d9e91ed`.
- Expected deployed commit: `8d9e91ed` (Render auto-deploy observed healthy;
  provider UI SHA was not exposed in this run).
- Frontend unchanged: `8f36ca7b66ff5519722cbd1e7a7675d430eb6f18`.

## Public N=20 measurements (milliseconds)

| Operation | H7.9B baseline p50 / p95 | H7.9C warm p50 / p95 | Result |
|---|---:|---:|---|
| Dashboard user stats | 1163 / 1952.2 | 209 / 261 | PASS |
| Auto-select (isolated warm sequence) | 2718.6 / 3183.3 | 790 / 1230 | PASS |
| Confirm (after one auto-select, no mutation between confirms) | 2538.7 / 2590.5 | 388 / 495 | PASS |

All samples returned HTTP 200. An interleaved auto-select/confirm run is
intentionally not used for the gate because each auto-select marks the cached
career dirty and must persist its mutation before the following confirmation.

## Validation

- Readiness: three consecutive `200`, `database=UP`, `redis=UP`.
- Liveness: available and healthy during the run.
- Backend: 2632 tests, 0 failures, 0 errors, 4 skipped.
- `git diff --check`: clean for both code commits.
