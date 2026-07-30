# Post-MVP 1 Code Quality Independent Audit

Date: 2026-07-30

Verdict: `READY WITH TECHNICAL DEBT`

## 1. Baseline

Audited repositories:

- Root: `D:\ProyectosOpenCode\MANAGER`
- Frontend: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`

Declared MVP 1 closure commit:

- Root: `35e6bbf7 docs: record definitive MVP 1 closure audit`
- Frontend head: `6fc04e2 Complete player trait browser acceptance`

This audit was read-only except for this new report. No code, tests, configuration, DB contents, commits or pushes were changed.

## 2. Git

Commands executed:

- root `git status --short`
- root `git log --oneline -20`
- root `git show --stat --oneline 35e6bbf7`
- root `git diff --check`
- frontend `git status --short`
- frontend `git log --oneline -15`
- frontend `git diff --check`

Results:

| Repository | Head | Status before report | `diff --check` | Notes |
| --- | --- | --- | --- | --- |
| Root | `35e6bbf7` | clean | clean | closure audit commit adds `docs/data/MVP1_FINAL_CLOSURE_INDEPENDENT_AUDIT.md` |
| Frontend | `6fc04e2` | clean | clean | latest commits focus on trait browser acceptance and UTF-8 |

The root tree contains many local runtime/log/artifact-looking files in the working directory (`backend*.log`, `frontend*.log`, `dump.rdb`, `dist`, `node_modules`, `target`, etc.), but Git status was clean. They are not active tracked changes. For day-to-day hygiene, repository root should avoid becoming an operational scratchpad.

## 3. Inventory

Backend Java inventory:

| Metric | Count |
| --- | ---: |
| Java files | 855 |
| Productive Java files | 624 |
| Test Java files | 231 |
| Productive files > 400 lines | 22 |
| Productive files > 500 lines | 2 |
| Total Java files > 400 lines, including tests | 63 |
| Total Java files > 500 lines, including tests | 28 |

Top productive backend size/dependency risks:

| File | Lines | Approx. collaborators/fields | Risk |
| --- | ---: | ---: | --- |
| `ThreeLeagueDatasetImporter.java` | 590 | 3 | large infrastructure importer, many SQL/data responsibilities |
| `TestHarnessScenarioMatrixService.java` | 501 | 3 | large diagnostic matrix orchestration |
| `TestHarnessWideDefenderLabService.java` | 498 | 3 | large harness lab service |
| `TestHarnessLineupDiagnosticService.java` | 494 | 4 | diagnostic calculations plus mapping |
| `LineupAutoSelector.java` | 484 | 2 | domain-heavy selection rules concentrated in one service |
| `LineupDtoAssembler.java` | 481 | 2 | DTO assembly plus chemistry/rating mapping |
| `LiveSession.java` | 478 | 6 | live state and tick coordination |
| `TacticalChangeService.java` | 469 | 1 | important tactical rules concentrated in one class |
| `LeagueSimulator.java` | 462 | 14 | major orchestration and lifecycle mutation coordinator |
| `RoundController.java` | 444 | 11 | controller with many collaborators |
| `DetailedMatchEngine.java` | 422 | 19 | central match simulation coordinator |
| `TestHarnessUseCaseImpl.java` | 402 | 17 | facade over many harness subsystems |

Frontend inventory:

| Metric | Count |
| --- | ---: |
| TS/HTML/SCSS files under `src` | 321 |
| TypeScript files | 266 |
| HTML templates | 54 |
| SCSS files | 1 |
| Files > 400 lines | 58 |
| Files > 500 lines | 30 |

Top frontend size/type risks:

| File | Lines | `any` count | `subscribe` count | Risk |
| --- | ---: | ---: | ---: | --- |
| `squad-editor-modal.component.spec.ts` | 4164 | 379 | 1 | oversized test file; brittle fixture surface |
| `test-harness-page.component.spec.ts` | 4028 | 215 | 0 | oversized test file |
| `test-harness-page.component.html` | 3507 | 0 | 0 | very large template; many responsibilities in view |
| `test-harness.model.ts` | 1558 | 0 | 0 | large contract/model aggregation |
| `partido-modal.component.ts` | 660 | 0 | 0 | large live tactical component |
| `formation-modal.component.ts` | 608 | 0 | 1 | large modal component |
| `squad-management.component.ts` | 564 | 0 | 13 | large smart component with manual subscriptions |
| `live-match-modals.service.ts` | 535 | 0 | 4 | modal orchestration and memory state |
| `round-live.component.ts` | 533 | 2 | 5 | live round orchestration |
| `detailed-match-page.component.scss` | 525 | 0 | 0 | large style surface |

## 4. God classes and large orchestrators

| Class | Responsibilities | Dependencies/state | Assessment | Severity |
| --- | --- | --- | --- | --- |
| `DetailedMatchEngine` | possession, chance volume, fatigue, cards, injuries, shots, tactical shape, substitutions, result finalization | creates ~19 collaborators manually; static goal counter; owns minute simulation flow | Not a classic unbounded god class anymore, but still a central engine coordinator with too much collaborator construction and tactical knowledge. | P1 |
| `LeagueSimulator` | chooses simulation engine, applies fixtures, persists details, applies injury/fatigue/discipline/form mutations, lifecycle transitions | 14 collaborators/flags; multiple constructors; synchronous and reactive persistence boundary | God-class risk: orchestration, lifecycle mutation and persistence concerns should be split. | P1 |
| `LineupDtoAssembler` | maps lineup domain view into API DTO, chemistry groups, ratings, slots and warnings | moderate dependencies but broad mapping knowledge | Acceptable adapter/application assembler, but too broad and likely changes for many UI reasons. | P2 |
| `MatchSimulationOrchestrator` | match-day orchestration and stale/future match handling | moderate size; coordinates career/match operations | Orchestrator acceptable, but stale-match handling and persistence side effects need stronger separation. | P2 |
| `TestHarnessUseCaseImpl` | exposes all harness operations | 17 collaborators; profile-gated; facade methods | Large but profile-gated facade. Not critical in production, but hard to maintain and test. | P1/P2 |
| `ThreeLeagueDatasetImporter` | read JSON resources, validate, upsert countries/leagues/clubs/teams/players/traits, schema patching, global validation | JDBC + ObjectMapper + validator; 590 lines | Infrastructure god-class risk: cohesive around import, but combines input parsing, validation, SQL writing and schema repair. | P1 |
| `SquadManagementComponent` | dashboard/squad/lineup/status/modals/advance flow | many observables and manual subscriptions; 564 lines | Smart component still too broad; should become shell plus feature facades. | P1 |
| `LiveMatchModalsService` | pause/resume, squad loading, dialog data shaping, modal memory, local substitution memory | stateful maps and nested subscriptions | Useful coordinator, but state and side effects are concentrated. | P1 |
| `TestHarnessPage` frontend | full debug UI, matrixes, replay, export, scenario state | 3507-line template plus many flow utils | Harness is functionally valuable but visually/code-wise too monolithic. | P1 |

No `P0` god class was found: the system is stable and tested. However, several P1 candidates should be addressed before large new features.

## 5. Hexagonal architecture

Positive findings:

- Application/domain scans did not show DTO web imports under the main domain/application boundaries.
- Infrastructure owns JDBC, R2DBC, Redis and importer implementation details.
- Test harness adapters are profile-gated.
- Web controllers mostly call use cases/services and map responses at boundaries.

Issues:

| Class/package | Expected layer | Actual dependencies | Complies | Problem |
| --- | --- | --- | --- | --- |
| `domain.ports.*` | pure domain/application port contracts | `reactor.core.publisher.Mono/Flux` | partial | Domain port API is tied to Reactor. This may be acceptable by project convention, but not pure hexagonal domain. |
| `domain.port.in.testharness.*` | domain/application contract | many harness-specific result records | partial | Debug/test harness concepts live in domain port package, increasing domain vocabulary with tooling concepts. |
| `application.config.*` | composition root/config | Spring `@Configuration` in application package | partial | Configuration belongs closer to infrastructure/bootstrap. |
| `application.service.*` | application use cases | many Spring `@Service`, `@Component`, `@Profile`, `@Autowired` | partial | Pragmatic Spring application service style, but not framework-independent application core. |
| `application.service.simulation.detailed.*Dto` | application/detail simulation | DTO-named classes used internally | partial | Naming blurs app model vs API DTO. |
| `adapters.in.web.common.ControllerHelper` | web adapter | security/auth checks | yes | Properly adapter-scoped. |
| `infrastructure.world.importer.*` | infrastructure adapter | JDBC/ClassPathResource/ObjectMapper | yes | Correctly outside domain/application, but importer is large. |

Hexagonal verdict: functional and mostly directionally correct, but not strict. The biggest impurity is Reactor/Spring leakage into domain/application port contracts.

## 6. Spring WebFlux

Findings:

| Location | Pattern | Classification | Notes |
| --- | --- | --- | --- |
| `ReactiveLifecycleExecutor` | manual `.subscribe()` | intentional boundary, risky | Good centralization of fire-and-forget lifecycle work; it logs and disposes. Still swallows errors after logging via `onErrorResume(Mono.empty())`. |
| `WorldSeedBatchWriter` / `WorldTeamPostgresWriter` / `LegacySeedPrincipalDatabaseGuard` | `.block(...)` | batch/startup/legacy acceptable with caveat | Not on normal request path if guarded, but seed endpoints can invoke it; document/ensure bounded scheduler or admin-only profile before production. |
| Redis adapters | `onErrorResume(... Mono.empty())` | risky | `RedisWorldRepository.findByUserId`, `RedisMatchStateRepository`, command repositories may hide infrastructure failures as cache miss. |
| Controllers | `Mono.just(...)` for simple response constants | correct | No heavy eager work observed in those `Mono.just` cases. |
| Application services | `Mono.just` for already computed command results | mostly correct | Some pipelines still mix sync state mutation and reactive persistence. |
| `LeagueSimulator` | sync simulation plus reactive detail persistence | risky | Details are persisted through reactive port from sync simulation path; requires careful lifecycle boundary ownership. |

No blanket WebFlux failure was found, but error swallowing and sync/reactive mixing are P1/P2 risks.

## 7. Backend clean code

Strengths:

- Many former monoliths were split into named services.
- Tests are broad and behavior-rich.
- MVP dataset invariants are now explicit and executable.
- Profile gates exist for debug/test harness endpoints.

Issues:

- Too many compatibility comments and legacy paths remain in productive classes.
- Some application services are Spring-aware and framework-coupled.
- Importer mixes schema patching, parsing, validation, upsert SQL and reporting.
- Simulation engine still manually creates collaborators, making replacement/testing harder.
- Several records/DTO-like classes have very wide constructors; they are data shapes, but ergonomics are poor.
- Generic names remain: `Helper`, `Processor`, `Impl`, `Legacy`, `Manager`, `MVP`, `V25/V27/V31` in tests/docs/comments.
- Some Spanish/English naming is mixed in errors/logs/classes; not dangerous, but consistency is uneven.

Backend clean-code verdict: stable and understandable for current MVP, but not yet “polished professional” in the engine/importer/debug areas.

## 8. Frontend clean code

Strengths:

- MVP screens build and pass tests.
- Player trait rendering has dedicated tests and encoding guard.
- Several utility modules were extracted from modals/harness.
- Debug harness is valuable for product tuning.

Issues:

- Very large templates and specs remain, especially test harness and squad editor modal.
- `any` usage is high in harness flow utils/specs and round-live utilities.
- Manual `subscribe()` is common in production components/services (`SquadManagementComponent`, `RoundLiveComponent`, `LiveMatchModalsService`, older games/team screens).
- Some state is managed with `BehaviorSubject` fields directly in components instead of a cohesive store/facade.
- `LiveMatchModalsService` stores local mutable maps for substitutions/partido slots; useful, but this should be isolated behind a dedicated state object.
- Some older screens still look like pre-refactor Angular style and may not follow smart/dumb separation.

Frontend clean-code verdict: playable and stable, but the harness/live flows are still heavier than ideal.

## 9. Naming professionalism

Valid/historical:

- `legacy` in compatibility tests and comments is often legitimate.
- `Impl` is consistent with use case implementation naming, not automatically wrong.
- `MVP 1` in data/import docs and source-system constants is product/data provenance, acceptable.

Debt:

- `LineupHelper` is vague.
- `MatchResultProcessor` is moderately vague and could be named around standings/promotion side effects.
- `WorldSeedBatchWriter`, `WorldTeamPostgresWriter`, `legacyseed` package are historical and should eventually move behind explicit demo/seed tooling.
- Frontend flow files such as `.flow.misc-01.ts`, `.flow.position-06.ts` are mechanical split names, not domain names.
- Version-like names remain in tests/diagnostics (`V25`, `V27`, `V31`) and should be consolidated when those tests are touched.

## 10. Domain model

Strengths:

- Domain has meaningful objects for teams, players, careers, lineups, formation effectiveness, positions, chemistry and match state.
- Many tactical/formation calculations live in domain value objects rather than controllers.
- Dataset invariants around player identity and traits are now validated.

Issues:

- Some domain entities remain mutable and partly anemic (`CareerSave`, runtime/session models).
- Important match/lifecycle rules are spread across application services (`LeagueSimulator`, lifecycle appliers, detailed engine) rather than explicit domain services with narrow responsibilities.
- Injury/fatigue/discipline/form concepts exist, but their invariants are not yet a clean aggregate boundary.
- Match engine uses many DTO-like internal structures; the boundary between domain event, simulation event and API detail event is still blurred.

Domain verdict: good enough for MVP, but future injuries/stamina/professional-manager features should start with domain boundaries, not more orchestration inside current classes.

## 11. Persistence

Strengths:

- Principal dataset is clean and invariant-tested.
- Unsafe global player catalog writer was previously removed/guarded.
- Legacy seed writers have principal DB guard.
- R2DBC/Redis adapters are mostly isolated at edges.

Risks:

- `ThreeLeagueDatasetImporter` embeds SQL throughout one class.
- Legacy seed writers use blocking R2DBC calls; acceptable for batch only, not request path.
- Redis adapters sometimes turn errors into empty results, which can masquerade as missing state.
- SQL is dispersed across repositories/importers/writers; no central query catalog or migration-linked ownership.
- Local root has runtime logs and dump files; not tracked, but operational hygiene should improve.

## 12. API and contracts

Strengths:

- Controllers are mostly thin enough and return reactive types.
- Test harness endpoints are profile-gated with `dev/local/test`.
- Admin world endpoint checks `ROLE_ADMIN`.
- Lineup contracts have richer slots/chemistry/warnings.

Risks:

- Security config still permits several broad public paths (`teams`, `leagues`, `match-engine`, `fixtures`). Some may be product-intent, but should be explicitly reviewed before public deployment.
- There are duplicate-era endpoints (`/api/v1/matches`, `/api/v1/match-engine`, career round endpoints) and compatibility fallbacks.
- Some controllers (`RoundController`, `LineupController`, `TestHarnessController`) remain large and map many concerns directly.
- API status/error shape consistency is uneven; many errors are maps/strings rather than a single error contract.

## 13. Backend tests

Current known suite status from MVP closure:

- 2453 tests
- 0 failures
- 0 errors
- 4 skipped

Strengths:

- Broad integration, architecture, importer, runtime, lineup and simulation coverage.
- Reflection scan for `setAccessible`, `getDeclaredField`, `getDeclaredMethod`, `.invoke(` under backend tests returned no matches.
- Importer idempotence/rollback tests are behavior-oriented.

Risks:

- Very large test classes remain: several exceed 1000 lines.
- Some diagnostic/baseline tests carry versioned names (`V27`, `V31`) and should be normalized when next touched.
- Many tests rely on large fixture setup; maintainability will suffer unless fixture builders are further standardized.

## 14. Frontend tests

Current known suite status from MVP closure:

- `TOTAL: 1022 SUCCESS`
- 2 skipped
- 0 failures

Strengths:

- Encoding guard runs before test suite.
- Player card trait tests now assert correct UTF-8 and visible text.
- Live modal and harness flows have extensive coverage.

Risks:

- Oversized specs: `squad-editor-modal.component.spec.ts` and `test-harness-page.component.spec.ts` are too large.
- `any` is heavy in specs and harness utilities.
- Some tests inspect private implementation via `(component as any)` / `(service as any)`, especially `round-live` and modal service tests.
- Async tests include `setTimeout` and nested subscriptions in places.

## 15. Complexity and duplication

Top complexity risks:

1. `DetailedMatchEngine`
2. `LeagueSimulator`
3. `ThreeLeagueDatasetImporter`
4. `TestHarnessUseCaseImpl`
5. `TestHarnessScenarioMatrixService`
6. `LineupAutoSelector`
7. `LineupDtoAssembler`
8. `LiveSession`
9. `TacticalChangeService`
10. `RoundController`
11. `LineupController`
12. `TestHarnessController`
13. `SquadManagementComponent`
14. `LiveMatchModalsService`
15. `RoundLiveComponent`
16. `PartidoModalComponent`
17. `FormationModalComponent`
18. `test-harness-page.component.html`
19. `squad-editor-modal.component.html`
20. `test-harness-page.flow.*.ts` mechanical split set

No Checkstyle/PMD/SpotBugs/ESLint script was found as an active project command in the inspected package/pom output. Angular build/test are present; frontend lint appears absent.

## 16. Dead code and stale areas

Likely valid compatibility:

- Legacy lineups without slots.
- Legacy match path fallbacks.
- Historical fixture/division fallback logic.

Debt candidates:

- `legacyseed` package and old seed endpoints after MVP 1 dataset import becomes the only supported path.
- Deprecated career/admin wrapper services (`FixtureAdminService`, `CareerPlayerService`, `SeasonAdvancementService`) once all callers migrate.
- Duplicate frontend routes/screens for older games/team management if no longer product-facing.
- `debug/test-harness` should remain dev-only; if product debug surface moves elsewhere, prune old labs.

No code was removed in this audit.

## 17. Security and configuration

Strengths:

- JWT is configured.
- Passwords are not printed by runbook flow.
- Redis auth was validated in MVP closure.
- Test harness controllers use `@Profile({"dev", "local", "test"})`.
- Admin world seed endpoint checks `ROLE_ADMIN`.

Risks:

- CORS config allows `*` origins. This is acceptable for local MVP, not for production.
- CSRF is disabled; OK for stateless API if JWT and CORS are tightened.
- Security config permits broad paths including teams/leagues/match-engine/fixtures. This should be reviewed before public release.
- `/api/v1/career/admin` is not visibly profile-gated in the controller; authorization relies on security config/request authentication. Review before production.
- Some log messages include IDs and operational details; not secrets, but should be classified.

Security verdict: no immediate P0 for local MVP; P1 before public deployment.

## 18. Observability

Strengths:

- Logs exist in simulation, lifecycle and controllers.
- `ReactiveLifecycleExecutor` centralizes lifecycle fire-and-forget logging/disposal.
- Health endpoints exist.

Gaps:

- No clear correlation ID story.
- No metrics/tracing inventory found.
- Some Redis failures are silenced into empty fallbacks.
- Warning/error logs are uneven and sometimes debug-tag based (`[SQUAD-DUP]`, `[ROUND-CONTROLLER]`).
- Background/lifecycle side effects lack durable audit trail.

## 19. Performance

Strengths:

- Importer uses deterministic upserts and batch-ish explicit dataset path.
- Legacy seed batch writer avoids old row-by-row insertion.
- Match simulation is in-memory and deterministic by seed.

Risks:

- Detailed engine and harness matrix simulations can become CPU-heavy; they need explicit bounds when exposed.
- Large frontend debug template and harness payloads are not product-user optimized.
- Redis/object mapper serialization may become expensive for large career snapshots.
- Some frontend components may trigger repeated subscriptions/change detection.
- Production bundle size is acceptable for MVP, but debug routes should remain lazy/dev.

## 20. Documentation

Current:

- `MANAGER_TEAM_RUNBOOK.md`
- MVP closure docs under `docs/data`
- architecture/refactor reports
- frontend acceptance docs

Historical/needs curation:

- Many refactor/audit docs are valuable but numerous.
- Some historical docs preserve rejected states and addenda; useful for traceability, noisy for onboarding.
- Root has many operational log filenames that can confuse new contributors.

Recommendation: create a short “active docs index” that points to current runbook, architecture, MVP dataset closure and test commands, and moves older reports under an explicit historical index.

## 21. Scores

| Area | Score / 10 | Reason |
| --- | ---: | --- |
| Architecture | 7.0 | Mostly layered, but Reactor/Spring leak into ports/application |
| Domain | 6.5 | Good tactical concepts, still mutable/anemic in career/match areas |
| Backend clean code | 7.0 | Stable and tested; engine/importer/harness still heavy |
| Frontend clean code | 6.0 | Playable, but large templates/specs and manual subscriptions |
| WebFlux | 6.5 | Works, but error swallowing and sync/reactive mixing remain |
| Persistence | 7.0 | Clean MVP dataset; SQL/importer concentration remains |
| Tests | 8.0 | Strong breadth; maintainability issues in huge specs/tests |
| Security | 6.0 | Fine for local MVP, not public-production hardened |
| Observability | 5.5 | Logs exist; no correlation/metrics/tracing discipline |
| Performance | 6.5 | OK for MVP; harness/simulation and payloads need bounds |
| Documentation | 7.0 | Rich but noisy; active vs historical needs curation |
| Maintainability | 6.5 | Stable enough, with clear P1 cleanup before big features |

## 22. Prioritized backlog

### P0 critical

None found. No data-loss, build-breaking, test-breaking or immediate unsafe condition was detected in the current post-MVP state.

### P1 important

| Order | File/class | Evidence | Impact | Proposed correction | Effort | Risk |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | `domain.ports.*`, `domain.port.*` | Reactor `Mono/Flux` in domain port interfaces | Domain depends on reactive library; less pure hexagonal boundary | Move async type choice to application/adapters or formally define Reactor as application port convention and relocate ports out of domain | M/L | Medium |
| 2 | `DetailedMatchEngine` | 422 lines, ~19 manually created collaborators | Hard to evolve injuries/stamina/tactics professionally | Introduce composition/factory and split minute loop, chance generation, tactical shape and event mutation policies | L | Medium |
| 3 | `LeagueSimulator` | 462 lines, 14 deps/flags, simulation + persistence + lifecycle | High-change class for match/season features | Split fixture simulation, detail persistence, lifecycle mutation and result application | L | Medium |
| 4 | `ThreeLeagueDatasetImporter` | 590 lines, parsing + validation + SQL + schema patching | Dataset changes risky | Extract resource reader, validator, SQL writers and schema migrator/remediation guard | M/L | Medium |
| 5 | `LiveMatchModalsService` | 535 lines, mutable maps, nested modal/pause flows | Live modal bugs likely as features grow | Extract pause/resume coordinator, modal data builder, local match UI memory store | M | Medium |
| 6 | `SquadManagementComponent` | 564 lines, 13 subscribes | Smart component too broad | Move data orchestration into facade/store and split child components | M | Low/Medium |
| 7 | Security config/CORS | `*` origins, broad permitAll paths | Unsafe for public deployment | Define production profile CORS origins and require auth for match-engine/fixtures/team mutation paths as needed | M | Medium |
| 8 | Redis adapters | `onErrorResume(... Mono.empty())` | Infrastructure failures can look like missing data | Return typed errors or degraded result with logging; only use empty fallback where explicitly safe | M | Medium |
| 9 | Frontend test harness template | 3507-line HTML | Hard to review/change | Split panels into standalone components and routes | L | Low/Medium |

### P2 improvement

| File/class | Evidence | Impact | Proposed correction |
| --- | --- | --- | --- |
| `LineupDtoAssembler` | 481 lines | Mapping churn | Split chemistry, slots, warnings and ratings assemblers |
| `LineupAutoSelector` | 484 lines | Rule concentration | Extract scoring/ranking/constraint policies |
| `RoundController`, `LineupController`, `TestHarnessController` | >400 lines | Controller sprawl | Move mapping/request orchestration to adapter services |
| Frontend `*.flow.misc-01.ts` etc. | mechanical names | Low readability | Rename by domain responsibility |
| Huge backend tests | many >1000 lines | Slow comprehension | Introduce fixtures/builders and split by behavior |
| Frontend specs with `(as any)` | private-state assertions | Brittle tests | Test through public UI/service behavior or typed harness fixtures |
| Documentation set | many closure/refactor reports | Onboarding noise | Active docs index + historical archive index |
| Logs in root workspace | many local artifacts | Confusing workspace | Move runtime logs to ignored `logs/` workflow and document it |

### P3 optional

- Normalize Spanish/English message style.
- Replace remaining vague comments with clearer names where possible.
- Add optional static analysis scripts once dependencies are already available.
- Add bundle budget tracking for debug/harness chunks.
- Add correlation ID/tracing if the project moves toward hosted/public use.

## 23. Risks

- New tactical/injury/stamina features could overload `DetailedMatchEngine` and `LeagueSimulator` if added directly.
- Frontend live modal state can drift if more event types are added without a dedicated state store.
- Keeping debug/test harness large but useful is a balancing act; it should remain dev-only and modular.
- Public deployment requires a security pass; local MVP readiness is not the same as internet readiness.

## 24. Conclusion

The project is stable, playable and convincingly validated for MVP 1. It is not in an unsafe state, and there is no evidence that new work must stop immediately. However, it is not yet “professional ready” in the strict clean-code/architecture sense. The main risks are concentrated and understandable: simulation orchestration, importer responsibilities, frontend live/harness complexity, security hardening and strict hexagonal purity.

Final verdict: `READY WITH TECHNICAL DEBT`.
