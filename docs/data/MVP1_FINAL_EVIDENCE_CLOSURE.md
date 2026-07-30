# MVP 1 Final Evidence Closure

Date: 2026-07-30

## Purpose

This document consolidates the final evidence added after the definitive audit originally rejected MVP 1 closure. The closure covers UTF-8 UI integrity, importer idempotence, rollback safety, real backend restart recovery, database integrity and final validation.

## Commits created in this closure

### Root repository

| Commit | Purpose |
| --- | --- |
| `772ab656 Complete roster import idempotence matrix` | Adds executable import idempotence coverage and fixes stale team-squad relations during re-import. |
| `859068da Complete roster import rollback matrix` | Documents and validates rollback safety for representative validation failures. |
| `517428e2 Prove backend process restart recovery` | Records real backend PID restart and persisted career recovery evidence. |
| `Close MVP 1 final evidence remediation` | Adds this final evidence package and re-audit. |

### Frontend repository

| Commit | Purpose |
| --- | --- |
| `e786b44 Remove player UI mojibake` | Corrects corrupt visible player/squad text and adds the visible-text encoding guard. |
| `544f9aa Enforce UTF-8 player trait rendering` | Strengthens trait rendering tests with correct accents and backend code isolation. |
| `6fc04e2 Complete player trait browser acceptance` | Adds three-league browser smoke evidence for squad/player trait rendering. |

## Principal database evidence

Validated against the principal `football_manager` database loaded through the real `.env` runtime path without printing secrets.

| Check | Result |
| --- | ---: |
| Countries | 3 |
| Leagues | 3 |
| Clubs | 70 |
| Teams | 70 |
| Players | 1680 |
| Player trait rows | 3360 |
| Players missing source identifiers | 0 |
| Orphan trait rows | 0 |
| Duplicate player source identifiers | 0 |
| Players without exactly two traits | 0 |
| Player names or display names with literal question marks | 0 |
| Clubs with fewer than two goalkeepers | 0 |

## Backend restart and recovery

| Evidence | Result |
| --- | --- |
| Pre-restart artifact | `D:\temp\mvp1-runtime-before-restart.json` |
| Career before restart | `010f32eb-36cf-4993-91e8-344d09dadcce` |
| Old backend listener PID | `25364` |
| Old PID stopped | yes |
| New backend listener PID | `26580` |
| PID changed | yes |
| Post-restart artifact | `D:\temp\mvp1-runtime-after-restart.json` |
| Same career recovered | yes |
| Lineup recovered | 11 players / 11 slots |
| Same first fixture recovered | yes |
| Standings recovered | 20 rows |

## Import safety evidence

The importer now validates source references, accepted player positions and the exactly-two-traits invariant starting from the `players` table. This catches zero-trait players as well as one-trait and over-trait cases. Re-import also removes stale team-squad relations before inserting the canonical relation for each source player.

Focused importer validation:

```text
mvn -q "-Dtest=ThreeLeagueDatasetImporterTest" test
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

## Full validation

| Validation | Result |
| --- | --- |
| Backend full suite | 2453 tests, 0 failures, 0 errors, 4 skipped |
| Frontend development build | passed |
| Frontend production build | passed |
| Frontend full suite | 1022 success, 0 failures, 2 skipped |
| Frontend visible-text encoding guard | passed, 381 files scanned |
| Three-league browser smoke | Spain, Argentina and Brazil passed |
| Root `git diff --check` | clean after final commit validation |
| Frontend `git diff --check` | clean after final commit validation |

## Verdict

All known critical and important findings from the definitive final audit are resolved with code, tests, runtime evidence and documentation. The MVP 1 dataset/runtime/UI closure is ready for the final re-audit verdict.
