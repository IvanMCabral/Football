# MVP1 Final Release Dataset Closure Report

Date: 2026-07-29

Verdict: `APPROVED`

## Scope

This closure covers the MVP 1 principal database dataset, import pipeline, runtime acceptance, frontend special-trait exposure, and final audit evidence.

## Principal database

Validated against `football_manager`:

| item | result |
| --- | ---: |
| countries | 3 |
| leagues | 3 |
| clubs | 70 |
| teams | 70 |
| players | 1680 |
| player special traits | 3360 |
| legacy/non-final players | 0 |
| missing source refs | 0 |
| duplicate `source_id` values | 0 |
| orphan trait rows | 0 |
| invalid trait counts | 0 |
| corrupt `?` name markers | 0 |

## Safety

- Backup before principal import: `backups/football_manager_before_mvp1_real_dataset_20260729.dump`.
- Backup before legacy cleanup: `backups/football_manager_before_mvp1_catalog_cleanup_20260729.dump`.
- Backup files are ignored by Git.
- Legacy seed writers now refuse principal DB writes unless explicitly overridden.

## Runtime

Validated:

- three-league loading;
- career creation;
- squad loading;
- lineups and fixtures in previous closure smoke;
- restart/recovery in previous closure smoke;
- detailed match persistence in previous closure smoke;
- special traits visible through the live squad API.

Latest trait runtime evidence:

`TRAITS_RUNTIME_OK league=Spanish Primera Division team=Valencia CF squad=24 withTwoTraits=24`

## Build and test evidence

Backend:

- `mvn -q -DskipTests test-compile`: passed.
- focal dataset/runtime tests: passed.
- `mvn -q test`: passed.

Frontend:

- `npm run build -- --configuration development`: passed.
- `npm run build`: passed.
- `npm test -- --watch=false --browsers=ChromeHeadless`: 1016 SUCCESS, 0 failed, 2 skipped.

## Final audit

Final independent audit:

`docs/data/MVP1_PRINCIPAL_DATABASE_FINAL_INDEPENDENT_AUDIT.md`

Final verdict:

`APPROVED`
