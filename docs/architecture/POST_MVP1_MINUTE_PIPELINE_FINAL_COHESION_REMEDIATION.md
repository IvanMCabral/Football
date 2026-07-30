# Post-MVP 1 minute pipeline final cohesion remediation

Date: 2026-07-30

## Scope

This closure addresses the residual issues from
`POST_MVP1_MINUTE_PIPELINE_DEFINITIVE_INDEPENDENT_AUDIT.md`.

The work is intentionally limited to the backend detailed match minute
pipeline. It does not modify LeagueSimulator, Redis, frontend, database,
datasets, public API contracts, or request/response compatibility.

## Changes

### Cohesive minute phases

`MinutePhysicalStatePhase` was removed. Its responsibilities are now explicit:

- `MinuteInjuryPhase`: injury selection, injury model evaluation, player state
  mutation, and injury timeline event.
- `MinuteRestartEventPhase`: corner and offside restart-style events, with the
  same event order and defensive-style offside suppression as before.

The minute pipeline order remains behavior-preserving:

1. scheduled substitutions;
2. tactical state;
3. possession and fatigue drain;
4. attack resolution;
5. discipline;
6. injuries;
7. restart events;
8. automatic substitutions.

### Minute state cohesion

`MinuteMatchState` now carries only per-match mutable state:

- random source;
- home and away team states;
- timeline;
- home and away selectors;
- applied scheduled substitution ids.

It no longer carries `SubstitutionEngine` or other behavior services.
Scheduled and automatic substitution engines are provided through
`MinuteSubstitutionPolicies`, keeping behavior dependencies in the composition
boundary instead of the minute state.

### Phase-level tests

Each minute phase now has direct behavioral coverage:

- `MinuteScheduledSubstitutionPhaseTest`
- `MinuteTacticalStatePhaseTest`
- `MinutePossessionPhaseTest`
- `MinuteAttackPhaseTest`
- `MinuteDisciplinePhaseTest`
- `MinuteInjuryPhaseTest`
- `MinuteRestartEventPhaseTest`
- `MinuteAutomaticSubstitutionPhaseTest`

The tests verify observable state and timeline effects through package-visible
application behavior, without reflection or private compatibility shims.

### Golden and concurrency coverage

`DetailedMatchMinuteGoldenSnapshotTest` now freezes:

- score, shots, xG, possession, event totals and timeline hash;
- per-player event distributions;
- a deterministic rating proxy derived from timeline events;
- projected fatigue impact by team style;
- concurrent replay isolation;
- repeated concurrent simulations using the same seeds.

### Architecture guardrails

`SimulationArchitectureBoundaryTest` now enforces:

- detailed minute phases do not depend on adapters, infrastructure, Spring, or
  SQL;
- context/state/input/result classes do not hold services, engines, phases,
  policies, or minute composition;
- `MinutePhysicalStatePhase` cannot be reintroduced;
- the pipeline must explicitly use `MinuteInjuryPhase` and
  `MinuteRestartEventPhase`;
- minute pipeline methods remain small enough to audit.

## Validation evidence

Focused validation completed successfully:

- `mvn -q -DskipTests test-compile`
- `mvn -q "-Dtest=com.footballmanager.application.service.simulation.detailed.Minute*PhaseTest,com.footballmanager.application.service.simulation.detailed.DetailedMatchMinuteGoldenSnapshotTest,com.footballmanager.application.architecture.SimulationArchitectureBoundaryTest" test`
- `mvn -q "-Dtest=com.footballmanager.application.architecture.SimulationArchitectureBoundaryTest" test`

Full-suite validation is recorded in
`POST_MVP1_MINUTE_PIPELINE_FINAL_COHESION_AUDIT.md`.

## Commits

- `80751206 Separate minute injury and restart event phases`
- `b1ef2d14 Remove substitution behavior from minute state`
- `5c5a8114 Add unit coverage for detailed match minute phases`
- `58ada394 Extend detailed match golden and concurrency coverage`

The final documentation and audit closure are committed separately.
