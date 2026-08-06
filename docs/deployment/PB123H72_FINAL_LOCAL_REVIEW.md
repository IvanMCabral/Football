# PB1.2.3H7.2 — Final local review

## Verification status

**PB1.2.3H7.2 LOCAL REMEDIATION COMPLETE WITH ISSUES**

The remediation implements immutable career ownership mappings, bounded and
validated owner indexes, fail-closed short-count handling, real ephemeral
Redis integration coverage, bounded retention validation, explicit Java 21
`jdk.random` test-module configuration, and concurrent owner-isolation tests.

Standard `mvn -q test` completed with 263 report files, 2,609 tests, 0
failures, 0 errors and 4 skipped. `mvn -q -DskipTests test-compile` is green.
The focused H7.2 set and three repeated `ShotCoordinateAttachmentTest` runs
are green.

## Non-goals and remaining external gates

- no Upstash cleanup or inspection;
- no public account creation, N=10, Render deploy, or Firebase action;
- no remote backup/restore drill;
- no frontend changes;
- no gameplay or simulation changes.

Any remaining provider, public SSE, or deployment evidence is outside this
local H7.2 remediation and must remain explicitly classified rather than
reported as green.

## Remaining issues

- No remote Redis, Upstash, Render, PostgreSQL or public account was touched.
- A real shortfall race is simulated deterministically in unit tests; the real
  Redis integration covers ownership, counters, batching, TTL and DBSIZE.
- Canonical world reconstruction remains a separate source/database contract;
  this test proves expiry does not delete the career root.
- Public N=10, SSE certification and provider restore remain outside H7.2.
