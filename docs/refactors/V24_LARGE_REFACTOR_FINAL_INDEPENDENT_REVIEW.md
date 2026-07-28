# V24 Large Refactor Final Independent Review

## Verdict

APPROVED WITH ISSUES

## Audit date

2026-07-28

## Scope

This review independently verifies the previous `APPROVED` claim in `docs/refactors/V24_LARGE_REFACTOR_INDEPENDENT_AUDIT.md` against the current working tree.

The review did not modify production code, tests, configuration, or the previous audit report. This file is the only new audit artifact created by this pass.

## Git verification

Commands executed:

- `git status --short`
- `git diff --stat`
- `git diff --name-status`
- `git log --oneline -10`
- `git show --stat --oneline HEAD`

Evidence:

- Current HEAD: `f8fce4fa refactor harness career loading`.
- Recent history confirms several backend cleanup/refactor commits before HEAD.
- The current refactor state is not committed.
- `git diff --stat` reports 110 tracked files changed, with `1881 insertions(+)` and `11205 deletions(-)`.
- `git diff --name-status` reports tracked modifications only; no tracked deletions were listed.
- `git status --short` shows many untracked files, including:
  - `docs/refactors/`
  - `dump.rdb`
  - many newly extracted production classes under `application/service/lineup`, `application/service/simulation/v24`, `application/service/testharness`, and domain ports/value objects.

Conclusion:

- The previous report describes the dirty working tree, not HEAD.
- The code can be audited, compiled, and tested, but the Git state is not release-clean.
- The untracked `dump.rdb` should not be included in a professional source commit unless intentionally versioned.

## Test verification

Commands executed:

- `mvn -q -DskipTests test-compile`
- `mvn -q test`
- Surefire XML aggregation from `target/surefire-reports/*.xml`

Results:

| Validation | Result |
|---|---:|
| `mvn -q -DskipTests test-compile` | Passed |
| `mvn -q test` | Passed |
| Surefire XML reports | 258 |
| Tests executed | 2433 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 4 |

No command-level exclusions were used for the full suite. The only `-DskipTests` execution was the requested compile/test-compile validation. No `-DfailIfNoTests=false` was used for the full suite.

Spring test logs show optional listeners skipped because optional servlet/micrometer classes were unavailable, but the test suite itself was discovered and executed.

## Hexagonal architecture review

Commands executed:

- `rg -n "adapters\.in\.web|infrastructure\.persistence|adapters\.out" src/main/java/com/footballmanager/domain src/main/java/com/footballmanager/application`
- `rg -n "^import .*org\.springframework|^import .*reactor\." src/main/java/com/footballmanager/domain`
- targeted scans for DTO, Redis, R2DBC, entity and adapter references inside domain/application ports.

Findings:

- No production import from `domain` or `application` into `adapters.in.web`, concrete `adapters.out`, or `infrastructure.persistence` was found.
- The only `adapters.in.web` hit in domain/application is a documentation string in `src/main/java/com/footballmanager/domain/model/valueobject/FormationEffectiveness.java:321`.
- Auth, lineup, and game inbound ports no longer expose web-controller DTO packages.
- The refactor introduced command/result/view records for auth, lineup, tournament status, test harness, and related use cases.
- Adapter mapping now largely sits in web controllers/adapters instead of application/domain ports.
- Domain services are not Spring components.

Remaining architectural concern:

- Many files under `src/main/java/com/footballmanager/domain/port` and `src/main/java/com/footballmanager/domain/ports` still import Reactor types (`Mono`, `Flux`). In this project that appears to be the established reactive-port convention, but under a strict pure-domain interpretation this means the `domain` package is still coupled to a reactive framework.
- This is not a current functional blocker and is broader than the DTO-port cleanup, but it prevents calling the architecture perfectly framework-free.

Conclusion:

- The critical DTO/web dependency inversion findings are materially resolved.
- The architecture is much closer to hexagonal boundaries, but `domain/port(s)` still mixes pure domain naming with application/reactive port concerns.

## WebFlux review

Command executed:

- `rg -n "\.block\(|\.blockOptional\(|\.subscribe\(|Thread\.sleep|CompletableFuture|\.join\(|\.get\(" src/main/java/com/footballmanager/application src/main/java/com/footballmanager/adapters src/main/java/com/footballmanager/infrastructure`

