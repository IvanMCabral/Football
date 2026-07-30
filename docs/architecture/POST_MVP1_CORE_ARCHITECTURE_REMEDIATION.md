# Post-MVP 1 Core Architecture Remediation

Date: 2026-07-30

## 1. Baseline

Baseline was preserved before productive changes:

- initial Git status contained only the untracked audit report under `docs/architecture`;
- `mvn -q -DskipTests test-compile` passed;
- baseline full backend suite from Surefire reports: 2453 tests, 0 failures, 0 errors, 4 skipped;
- focused simulation/lineup/runtime suite was executed; the first isolated runtime sample exposed a Brazil coverage failure caused by the local test dataset state, while the complete suite remained green.

The baseline audit was committed separately:

- `73b616fa docs: record post-MVP 1 code quality audit`

## 2. Original P1 problems

The independent audit marked these backend-core areas as P1:

1. `DetailedMatchEngine` manually created a large collaborator graph and mixed public engine entry points with low-level minute simulation rules.
2. `LeagueSimulator` mixed fixture simulation, detailed-match persistence, career mutations and lifecycle concerns.
3. Redis adapters could convert infrastructure/deserialization failures into empty results.
4. Sync/reactive boundaries were implicit.
5. Architecture tests did not pin the simulation boundaries.

Frontend, dataset import, DB contents, CORS/security and test harness work were intentionally left untouched.

## 3. Before/after map

| Area | Before | After |
| --- | --- | --- |
| Detailed engine public class | simulation flow plus collaborator construction plus debug seams | 95-line facade delegating to `DetailedMatchEngineFlow` |
| Detailed engine flow | hidden inside public engine | cohesive package-private flow collaborator |
| League simulator persistence | built and blocked detail persistence inline | delegated to `MatchDetailPersistenceCoordinator` |
| League simulator career mutation | applied mutation and tracking inline | delegated to `CareerMutationCoordinator` |
| Redis detail reads | per-key read errors were skipped in career scans | failures propagate as `RedisStateAccessException` |
| Redis baseline reads | connection errors could become empty optional | failures propagate as `RedisStateAccessException`; real missing keys still return empty |
| Pending match commands | save/delete/corrupt payload failures were hidden | failures propagate as `RedisStateAccessException`; real missing key returns empty list |
| Architecture guards | only broad application boundary existed | simulation-specific boundary test added |

## 4. DetailedMatchEngine

`DetailedMatchEngine` is now a public facade. It owns:

- public simulation entry points;
- compatibility/debug seams already used by tests;
- conversion of tactical debug records from the flow.

It no longer owns:

- possession/chance/event minute loop;
- shot/fatigue/card/injury/tactical collaborator construction;
- detailed tactical rule implementation;
- mutable per-match state.

The detailed rule flow remains in `DetailedMatchEngineFlow`, which is package-private and isolated from Spring, Redis, JDBC, web DTOs and infrastructure.

Metrics:

| Class | Lines | Primary responsibility |
| --- | ---: | --- |
| `DetailedMatchEngine` | 95 | facade / public engine API |
| `DetailedMatchEngineFlow` | 423 | deterministic minute simulation flow |

`DetailedMatchEngine` is no longer a P1 god-class candidate. The flow is still large, but cohesive around deterministic minute simulation and protected by equivalence tests.

## 5. LeagueSimulator

`LeagueSimulator` remains the round coordinator, but it no longer contains the detailed persistence or career mutation bodies.

Extracted collaborators:

- `MatchDetailPersistenceCoordinator`: builds and persists `DetailedMatchData` at the explicit synchronous league/batch boundary.
- `CareerMutationCoordinator`: collects participation, applies injury/fatigue/discipline/form mutation results and tracks new injuries/suspensions.

Metrics:

| Class | Lines | Responsibility |
| --- | ---: | --- |
| `LeagueSimulator` | 313 | coordinate round fixtures and choose simulation engine |
| `MatchDetailPersistenceCoordinator` | 117 | detailed-match persistence boundary |
| `CareerMutationCoordinator` | 136 | career mutation and participation tracking |

`LeagueSimulator` is no longer a P1 god-class candidate for this scope because persistence and mutation responsibilities are explicit collaborators.

