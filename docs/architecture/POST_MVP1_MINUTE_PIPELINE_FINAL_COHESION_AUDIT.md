# Post-MVP 1 minute pipeline final cohesion audit

Date: 2026-07-30

## Verdict

APPROVED

## Scope audited

Backend detailed match minute pipeline after the final cohesion remediation.
Frontend, Redis, database, datasets, LeagueSimulator, and public API contracts
were intentionally left untouched.

## Evidence

### Cohesion

- `MinutePhysicalStatePhase` is no longer present in production code.
- Injury behavior is isolated in `MinuteInjuryPhase`.
- Corner/offside restart events are isolated in `MinuteRestartEventPhase`.
- `DetailedMatchMinutePipeline` remains a small ordered coordinator.
- `DetailedMatchMinuteFlow` remains a thin composition entry point.

Current representative line counts:

| Class | Lines |
|---|---:|
| `DetailedMatchMinutePipeline` | 69 |
| `DetailedMatchMinuteFlow` | 45 |
| `MinuteInjuryPhase` | 35 |
| `MinuteRestartEventPhase` | 46 |
| `MinuteMatchState` | 31 |

No relevant god class remains in the minute pipeline.

### State and dependencies

- `MinuteMatchState` no longer owns `SubstitutionEngine`.
- `MinuteMatchState`, minute input, context, and result objects are guarded
  against dependencies on composition, policies, engines, phases, adapters,
  infrastructure, Spring, and SQL.
- Simulation architecture tests keep dependencies pointing inward.

### Test coverage

Specific phase tests exist for all minute phases:

- scheduled substitutions;
- tactical state;
- possession and fatigue drain;
- attack resolution;
- discipline;
- injuries;
- restart events;
- automatic substitutions.

Golden coverage now includes player event distributions, deterministic rating
proxy, fatigue projection, timeline hash, and repeated same-seed concurrency.

### Behavior preservation

The golden timeline hashes for the existing canonical scenarios remain stable
after the phase split and state cleanup:

- `balanced-42`: `bf1952ad201ac8a44f874e9df636c8ec359cca3b49b2863267e71cd00d8ca33c`
- `favorite-7`: `7908e9cc954e7e2bb2c7c930da4c35bb324154e9bca18e6d4e8d983933d9965d`
- `defensive-99`: `871f2fdbddfa5d24d63918aff3cf228018337045828c8da8c902e81236ab6f8a`
- `manual-sub-12345`: `ee47c33d2c9ea4e8ab9b8a5294d4221c51adad92c3fd8eb4a17b1b7d30d42d43`

## Validation commands

Focused commands:

- `mvn -q -DskipTests test-compile`
- `mvn -q "-Dtest=com.footballmanager.application.service.simulation.detailed.Minute*PhaseTest,com.footballmanager.application.service.simulation.detailed.DetailedMatchMinuteGoldenSnapshotTest,com.footballmanager.application.architecture.SimulationArchitectureBoundaryTest" test`
- `mvn -q "-Dtest=com.footballmanager.application.architecture.SimulationArchitectureBoundaryTest" test`

Final commands:

- `mvn -q test`
- `git diff --check`
- `git status --short`
- frontend status check at `D:\ProyectosOpenCode\MANAGER\front-ciber\project`

Final full-suite result:

- Surefire reports: 274
- Tests: 2492
- Failures: 0
- Errors: 0
- Skipped: 4

Repository hygiene:

- `git diff --check`: no output.
- Root `git status --short`: clean after final commit.
- Frontend `git status --short`: clean.

## Final conclusion

The remaining issues from the previous independent audit are resolved:

- the mixed physical-state phase is gone;
- minute state does not carry substitution behavior;
- every phase has focused tests;
- golden coverage includes fatigue and rating-sensitive evidence;
- concurrency includes repeated same-seed simulations;
- architecture tests prevent the known regressions from returning.

Final verdict: APPROVED.