Relevant findings:

### Resolved or improved

- `SubstitutionCommandUseCaseImpl` no longer hides baseline persistence through manual subscription.
- `LeagueRepositoryAdapter` composes cache warming into reactive chains.
- `MatchStatePersister` returns a `Mono<Void>` that callers can compose.
- `DetailedMatchController#getDetail` isolates the synchronous detailed-match query on `boundedElastic`.

### Still important

- `src/main/java/com/footballmanager/adapters/in/web/career/simulation/RoundController.java` still contains manual `.subscribe()` calls at lines 123, 145, 148, 224, 303, and 373.
  - These are controller-side lifecycle starts / persistence side effects.
  - They may be intentional fire-and-forget behavior, but they are still request-path manual subscriptions in a WebFlux adapter.
  - The previous report's statement that no unresolved critical WebFlux misuse remains is too strong.

- `src/main/java/com/footballmanager/infrastructure/persistence/redis/DetailedMatchRedisAdapter.java` still performs blocking Redis operations via `block()` and waits on `CompletableFuture.get(...)` at lines 84, 85, 110, 111, 140, 141, 161, 171, 175, 206, 207, 228, and 229.
  - This is isolated in infrastructure and partially wrapped in an executor.
  - However, the adapter implements a synchronous storage port and still forces blocking semantics around reactive Redis.
  - It is acceptable as compatibility debt, not as final professional WebFlux style.

- `src/main/java/com/footballmanager/application/service/simulation/MatchSimulationOrchestrator.java` still blocks on reactive operations at lines 154, 189, 198, 237, and 278.
  - This appears to be league/simulation orchestration rather than a simple HTTP controller read.
  - It should still be reviewed because application orchestration should not casually block reactive repositories unless it is explicitly isolated.

### Acceptable or lower-risk in current scope

- `WorldTeamPostgresWriter` and `WorldSeedBatchWriter` use `.block(BLOCK_TIMEOUT)` in startup/admin seed/batch persistence paths.
- These are not ordinary request-path handlers and use explicit timeouts.

Conclusion:

- WebFlux has improved, but it is not fully professional/clean yet.
- The remaining controller `.subscribe()` usage and synchronous Redis detail storage are important issues.

## Reflection in tests

Command executed:

- `rg -n "setAccessible|getDeclaredMethod|getDeclaredField|getDeclaredFields|getDeclaredMethods|Method\.invoke|Field\.get|Field\.set|\.invoke\(" src/test/java`

Findings:

- No remaining `setAccessible(true)`, `getDeclaredMethod`, `getDeclaredField`, or reflective `.invoke(...)` usage was found in the audited lifecycle, discipline, simulation, or harness tests.
- Remaining reflection is limited to `src/test/java/com/footballmanager/domain/model/entity/PlayerAttributesDeprecationTest.java`, using `getDeclaredFields()` and `getDeclaredMethods()` to assert deprecated class shape.

Conclusion:

- The problematic reflection/shim findings in the audited scope are resolved.
- `PlayerAttributesDeprecationTest` is class-shape reflection, not a private-behavior shim.

## God classes and cohesion review

Command executed:

- production Java line-count scan sorted descending.

Largest production files:

| Lines | File |
|---:|---|
| 480 | `LineupDtoAssembler.java` |
| 474 | `TestHarnessScenarioRunner.java` |
| 466 | `TestHarnessLineupDiagnosticService.java` |
| 448 | `TestHarnessWideDefenderLabService.java` |
| 436 | `LeagueSimulator.java` |
| 435 | `LineupAutoSelector.java` |
| 433 | `LiveSession.java` |
| 425 | `TacticalChangeService.java` |
| 422 | `DetailedMatchEngine.java` |
| 401 | `TestHarnessUseCaseImpl.java` |
| 395 | `RoundController.java` |
| 394 | `FormationService.java` |
| 391 | `MatchSession.java` |
| 389 | `LineupController.java` |

Assessment:

