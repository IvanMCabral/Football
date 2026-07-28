# MANAGER - Final Semantic Cleanup Review

## Verdict

APPROVED

## 1. Baseline

- Starting commit for this closure: `43538036 Complete semantic cleanup and isolate legacy version compatibility`.
- Starting working tree: clean.
- Previous state: stable compile and full suite, 3750 tests, 0 failures, 0 errors, 8 skipped.
- Scope: residual naming cleanup only; no architectural refactor or behaviour changes.

## 2. Residual internal names corrected

Production fields, parameters and local variables with versioned names were renamed to domain language:

| Previous name | Current name |
| --- | --- |
| `TestHarnessUseCaseImpl.v24StoragePort` | `detailedMatchStoragePort` |
| `TestHarnessReplayService.v24StoragePort` | `detailedMatchStoragePort` |
| `TestHarnessFormationMatrixService.v24StoragePort` | `detailedMatchStoragePort` |
| `TestHarnessAdminCommandService.v24StoragePort` | `detailedMatchStoragePort` |
| `MatchControllerReactive.v24DetailedMatchQueryService` | `detailedMatchQueryService` |
| `RoundController.v24Event` | `detailedEvent` |
| `RoundController.v24Type` | `detailedEventType` |
| `MatchSession.v24Type` | `detailedEventType` |
| `LiveMatchEventConverter.v24Type` | `detailedEventType` |
| `LiveMatchEventConverter.v24Event` | `detailedEvent` |

Related tests and mocks were updated to use the same observable domain names. No compatibility aliases with old field names were kept.

## 3. Properties updated

Active runtime profiles now use domain names:

- `app.simulation.league.use-classic-engine`
- `app.simulation.league.detailed-enabled`
- `app.simulation.detailed.*`

Updated active resources:

- `src/main/resources/application.yaml`
- `src/main/resources/application-local.yml`
- `src/test/resources/application-test.yml`
- `src/main/resources/application-career-mutations.yml`

## 4. Deprecated property aliases

Legacy versioned properties are accepted only in configuration boundary classes so existing deployments can still boot while they migrate:

| Legacy alias | Current property | Boundary |
| --- | --- | --- |
| `app.simulation.league.use-v23-engine` | `app.simulation.league.use-classic-engine` | `SimulationConfig` |
| `app.simulation.league.use-v24-detailed-engine` | `app.simulation.league.detailed-enabled` | `SimulationConfig` |
| `app.simulation.v24.*` | `app.simulation.detailed.*` | `SimulationConfig`, `DetailedSimulationConfig` |

When a legacy alias is used and the current property is absent, the configuration class logs a warning. Current properties take priority. These aliases are isolated from the simulator core and can be removed after deployment configs have migrated.

## 5. Profile rename

The active mutation profile was renamed from version terminology to functional terminology:

- `application-v24-mutations.yml` -> `application-career-mutations.yml`
- profile name `v24-mutations` -> `career-mutations`

Operational documentation was updated to reference `local,career-mutations`.

## 6. Persisted compatibility

The stored value `engineVersion = "V24"` remains only as a persisted Redis/JSON discriminator for existing detailed match and baseline snapshots.

It is isolated in:

- `PersistedEngineVersions.PERSISTED_ENGINE_VERSION_V24`

Consumers:

- `DetailedMatchData`
- `BaselineState`
- Redis adapters that serialize/deserialize those records

Retirement condition: this value can be removed only after old Redis/JSON snapshots either expire, are migrated, or are no longer supported.

## 7. Final production V23/V24 inventory

Final search scope:

- `src/main/java`
- `src/main/resources`

Classified remaining production references:

| File | Reference | Classification |
| --- | --- | --- |
| `SimulationConfig.java` | `app.simulation.league.use-v23-engine` | Deprecated config alias only |
| `SimulationConfig.java` | `app.simulation.league.use-v24-detailed-engine` | Deprecated config alias only |
| `SimulationConfig.java` | `app.simulation.v24.*` | Deprecated config alias only |
| `DetailedSimulationConfig.java` | `app.simulation.v24.*` | Deprecated config alias only |
| `PersistedEngineVersions.java` | `PERSISTED_ENGINE_VERSION_V24 = "V24"` | Persisted compatibility discriminator |

No unclassified production V23/V24 references remain. Seed data entries such as `Goiania B`/`Goi?nia B` are football data, not version references.

## 8. Encoding

- Removed BOMs introduced during local rewrite attempts.
- Corrected active documentation headings from `MANAGER ?` to `MANAGER -`.
- Normalized active profile comments and runbook references.
- Verified files with UTF-8 reads and replacement-character checks.

## 9. Validation evidence

Commands executed during this closure:

- `mvn -q -DskipTests test-compile` - passed.
- Focused tests for test harness, match controller, round controller, match session, live event converter, config and detailed persistence - passed.
- `mvn -q test` - passed after final commit validation.
- Surefire totals after final suite: 3750 tests, 0 failures, 0 errors, 8 skipped.
- `git diff --check` - passed.
- Final production version scan - only classified compatibility references remain.
- Final working tree - clean after commit and validation.

## 10. Final decision

APPROVED. The residual version naming cleanup is complete: current code paths and active properties use domain terminology; old versioned names remain only as deprecated config aliases or persisted compatibility discriminators; the profile was renamed to functional language; encoding issues were corrected; behaviour was preserved by focused and complete test suites.
