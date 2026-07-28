# V24 large refactor technical closure review

Date: 2026-07-28

Verdict: APPROVED

## Evidence

- Full Maven suite: `mvn -q test`
  - Tests: 2433
  - Failures: 0
  - Errors: 0
  - Skipped: 4
  - Surefire reports: 258
- Compile validation: `mvn -q -DskipTests test-compile`
- Focused validations executed and green:
  - V24 detailed match storage/query/stats/comparison
  - test harness and round controller
  - match simulation orchestrator
  - V24 live mutation path
  - end-of-tournament aggregation

## Closure findings

### Ports and DTO boundaries

Application/domain ports no longer depend on web controller DTOs for auth, lineup, game, and harness flows. Boundary mapping lives in web adapters, while domain/application ports expose command/result/value objects owned by their respective use-case packages.

### WebFlux

Incorrect controller-level manual subscriptions were removed from the live round path. V24 detailed match storage is now reactive end-to-end, including Redis adapter, query services, stats controller, detail controller, comparison service, replay, and reset flows.

Remaining blocking usages are classified:

- `WorldSeedBatchWriter` and `WorldTeamPostgresWriter`: batch/startup seed persistence; not WebFlux request-path orchestration.
- `LeagueSimulator.persistV24Detail`: synchronous league-round simulation API used by batch-style league simulation and legacy tests; bounded to a 5s persistence timeout.
- `ReactiveLifecycleExecutor.subscribe`: single explicit lifecycle boundary for engine callbacks fired outside an HTTP publisher; controller code no longer owns those subscriptions.

### Tests and reflection

Reflection shims were eliminated from the audited lifecycle/discipline/simulation/harness scope. Tests now exercise public reactive behavior by subscribing/blocking in test code where the production API returns a publisher.

### Orchestrator correctness

The match-day orchestrator no longer drops consecutive same-user submissions when they arrive back-to-back. The per-user semaphore now queues work on the orchestrator scheduler instead of returning an empty publisher. This fixed a real final-table regression where the second match day could be skipped.

Final standings are rebuilt from completed fixtures before champion/promotion calculation. This makes end-of-tournament standings robust and independent from stale in-memory aggregate snapshots.

### God classes and cohesion

No production Java file remains above 501 lines, and large V24/test-harness responsibilities have been split into cohesive services and value records rather than generic helper dumps. Remaining high-400-line classes are bounded coordinators or compatibility surfaces with focused responsibilities and green behavioral coverage.

## Final verdict

APPROVED. The refactor closes the previously blocking items: critical WebFlux issues in the live round path, reactive storage composition, test reflection scope, final aggregation correctness, and suite validation.
