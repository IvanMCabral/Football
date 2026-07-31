# PB1 Secret Rotation and Repository Hygiene

Date: 2026-07-31

## Result

Repository credential defaults were removed from active test configuration, tests, runbooks and Redis helper scripts. Previous secret values are not repeated in this report.

## Files corrected

- `src/test/resources/application-test.yml`: DB and Redis passwords now require environment variables.
- `src/test/java/com/footballmanager/application/service/world/importer/ThreeLeagueDatasetImporterTest.java`: DB password now requires an environment variable.
- `src/test/java/com/footballmanager/infrastructure/persistence/Mvp1DatabaseBaselineContractTest.java`: DB password now requires an environment variable.
- `src/test/java/com/footballmanager/application/service/simulation/V25D81InjuryPersistenceDiagnosticTest.java`: command comments no longer contain literal credentials.
- `MANAGER_TEAM_RUNBOOK.md`: literal local credentials replaced by `.env`/placeholder instructions.
- `restart-redis.ps1`: literal Redis credential comments removed.

## Rotation policy

Any credential that was previously committed must be considered compromised. Rotate local PostgreSQL, Redis and JWT secrets before reusing them outside this developer workstation. Production must use provider-generated secrets only.

## History

Known local secret patterns were searched in the working tree without printing previous values. No history rewrite was performed in this task; rotation is the required mitigation before Internet exposure.