## 6. Sync/reactive policy

Policy established for this scope:

### Request path

- Controllers and use cases should return publishers and avoid manual `subscribe`.
- Redis read/write failures should surface as errors unless the operation is explicitly optional.

### Batch/admin

- `simulateLeagueRound` is a synchronous league/batch workflow.
- The detailed match persistence block remains isolated in `MatchDetailPersistenceCoordinator` with a 5-second timeout.
- The block is documented as a batch boundary and is not part of a WebFlux request pipeline.

### Fire-and-forget

- No new fire-and-forget path was added.
- Existing lifecycle fire-and-forget work remains outside this P1 slice.

## 7. Redis error policy

Redis policy after remediation:

| Scenario | Result |
| --- | --- |
| Key does not exist | empty optional/list where the domain contract allows it |
| Redis connection failure | `RedisStateAccessException` |
| Redis read timeout/failure | `RedisStateAccessException` |
| Corrupt detailed match payload | `RedisStateAccessException` |
| Corrupt pending command payload | `RedisStateAccessException` |
| Save/delete failure | `RedisStateAccessException` or existing baseline-specific persistence exception |

Tests now cover missing keys, connection failures, corrupt payloads, save failure and delete failure.

## 8. Ports

No broad Reactor-port migration was performed in this phase. The audit scope required care rather than a blind migration.

Decision:

- Existing reactive ports stay stable for now to avoid broad contract churn after MVP 1 closure.
- The convention is documented: pure domain should not grow new Reactor ports; reactive ports belong in application-level contracts when touched.
- A future backlog item remains to relocate remaining `domain.ports` reactive interfaces in a dedicated port-boundary migration.

## 9. Equivalence tests

Equivalence was protected by existing characterization tests and focused suites:

- `DetailedMatchEngine*Test`;
- detailed timeline/shot/substitution tests;
- `LeagueSimulatorTest`;
- `DetailedLeague*Test`;
- detailed lifecycle, injury, discipline and form tests;
- detailed career mutation tests;
- Redis adapter failure behavior tests.

The detailed engine extraction preserves the same flow implementation in a package-private collaborator, so deterministic seed behavior remains protected by the existing determinism tests.

## 10. Architecture tests

Added:

- `SimulationArchitectureBoundaryTest`

It enforces:

- simulation core does not depend on web adapters, Redis/JDBC concrete APIs or infrastructure packages;
- `DetailedMatchEngine` remains a facade over flow and does not reabsorb detailed rule services or the minute loop;
- `LeagueSimulator` delegates detailed persistence and career mutation responsibilities.

Existing:

- `ApplicationLayerBoundaryTest`

Both architecture tests pass.

## 11. Metrics

| Class | Lines after | Assessment |
| --- | ---: | --- |
| `DetailedMatchEngine` | 95 | facade, no longer P1 |
| `DetailedMatchEngineFlow` | 423 | cohesive simulation flow, not public orchestrator |
| `LeagueSimulator` | 313 | round coordinator, no longer owns persistence/mutation bodies |
| `MatchDetailPersistenceCoordinator` | 117 | cohesive persistence boundary |
| `CareerMutationCoordinator` | 136 | cohesive mutation/tracking collaborator |

The remediation did not chase arbitrary line thresholds; responsibility and coupling were the exit criteria.

## 12. Risks remaining

- `DetailedMatchEngineFlow` is still a dense deterministic simulation flow. Future tactical/injury work should extract cohesive policy objects only when behavior changes demand it.
- Broad relocation of reactive ports out of `domain.ports` remains a separate migration.
- Legacy seed/importer, frontend harness and security/CORS were intentionally out of scope.

## 13. Verdict

Final validation after remediation:

- `mvn -q -DskipTests test-compile`: passed;
- architecture and focused simulation/Redis/runtime tests: passed;
- full backend suite: 2463 tests, 0 failures, 0 errors, 4 skipped;
- frontend repository: clean and untouched.

Core backend P1 remediation for simulation composition, league lifecycle separation, explicit Redis failure behavior and architecture boundaries is complete.

Verdict: `APPROVED`.
