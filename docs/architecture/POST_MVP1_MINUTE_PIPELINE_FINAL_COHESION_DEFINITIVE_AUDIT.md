# MANAGER - Post-MVP 1 Minute Pipeline Final Cohesion Definitive Audit

Date: 2026-07-30

Scope: independent audit of the minute-by-minute detailed match pipeline after the final cohesion remediation commits.

Audited range:

- Base: `28de2f2c`
- Root: `15265065`
- Commits:
  - `80751206` Separate minute injury and restart event phases
  - `b1ef2d14` Remove substitution behavior from minute state
  - `5c5a8114` Add unit coverage for detailed match minute phases
  - `58ada394` Extend detailed match golden and concurrency coverage
  - `15265065` Close minute pipeline cohesion remediation

## Verdict

APPROVED

The remediation is functionally sound for the audited minute pipeline and the current backend suite is green. The previous shared-substitution-engine risk is materially corrected by creating a fresh `DetailedMatchMinuteFlow` inside each bounded simulation run. The phase split is cohesive enough for the current MVP and the architecture guards cover the main regression points.

The remaining important issue from the prior audit has been closed: concurrent `LiveSession` replay/mutation scenarios now stress multiple simultaneous sessions, same and different seeds, distinct deferred substitutions, replay after mutation, sequential-baseline equivalence, visible snapshot isolation, accumulated-event deduplication, and executor termination. The previous bug remains verified by historical code inspection rather than executing the old broken commit, which is acceptable for this closure because the current regression coverage proves the intended public behavior on the resulting implementation.

## Evidence commands

- `git status --short`
- `git log --oneline -15`
- `git show --stat --oneline 80751206`
- `git show --stat --oneline b1ef2d14`
- `git show --stat --oneline 5c5a8114`
- `git show --stat --oneline 58ada394`
- `git show --stat --oneline 15265065`
- `git diff 28de2f2c..15265065 --name-status`
- `git diff --check 28de2f2c..15265065`
- `mvn -q -DskipTests test-compile`
- focused tests for minute phases, golden snapshots, substitutions, live session, and architecture
- current Surefire report aggregation
- static searches for obsolete phases, substitution-engine ownership, reflection, blocking calls, manual subscriptions, and generic helper names

## Backend validation

Current Surefire reports:

- Reports: 275
- Tests: 2503
- Failures: 0
- Errors: 0
- Skipped: 4

Focused validation:

- `mvn -q -DskipTests test-compile`: passed
- `mvn -q "-Dtest=com.footballmanager.application.service.simulation.detailed.LiveSessionIsolationStressTest" test`: passed
- `mvn -q "-Dtest=com.footballmanager.adapters.in.web.career.simulation.MatchEventPlayerNameMappingTest,com.footballmanager.application.service.simulation.detailed.*LiveSession*Test" test`: passed
- focused minute pipeline, phase, substitution, golden snapshot, live session, and architecture tests: passed
- `mvn -q test`: passed

## Git and hygiene

Before this audit file was created:

- backend working tree: clean
- frontend working tree: clean
- `git diff --check`: no whitespace errors
- `git diff --check 28de2f2c..15265065`: no whitespace errors

The only file intentionally created by this task is this audit report.

## File-level scope

The audited commit range changed the detailed simulation pipeline and its tests/docs, including:

- `DetailedMatchEngineFlow`
- `DetailedMatchMinuteComposition`
- `DetailedMatchMinuteFlow`
- `DetailedMatchMinutePipeline`
- `MinuteMatchState`
- `MinuteSimulationInput`
- `MinuteSubstitutionPolicies`
- `MinuteInjuryPhase`
- `MinuteRestartEventPhase`
- `MinuteScheduledSubstitutionPhase`
- phase-level unit tests
- golden/concurrency coverage
- architecture boundary tests

No frontend, database, dataset, runtime configuration, or production infrastructure changes are part of this audit.

## Minute phase cohesion

The current pipeline order is explicit and readable:

1. scheduled substitutions
2. tactical state
3. possession
4. attack
5. discipline
6. injury
7. restart event
8. automatic substitution

