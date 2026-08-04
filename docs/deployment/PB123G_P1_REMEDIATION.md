# PB1.2.3G — P1 remediation evidence

## Previous reproduction

The independent audit reproduced 4-4-2 → 4-3-3 using an index-based reflow.
The selector immediately posted `POST /career/lineup/manual-select`, left a
striker in a midfield slot, and the change survived reload. Production audit
also reported seven HIGH Angular production advisories. The historical report
remains unchanged and keeps its `REJECTED` verdict.

## Implemented correction

The modal now owns a confirmed snapshot and an in-memory draft. Formation
selection, drag movement, bench swaps, and slot assignment update only the
draft. The footer confirmation writes exactly once (manual-select then
confirm); close/cancel discards the draft. Reflow uses deterministic weighted
matching with goalkeeper and line/role constraints.

The public 429 message is UTF-8 (`Intentá nuevamente más tarde.`) and has a
regression assertion. No gameplay, simulation, calendar, database, or Redis
data was changed.

## Local evidence

- Frontend focused modal suite: 135 SUCCESS.
- Frontend full suite: 1,048 SUCCESS, 0 failures, 2 skipped.
- Frontend development and production builds: PASS.
- `npm ci`: PASS.
- Production audit: 0 vulnerabilities.
- Backend rate-limit regression: PASS.
- Backend compile: PASS (`mvn -q -DskipTests test-compile`).
- Backend focused rate-limit test: PASS.
- Backend full suite: 2,585 tests, 0 failures, 0 errors, 4 skipped. The run
  used an isolated `java.io.tmpdir` on the data volume because the default
  Windows temporary volume was full; no repository or application data was
  changed.

## Commits

- Frontend `c3ac4e2` — tactical draft/reflow and tests.
- Frontend `ca90496` — Angular production dependency remediation.
- Root `2da73b14` — public 429 encoding regression assertion.

## Remaining independent checks

The public Firebase/Render smoke and exact release hashes must be refreshed
after the final commits. The prior audit's P2 operational items (cold start,
full-season evidence, responsive matrix, SSE/recovery) are not silently
reclassified by this P1 remediation.
