# MANAGER - Post-MVP 1 LiveSession Thread-Safety Closure

Date: 2026-07-30

Scope: final remediation of same-instance LiveSession concurrency risks identified by the definitive LiveSession concurrency audit.

## 1. Objective

This closure resolves the bounded same-instance thread-safety issues left after the previous multi-session concurrency approval:

- public state accessors now use the same instance monitor as mutation paths;
- `finalResult()` no longer races with `tick()`, `snapshot()`, replay, or tactical mutation;
- visible accumulated event reads are produced from a synchronized copy path;
- substitution event identity now includes a stable team dimension;
- explicit same seed + same mutation concurrent coverage is present.

No frontend, database, Redis, dataset, minute probability, or match engine probability behavior was changed.

## 2. Previous model

Before this remediation, the heavy mutation paths in `LiveSession` were synchronized, but several public methods were not:

- `finalResult()`;
- `isFinished()`;
- `currentMinute()`;
- `context()`;
- `accumulatedEvents()`.

That left a same-instance race risk because those methods read, and in the case of `finalResult()` also mutated, state also touched by synchronized operations.

The issue was bounded to concurrent calls against the same `LiveSession` instance. It was not evidence of cross-session contamination.

## 3. Final synchronization policy

`LiveSession` now uses one simple policy:

- public mutation methods are synchronized on the instance monitor;
- public state readers are synchronized on the same instance monitor;
- no global lock was introduced;
- no mutable static state was introduced;
- no adapter, infrastructure, Redis, persistence, or web dependency was introduced into the detailed simulation core.

The synchronized public surface now includes:

- `tick()`;
- `snapshot()`;
- `finalResult()`;
- `isFinished()`;
- `currentMinute()`;
- `context()`;
- `accumulatedEvents()`;
- `recordManualSubstitution(...)`;
- `recordTacticalChange(...)`;
- `replayFromMinute(...)`;
- `mutateContext(...)`.

## 4. finalResult behavior

`finalResult()` is now synchronized and idempotent.

When called before the live session reaches minute 90, it completes the same deterministic full-match simulation from the session seed and current effective context, updates the cached final timeline and score, marks the session as finished, and moves the visible current minute to 90.

Repeated calls return the cached final result.

Concurrent calls against the same session converge to one final state without corrupting current minute, score, timeline, or finished state.

## 5. Safe public reads

`accumulatedEvents()` now executes under the same monitor as timeline mutation and returns an immutable snapshot copy.

`isFinished()`, `currentMinute()`, and `context()` are synchronized state reads.

The `MatchContext` object returned by `context()` remains an immutable context reference for the active session state. Lineups, bench lists, and event collections are built through defensive-copy paths in the session model.

## 6. Substitution identity

The visible deduplication key for substitution events now includes:

- minute;
- event type;
- stable team identity;
- outgoing player id;
- incoming player id.

The team identity is normalized against the current match context. If a regenerated engine event carries a non-context team id, the key infers the team from the outgoing player membership in home or away lineups/bench. This preserves replay deduplication while still distinguishing equal player ids across different teams.

This closes the prior future-proofing issue where two teams with overlapping player ids could theoretically collide in visible substitution deduplication.

## 7. Same-instance concurrency coverage

Added coverage exercises the same `LiveSession` instance under concurrent public calls:

- ticking and snapshot readers;
- ticking and accumulated-event readers;
- replay and readers;
- repeated final-result callers;
- ticking and final-result callers converging to one final state;
- same seed + same mutation concurrent sessions compared with sequential baseline.

The tests use public APIs only and coordinate workers through latches, futures, and bounded timeouts. They do not use reflection or `Thread.sleep`.

## 8. Architecture guard

The architecture test suite now includes guards that:

- reject mutable static state in `LiveSession`;
- verify public synchronization for the core same-instance access surface;
- reject global lock/data-structure shortcuts such as `static final Object`, `ReentrantLock`, `ConcurrentHashMap`, and `CopyOnWriteArrayList`;
- reject accidental adapter/infrastructure dependencies in the `LiveSession` source.

## 9. Deadlock and flakiness analysis

The final design uses only the `LiveSession` instance monitor.

No nested global lock was added. No blocking coordination primitive was added to production code. No manual subscription, external executor, or background thread was introduced into the production session path.

Concurrent tests use finite timeouts and executor cleanup checks. Full suite execution passed without deadlock, timeout, or flakiness.

## 10. Validation

Executed backend validation:

- `mvn -q -DskipTests test-compile`: passed;
- focused LiveSession tests: passed;
- substitution event identity tests: passed;
- same-instance concurrency tests: passed;
- detailed simulation tests: passed;
- architecture tests: passed;
- full backend suite `mvn -q test`: passed.

Final Surefire aggregation:

- tests: 2517;
- failures: 0;
- errors: 0;
- skipped: 4;
- reports: 277.

Additional hygiene checks:

- no reflection, `Thread.sleep`, or private invocation patterns in the new concurrency/identity tests;
- no global lock or concurrent collection shortcut in production `LiveSession`;
- `git diff --check`: passed.

Frontend was not modified and remained clean.

## 11. Remaining risk

No critical or important risk remains in the requested same-instance LiveSession thread-safety scope.

General product risks outside this closure remain unchanged:

- future engine features that add mutable state to `LiveSession` must follow the same instance-monitor policy;
- future visible event types with special replay semantics should define explicit identity keys;
- concurrency tests should remain part of the regression suite.

## 12. Verdict

APPROVED

The same-instance LiveSession thread-safety gap is closed. Public state access is synchronized consistently, accumulated events are safely exposed, final result computation is idempotent under concurrent callers, substitution event identity includes team context, and explicit same seed + same mutation concurrent coverage is present. The backend suite is green and no frontend, database, dataset, or probability behavior was changed.