The former aggregate physical-state phase has been removed from production code. `MinuteInjuryPhase` and `MinuteRestartEventPhase` are separate concrete phases with narrower responsibilities.

Assessment: acceptable.

## `MinuteMatchState`

`MinuteMatchState` now carries contextual state only:

- random
- home state
- away state
- timeline
- home selector
- away selector
- applied scheduled substitutions

It does not own substitution engines. It validates non-null collaborators and distinct home/away state and selectors.

Assessment: acceptable.

## Substitution ownership

`MinuteSubstitutionPolicies` owns substitution engines and is injected into the scheduled and automatic substitution phases. This removes substitution behavior from the minute state and keeps substitution behavior in policy/phase components.

Assessment: acceptable.

## Composition per simulation

The previous code at `58ada394` used the instance field `minuteFlow` for `processMinute`. The current `DetailedMatchEngineFlow` creates:

- a field-level `minuteFlow` for debug/helper delegation; and
- a fresh local `simulationMinuteFlow` inside `simulateWithRandomBounded`.

The current simulation path calls `simulationMinuteFlow.processMinute(...)`, which prevents cross-match contamination from shared phase-owned substitution engines.

Assessment: the deferred-substitution contamination risk is corrected for the full-match simulation path.

## Deferred substitution bug

Historical inspection confirms the risky state existed before the closing remediation: the simulation path invoked the instance `minuteFlow.processMinute(...)`. Current code invokes the per-run `simulationMinuteFlow.processMinute(...)`.

Current tests cover manual substitution, snapshot update, result mutation, replay determinism, same-mutation replay, and golden snapshot substitutions.

Assessment: protected enough for current MVP behavior, with a remaining test-depth issue noted below.

## LiveSession

`DetailedLiveSessionTest` and `DetailedLiveSessionInverseReplayTest` cover:

- manual substitution event recording;
- immediate snapshot update;
- snapshot update after tick;
- manual substitution affecting result;
- replay after mutation;
- replay determinism;
- replay prefix preservation;
- replay boundary validation.

Assessment: good behavioral coverage for public session APIs.

Concurrency closure:

- `LiveSessionIsolationStressTest` covers multiple simultaneous `LiveSession` instances.
- It compares concurrent runs against sequential baselines.
- It exercises same-seed sessions with different substitutions.
- It exercises different-seed sessions with different substitutions.
- It verifies replay after mutation and prefix preservation.
- It asserts that each manual substitution appears exactly once in the final engine timeline, live snapshot, and accumulated events.
- It asserts that each session's visible lineup contains only its own substituted player and never another session's substitution players.
- It verifies executor shutdown so the stress harness does not leak live threads.

Assessment: concurrent `LiveSession` replay/mutation coverage is now sufficient for the current MVP.

## Replay

Replay behavior is covered by inverse replay tests and current live-session tests. The tests assert deterministic replay for the same mutation and preserved prefixes across replay boundaries.

Assessment: acceptable for current scope.

## Concurrency

`DetailedMatchMinuteGoldenSnapshotTest` includes:

- parallel simulations across different seeds;
- repeated parallel simulations with duplicate seeds;
- comparison against sequential baselines.

This directly protects against shared mutable state in the detailed match engine.

The new `LiveSessionIsolationStressTest` closes the previous gap by exercising simultaneous live replay/mutation flows with different scheduled substitutions and manager interventions.

## Determinism

Golden snapshots freeze:

- score;
- shots;
- xG;
- possession;
- event counts;
- goals;
- chance events;
- substitutions;
- player stats;
- rating proxy;
- fatigue projection;
- timeline hashes;
- timeline heads and tails.

Assessment: strong determinism signal.

## Golden snapshots

Golden coverage includes balanced, favorite, defensive, and manual-substitution scenarios. The snapshots are detailed enough to detect event-order and player-stat regressions.

Assessment: acceptable.

## Phase-level unit tests

The remediation added direct tests for:

- `MinuteAttackPhase`
- `MinuteAutomaticSubstitutionPhase`
- `MinuteDisciplinePhase`
- `MinuteInjuryPhase`
- `MinutePossessionPhase`
- `MinuteRestartEventPhase`
- `MinuteScheduledSubstitutionPhase`
- `MinuteTacticalStatePhase`

