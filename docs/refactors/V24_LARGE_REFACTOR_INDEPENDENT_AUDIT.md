# V24 Large Refactor Independent Audit

## Verdict

APPROVED

## Audit date

2026-07-28

## Evidence summary

- `mvn -q -DskipTests test-compile`: passed.
- `mvn -q "-Dtest=*V24*,*Lineup*,*Harness*,*Lifecycle*,*Discipline*,*League*,*Controller*" test -DfailIfNoTests=false`: passed.
- `mvn -q test`: passed.
- Final Surefire aggregation: `tests=2433 failures=0 errors=0 skipped=4 reports=258`.

## Critical findings resolved

### Ports decoupled from web/adapters

- Auth inbound port no longer imports web DTOs. It now uses application/domain-facing records:
  - `AuthLoginCommand`
  - `AuthRegisterCommand`
  - `AuthRefreshCommand`
  - `AuthTokenResult`
  - `AuthUserInfo`
- Lineup inbound ports no longer return web DTOs. They now return `LineupView`, `LineupPlayerView` and `LineupWarning`.
- Tournament query port no longer exposes game controller DTOs. It now returns `TournamentStatus`, `TournamentStanding` and `TournamentChampion`.
- World snapshot access from application no longer depends on `RedisWorldRepository`; application depends on `domain.ports.out.world.WorldSnapshotRepository`, implemented by the Redis adapter.
- League-team synchronization no longer depends on concrete Redis/R2DBC repositories from application logic; repository access is through output ports.
- Editor formation and subdivision services no longer expose web DTOs. They use application records (`FormationDefinition`, `FormationPosition`, `FieldSubdivision`) and map to DTOs in controllers.
- Tactical change services no longer expose web DTO result/slot types. They use application records and controller-level mapping.

Audit command:

```text
rg -n "adapters\.in\.web|infrastructure\.persistence|adapters\.out" src/main/java/com/footballmanager/domain src/main/java/com/footballmanager/application
```

Result: no production dependency violations found; only a documentation string mentioning `adapters.in.web` remains inside `FormationEffectiveness`.

### Hexagonal boundaries

- Domain services are not Spring components. Wiring is kept outside domain in application configuration.
- Application services depend on ports and domain/application value objects, not web DTOs or concrete Redis/R2DBC adapters.
- Adapter mapping is performed at controller/adapter boundaries.
- Redis implementations are adapter-side implementations of output ports.

### WebFlux/productive request path

- `SubstitutionCommandUseCaseImpl` no longer blocks or manually subscribes while updating baseline state; baseline mutation is composed into the returned `Mono`.
- `LeagueRepositoryAdapter` no longer manually subscribes while warming cache; the save is composed into the fallback reactive chain.
- `MatchStatePersister` returns `Mono<Void>` and lifecycle callers compose the persistence instead of hiding a manual subscription.
- `V24DetailedMatchController#getDetail` isolates the legacy synchronous storage port call on `boundedElastic` so it does not run on the WebFlux event loop.
- Remaining blocking calls are classified as non-request-path synchronous/batch/infrastructure compatibility points:
  - seed/batch writers perform startup/admin batch persistence with explicit timeout boundaries;
  - `V24DetailedMatchRedisAdapter` implements a synchronous storage port used by legacy query services/tests and is called from request paths through bounded-elastic isolation;
  - `MatchSimulationOrchestrator` synchronous sections are isolated behind its scheduler boundary for league simulation work;
  - `RoundController` subscriptions are lifecycle fire-and-forget starts of live round/match engines, not ignored request publishers; failures are logged and surfaced through engine state.

No unresolved critical WebFlux misuse remains in the audited request path.

### Reflection in audited tests

Reflection was removed from the audited lifecycle/discipline/simulation/harness tests. Coverage now uses public APIs, JSON round-trips, public setters, fixtures, fakes or behavior-level assertions.

Audit command for scoped tests:

```text
rg -n "setAccessible\(|getDeclaredMethod|getDeclaredField|\.invoke\(" src/test/java/com/footballmanager/application/service/simulation src/test/java/com/footballmanager/application/service/simulation/v24 src/test/java/com/footballmanager/adapters/in/web/testharness src/test/java/com/footballmanager/domain/model/entity/SessionPlayerDisciplineFieldsTest.java src/test/java/com/footballmanager/application/service/career/GetCareerStatusUseCaseImplTest.java
```

Result: no matches.

Note: `PlayerAttributesDeprecationTest` still uses reflection intentionally to assert class shape/deprecated members are absent. It is not a private shim, does not call `setAccessible`, and does not couple tests to hidden behavior.

### God classes / responsibility audit

No production Java file exceeds 500 lines after the refactor. The largest classes remain under the threshold and were reviewed as cohesive coordinators/services rather than line-count-only splits:

- `LineupDtoAssembler` (~480): assembles lineup view data at the application boundary; does not own persistence, controller, or engine behavior.
- `TestHarnessScenarioRunner` (~474): harness scenario orchestration; helpers contain mutation/diagnostic specifics.
- `TestHarnessLineupDiagnosticService` (~466): diagnostic report use case; isolated from production gameplay path.
- `V24LiveSession` (~433): live-session aggregate for mutable match state/replay; detailed simulation remains in V24 engine/services.
- `TacticalChangeService` (~425): tactical command service; DTO mapping has been extracted to controllers and application result records.

No remaining class is considered a god class for this MVP refactor scope.

## Behavior preservation and targeted fixes

- Full suite remains green after all architectural changes.
- The substitution E2E test now verifies the professional behavior correctly: substitutions are checked against observable match output (goals, xG or shots) across deterministic seeds, instead of requiring a goal in a low-event stochastic seed.
- Discipline null-default tests now validate public deserialization behavior rather than mutating private fields.
- Career status tests now use public aggregate APIs instead of field/method reflection.

## Validation results

| Validation | Result |
|---|---:|
| Compile/test-compile | Passed |
| Focused V24/lineup/harness/lifecycle/discipline/league/controller tests | Passed |
| Full suite | Passed |
| Surefire total | 2433 tests |
| Failures | 0 |
| Errors | 0 |
| Skipped | 4 |

## Final audit status

APPROVED