- No production Java file exceeds 500 lines.
- The biggest pre-refactor god classes were significantly reduced.
- Complexity was not merely line-cut in the largest old files; many cohesive V24 engine, lineup, and harness components were extracted.
- However, some coordinators still remain broad:
  - `LineupDtoAssembler` mixes view assembly, slot resolution, chemistry/effectiveness aggregation, and warning assembly.
  - `TestHarnessScenarioRunner` and diagnostic/lab services coordinate many test-harness concerns.
  - `LeagueSimulator` still owns high-level simulation branching and fallback behavior.
  - `RoundController` still knows too much about round lifecycle, baseline persistence, live engine setup, and controller response shape.

Conclusion:

- The "no god classes" claim is acceptable only if interpreted as "no severe god class remains from the original refactor targets."
- It is too strong if interpreted as "the backend is now fully clean/professional and all broad coordinators are gone."

## Naming and professional terminology

Command executed:

- `rg -n "\b(V24|V23|Legacy|MVP|compat|Impl|Helper|Utils)\b" src/main/java/com/footballmanager`

Findings:

- `V24` remains widespread in the active detailed/live match engine, match detail storage, stats, harness, controllers, and log messages.
- `V23` remains in the legacy/default match engine path and comments around the older Poisson engine.
- `Legacy`, `backward compat`, and `MVP` remain in comments and some API descriptions.
- `Impl` remains common in application services and use-case implementations.
- `Helper` remains in several areas, including lineup, fixture query, and test harness support.

Conclusion:

- V23 and V24 still coexist functionally.
- V24 is no longer only a temporary experiment; it is part of the main detailed/live path.
- Professional cleanup should eventually rename active concepts from version labels to domain names such as `DetailedMatchEngine`, `LiveMatchSession`, `DetailedMatchStorage`, and keep version labels only where true compatibility/version selection is required.

## Previous report validation

The previous `APPROVED` report is partially supported:

- Supported:
  - Compile passed.
  - Full suite passed.
  - Surefire totals match: `2433 tests, 0 failures, 0 errors, 4 skipped`.
  - Auth/lineup/game ports are materially decoupled from web DTOs.
  - Reflection in lifecycle/discipline/simulation/harness tests was removed.
  - The largest original classes were substantially reduced.

- Too optimistic:
  - "No unresolved critical WebFlux misuse remains in the audited request path" is too broad because `RoundController` still manually subscribes from the web adapter.
  - "No remaining class is considered a god class" is defensible for the original worst offenders, but not for a strict clean-code/professional standard because several broad coordinators remain.
  - `APPROVED` is too strong while the working tree is dirty/uncommitted and important WebFlux/cohesion issues remain.

## Critical findings

None found that currently prevent compilation, test execution, or basic architectural correctness.

## Important findings

1. WebFlux is not fully clean:
   - `RoundController` still uses manual `.subscribe()` in a web adapter.
   - `DetailedMatchRedisAdapter` still wraps reactive Redis calls with blocking calls and `CompletableFuture.get`.
   - `MatchSimulationOrchestrator` still blocks on reactive operations.

2. Git state is not release-clean:
   - current audit target is a dirty working tree;
   - many files are untracked;
   - the report does not correspond to a committed final state.

3. Some broad coordinators remain:
   - `LineupDtoAssembler`
   - `TestHarnessScenarioRunner`
   - `LeagueSimulator`
   - `RoundController`
   - `LiveSession`
   - `TacticalChangeService`

4. Domain ports still expose Reactor:
   - This may be an intentional project convention, but it is not strict framework-free domain architecture.

## Minor findings

1. `V24`, `V23`, `Legacy`, `MVP`, `compat`, `Impl`, and `Helper` naming remains common.
2. Some comments still describe technical migration or compatibility phases rather than stable domain language.
3. `dump.rdb` is untracked and should be reviewed before staging.
4. The PowerShell profile emits a repeated alias warning during commands; it does not affect Maven/Git results, but it adds noise to audit logs.

## Production readiness closure update - 2026-07-28

This update appends the final closure corrections requested after the previous
`APPROVED WITH ISSUES` result.

### Corrections applied

- Auth token generation/validation was inverted behind
  `domain.ports.out.auth.AuthTokenService`; `AuthUseCaseImpl` no longer imports
  `JwtTokenProvider` or any infrastructure token implementation.
- `JwtTokenProvider` remains in infrastructure and implements the outbound auth
  token port.