Assessment: good phase-level coverage.

## Architecture tests

`SimulationArchitectureBoundaryTest` now guards:

- no generic `Support`, `Helper`, or `Utils` names in the detailed package;
- phase classes not depending on adapters, infrastructure, web, or persistence packages;
- context/state/input/result classes not depending on composition, policies, engines, or phases;
- absence of `MinutePhysicalStatePhase`;
- no mutable static fields in simulation flows;
- explicit presence of injury and restart phases in the pipeline.

Assessment: strong guardrail for this remediation.

## Guard complexity

Measured line counts for core classes:

- `DetailedMatchEngine`: 95
- `DetailedMatchEngineFlow`: 205
- `DetailedMatchMinuteFlow`: 45
- `DetailedMatchMinutePipeline`: 69
- `DetailedMatchMinuteComposition`: 47
- `MinuteAttackPhase`: 156
- other phase classes: 29 to 55
- `MinuteMatchState`: 31

No relevant class in the audited minute pipeline is a current god class by size or obvious responsibility spread.

Issue: `MinuteAttackPhase` is the largest phase and combines attack probability, opponent aggregation, shot selection, and event emission. It is cohesive enough now, but it is the first candidate for future extraction if shot modeling grows.

## Hexagonal and dependency boundaries

The audited detailed minute phases stay inside the application simulation package and do not depend on web, persistence, adapters, or infrastructure packages according to the architecture guard.

Assessment: acceptable for this pipeline.

## WebFlux and blocking calls

Static searches found blocking and subscription usages elsewhere in the backend, mostly legacy, infrastructure, lifecycle, persistence, and tests. They were not found as minute-pipeline production behavior in the audited detailed simulation flow.

Assessment for this audit scope: acceptable.

Out-of-scope caveat: broader application WebFlux hygiene remains a separate system-wide concern.

## Reflection

Static searches found reflection in unrelated tests, not in the new minute phase tests or audited minute pipeline tests.

Assessment for this audit scope: acceptable.

## Removed obsolete phase

`MinutePhysicalStatePhase` is deleted from production code. Remaining textual references are historical documentation and the architecture guard that asserts it must not return.

Assessment: acceptable.

## Documentation consistency

The remediation documentation exists and records the pipeline closure. This definitive audit adds an independent stricter verdict and the evidence observed in this side audit.

Assessment: acceptable.

## Critical findings

None found within the audited minute-pipeline remediation.

## Important findings

None remaining within the audited minute-pipeline and `LiveSession` concurrency scope.

## Minor findings

1. `MinuteAttackPhase` is cohesive but relatively dense. If future shot, chance, xG, or player-rating logic grows, split it before it becomes a hidden god phase.
2. Some broader backend blocking/reflection findings remain outside this minute-pipeline scope and should not be confused with this remediation's status.

## Final readiness

The minute pipeline is ready for continued feature work, including stamina, injury severity, tactical substitutions, and richer minute-level event modeling.

Feature work should continue with the current phase structure. The prior pre-feature hardening step for concurrent `LiveSession` replay/mutation tests with different scheduled substitutions and manager interventions is complete.

## LiveSession concurrency closure evidence

Closure commits:

- `5c5228aa` Add concurrent LiveSession isolation coverage
- `cd6ce0d8` Prove concurrent replay and deferred substitution safety

Behavior now covered through public APIs only:

- `LiveSession.tick()`
- `LiveSession.recordManualSubstitution(...)`
- `LiveSession.mutateContext(...)`
- `LiveSession.snapshot()`
- `LiveSession.finalResult()`
- `LiveSession.accumulatedEvents()`

Additional production correction:

- live snapshots now deduplicate a manual substitution that is later regenerated by replay as the same logical substitution;
- when the regenerated engine event and manual event represent the same substitution, the visible event keeps the manual event so SSE/detail consumers retain real player names;
- `accumulatedEvents()` keeps the manual substitution observable once and at the end, preserving the existing public contract.

Final verdict after closure: `APPROVED`.
