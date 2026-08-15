# PB1.2.3H7.9F.1 — Operational runner remediation test report

## Review lineage

The independent review remains historically `PB1.2.3H7.9F.1 OPERATIONAL
RUNNER REJECTED` with four P1 findings. This report records the subsequent local
remediation; it does not self-promote the runner to independent approval.

## Fresh results

| Group | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Runner, immutable authority, canary context, activation matrix | 58 | 0 | 0 | 0 |
| Real normal application context | 1 | 0 | 0 | 0 |
| Migration/reference/physical capacity supporting suites | 55 | 0 | 0 | 0 |
| `WorldStorageV2NegativeControlsTest` | 34 | 0 | 0 | 0 |
| **Total** | **148** | **0** | **0** | **0** |

`mvn -q -DskipTests test-compile` completed successfully. All contexts used the
isolated `test` runtime. No public provider or production credential was used.

## Required matrix

The focused suite independently covers disabled/inert activation, read-only
default, exact execute and confirmation, case/whitespace attacks, immutable
owner/source/plan/fingerprint authority, capacity floors/ceilings and exact
boundary, malformed multi-owner input, source-before-capacity ordering, zero
orchestration on rejection, one call on success/partial/error, no retry,
second-call rejection, cold-publisher re-subscription rejection, exit mapping,
raw-owner log exclusion, and provider-secret log exclusion.

The real normal application context proves the runner, authority, source probe,
and Management API provider are absent. The activation matrix proves all four
negative profile/property combinations inert. The supporting integration suite
retains source-to-write CAS evidence, including stale source checksum rejection
without commit.

## Adversarial self-attack

| Attack | Result |
|---|---|
| A — owner B plus matching runtime hash | Failed before source; immutable owner authority retained |
| B — source B plus matching runtime echo | Failed before source/runtime acceptance |
| C — fingerprint B plus matching runtime echo | Failed before source/runtime acceptance |
| D — required headroom `0` | Effective floor remains `2436344` |
| E — cushion `0` | Effective floor remains `262144` |
| F — quota above certified | Effective ceiling remains `268435456` |
| G — threshold above certified | Effective ceiling remains `265736968` |
| H — mode `execute` | Not armed; zero orchestration |
| I — mode ` EXECUTE` | Not armed; zero orchestration |
| J — two calls | Second is `RUNNER_ALREADY_INVOKED` |
| K — two subscriptions | Second is `RUNNER_ALREADY_INVOKED`; one orchestration maximum |
| L — source changes after probe | Existing product CAS rejects stale checksum without commit |

## Production activity

Render deploys, public runner invocations, public Redis writes/deletes, public
PostgreSQL writes, catalog writes, cleanup, credential changes, billing changes,
and gameplay changes: all zero.

`PUBLIC CANARY EXECUTION AUTHORIZED = NO`.