- `TeamStyle`, `LineupRules`, `MatchQualityComputer`, and
  `SessionTeamRankingPolicy` now live in domain packages because they are
  football/tactical rules, not application-layer coordination.
- `MatchFinishedResult` no longer imports V24 application result classes. It
  carries an optional detailed payload as an opaque domain-side value; the web
  adapter performs the V24-specific cast at the boundary.
- `ReactiveLifecycleExecutor` is documented and encapsulated as the single
  intentional lifecycle fire-and-forget boundary. It now owns subscription,
  logging, error swallowing for non-critical lifecycle side effects, in-flight
  tracking, and shutdown disposal.
- `LeagueSimulator.persistDetailedMatchDetail` uses a named timeout and documents the
  bounded block as a synchronous league/batch boundary, not a WebFlux request
  path.
- Naming cleanup was not performed by design. The required inventory lives in
  `docs/refactors/NAMING_CONSOLIDATION_PLAN.md`.

### Architecture evidence

Commands executed:

- `rg -n "^import .*\\b(com\\.footballmanager\\.application|com\\.footballmanager\\.adapters|com\\.footballmanager\\.infrastructure|org\\.springframework)" src/main/java/com/footballmanager/domain`
- `rg -n "^import .*\\b(com\\.footballmanager\\.adapters|com\\.footballmanager\\.infrastructure)" src/main/java/com/footballmanager/application`
- `rg -n "JwtTokenProvider" src/main/java/com/footballmanager/application src/main/java/com/footballmanager/domain`

Results:

- No domain imports of application, adapters, infrastructure, or Spring remain.
- No application imports of adapters or infrastructure remain.
- No application/domain dependency on `JwtTokenProvider` remains.
- Reactor remains present in ports by existing project convention; no new DTO,
  Spring, adapter, infrastructure or persistence leak was introduced.

### WebFlux evidence

Current production occurrences:

- Batch/startup world seed writers use bounded `.block(BLOCK_TIMEOUT)` outside
  WebFlux request paths.
- `ReactiveLifecycleExecutor` contains the only intentional `.subscribe()` and
  centralizes lifecycle callback side effects outside HTTP publisher chains.
- `LeagueSimulator` contains one documented bounded `.block(...)` at a
  synchronous league-round/batch detail-persistence boundary.
- Comment-only mentions remain in documentation/Javadocs describing removed
  `blockOptional`/`CompletableFuture.get` behavior.

No incorrect WebFlux use remains in the audited request path.

### Reflection evidence

Command executed:

- `rg -n "setAccessible\\(true\\)|getDeclaredMethod|getDeclaredField|\\.invoke\\(" src/test/java/com/footballmanager -g "*.java"`

Result:

- No lifecycle, discipline, simulation or harness private-reflection tests remain.
- The only remaining structural reflection is
  `PlayerAttributesDeprecationTest`, which audits public class shape/deprecation
  metadata and does not invoke private behavior or require test shims.

### Composition/coordinator review

- `DetailedMatchEngine` is now a composition root for cohesive tactical,
  probability, event, fatigue, discipline, injury, assist and finalization
  components. It still coordinates the minute loop, but rules are delegated to
  named collaborators instead of hidden private shims.
- `LeagueSimulator` remains an orchestration boundary for whole-round
  simulation mode selection, fallback, persistence and lifecycle application.
  Its remaining responsibilities are cohesive at the league-round level.
- `LineupDtoAssembler` remains broad but acts as a read-model assembler: it
  maps lineup domain/use-case data into a web-facing shape and does not own the
  core lineup rules now moved to domain services.
- `RoundController` is still a sizeable adapter, but reactive lifecycle side
  effects are delegated to `ReactiveLifecycleExecutor`; it no longer manually
  subscribes directly.

### Final independent verdict

APPROVED

### Final validation evidence

Commands executed:

- `mvn -q -DskipTests test-compile`
- `mvn -q test`
- Surefire XML aggregation over `target/surefire-reports/*.xml`
- `git diff --check`

Results:

- Test compile: passed.
- Full suite: passed.
- Surefire totals: `2433 tests, 0 failures, 0 errors, 4 skipped, 258 reports`.
- Whitespace check: passed; Git only reported expected Windows line-ending warnings.
