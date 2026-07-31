# MANAGER - Post-MVP 1 LiveSession Concurrency Definitive Audit

Date: 2026-07-30

Scope: independent technical audit of the LiveSession concurrency closure commits.

Audited repository:

- Root: `D:\ProyectosOpenCode\MANAGER`
- Frontend: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`

Audited commits:

- `5c5228aa` Add concurrent LiveSession isolation coverage
- `cd6ce0d8` Prove concurrent replay and deferred substitution safety
- `ac16c3cf` Close LiveSession concurrency audit

Audited range:

- Base: `15265065`
- Head: `ac16c3cf`

## 1. Verdict

APPROVED WITH ISSUES

The LiveSession concurrency closure is materially sound for the stated inter-session contamination risk. The new coverage exercises multiple concurrent sessions, different seeds, same seeds with different mutations, replay after mutation, distinct deferred substitutions, visible snapshot isolation, final timeline isolation, accumulated-event deduplication, SSE/player-name preservation, and executor cleanup. The backend suite is green with 2503 tests, 0 failures, 0 errors, and 4 skipped.

The audit does not find evidence of shared mutable state across LiveSession instances, shared RNG, shared substitution engine, shared timeline, or cross-session replay contamination.

The remaining issue is not enough to reject the closure, but it prevents an unconditional approval: `LiveSession.finalResult()`, `isFinished()`, `currentMinute()`, `context()`, and `accumulatedEvents()` are not synchronized even though several of them read or mutate state also touched by synchronized operations. The current tests prove the intended inter-session behavior and common observable flows, but they do not prove full thread safety for concurrent calls against the same LiveSession instance. This is a bounded intra-session concurrency concern, not a regression in the completed multi-session isolation goal.

## 2. Commits

Confirmed present in `git log --oneline -12`:

- `ac16c3cf` Close LiveSession concurrency audit
- `cd6ce0d8` Prove concurrent replay and deferred substitution safety
- `5c5228aa` Add concurrent LiveSession isolation coverage
- `15265065` Close minute pipeline cohesion remediation

Commit stats:

- `5c5228aa`: changed `LiveSession.java` and added `LiveSessionIsolationStressTest.java`; 511 insertions, 5 deletions.
- `cd6ce0d8`: changed `LiveSession.java` and `LiveSessionIsolationStressTest.java`; 22 insertions, 1 deletion.
- `ac16c3cf`: added `POST_MVP1_MINUTE_PIPELINE_FINAL_COHESION_DEFINITIVE_AUDIT.md`; 339 insertions.

Range diff `15265065..ac16c3cf`:

- Added: `docs/architecture/POST_MVP1_MINUTE_PIPELINE_FINAL_COHESION_DEFINITIVE_AUDIT.md`
- Modified: `src/main/java/com/footballmanager/application/service/simulation/detailed/LiveSession.java`
- Added: `src/test/java/com/footballmanager/application/service/simulation/detailed/LiveSessionIsolationStressTest.java`

`git diff --check 15265065..ac16c3cf`: passed with no whitespace errors.

## 3. Git

Initial root working tree before this audit report was clean.

No frontend files are changed in the audited range.

The only file created by this independent audit is:

- `docs/architecture/POST_MVP1_LIVE_SESSION_CONCURRENCY_DEFINITIVE_AUDIT.md`

No commits were created by this audit.

## 4. LiveSession

Audited production file:

- `src/main/java/com/footballmanager/application/service/simulation/detailed/LiveSession.java`

Per-session fields:

- `effectiveContext`
- `seed`
- `cachedRandom`
- `cacheIndex`
- `engine`
- `cachedResult`
- `engineTimeline`
- `manualEvents`
- score/current-minute/tick/finished state

Findings:

- Each LiveSession constructs its own `CachingRandomWrapper`.
- Each LiveSession constructs its own `DetailedMatchEngine`.
- Each LiveSession constructs its own `DoubleCacheIndex`.
- Each LiveSession owns its own `engineTimeline` and `manualEvents`.
- No mutable static state controls session timeline, replay, substitutions, RNG, or composition.
- Static state is limited to logger, an integer threshold, and immutable `Set.of(...)` noise event configuration.

Assessment: inter-session state isolation is acceptable.

## 5. Duplicate substitution bug

The reconstructed bug path is:

1. create `LiveSession`;
2. record a manual substitution;
3. mutate context / replay;
4. let the engine regenerate the logical substitution;
5. ask for snapshot/SSE/accumulated events;
6. count substitution events.

The audited fix deduplicates visible substitutions after replay. The tests assert:

- one substitution in final engine timeline;
- one substitution in live snapshot;
- one substitution in accumulated events;
- same outgoing player id;
- same incoming player id;
- same minute;
- final lineup contains incoming player and excludes outgoing player;
- player names from manual event are preserved for SSE/UI.

Focused SSE regression tests pass:

- `DetailedMatchEventPlayerNameMappingTest`
- LiveSession focused tests

Assessment: duplicate substitution bug is fixed for the tested public behavior.

## 6. Deduplication key

The visible-event deduplication key for substitutions is:

- minute;
- event type;
- outgoing player id (`playerId`);
- incoming player id (`relatedPlayerId`).

For non-substitution events it uses the fuller event key:

- minute;
- event type;
- team id;
- player id;
- related player id;
- description;
- xG.

Positive assessment:

- The substitution key is not based on visible text or player names.
- It is stable across replay because it uses ids and minute.
- It distinguishes different incoming/outgoing pairs in the same minute.
- It preserves manual substitution naming because manual events overwrite regenerated engine events in the visible snapshot.

Issue:

- The substitution visible key omits `teamId`. In normal match data, player ids are session/player unique enough for the current implementation, and tests cover home-side substitutions without cross-session contamination. Still, including `teamId` would make the key more explicit and safer for future importers or edge-case ids. This is classified as minor because no current evidence shows collision or corruption.

## 7. Stress

Audited test file:

- `src/test/java/com/footballmanager/application/service/simulation/detailed/LiveSessionIsolationStressTest.java`

The stress coverage includes:

- real `ExecutorService` thread pools;
- `CountDownLatch` start barriers;
- `Future.get(...)` with timeouts;
- JUnit `@Timeout`;
- executor shutdown and await termination;
- 12 parallel scenarios with different seeds;
- 8 parallel scenarios with the same seed and different substitutions;
- repeated same-seed stress via `@RepeatedTest(6)`;
- interleaved two-session test;
- concurrent replay-after-mutation test;
- accumulated-events replay deduplication test;
- observable composition lifetime test.

The test does not use `Thread.sleep` as a synchronization mechanism.

No reflection calls were found in the new stress test.

## 8. Same seeds

Same-seed coverage:

- multiple sessions use seed `777L`;
- each session has a different substitution minute/player pair/style;
- parallel results are compared against sequential baselines;
- substitution keys are asserted distinct;
- timeline hashes differ where mutation differs.

Assessment: same seed + different mutation is covered.

Same seed + same mutation determinism is indirectly covered by sequential-baseline equality and replay determinism in existing LiveSession tests, but not as a separate explicit method in `LiveSessionIsolationStressTest`.

## 9. Different seeds

Different-seed coverage:

- 12 parallel scenarios use seeds `100L + i`;
- substitutions and styles vary;
- parallel output is compared to sequential baseline output per scenario.

Assessment: different seeds remain isolated and deterministic.

## 10. Replay

Replay coverage includes:

- replay after manual substitution;
- replay after tactical/style mutation;
- prefix hash before replay;
- immediate replay hash;
- post-replay hash;
- concurrent replay signatures compared to sequential replay signatures;
- replay boundary validation in existing LiveSession tests.

Assessment: replay does not show cross-session contamination and remains deterministic in the covered flows.

## 11. Deferred substitutions

Deferred substitution behavior is covered by:

- final engine timeline assertion;
- live snapshot assertion;
- accumulated events assertion;
- scheduled substitutions assertion in `MatchContext`;
- visible lineup assertion.

Confirmed:

- one logical substitution per scenario;
- not present in another session;
- not duplicated by snapshot;
- not duplicated by accumulated events;
- preserved after replay;
- final lineup reflects the substitution.

Assessment: acceptable.

## 12. SSE/UI

Audited evidence:

- `DetailedMatchEventPlayerNameMappingTest`
- `LiveSession.buildSnapshot()`
- `LiveSnapshot.allEvents()`

The fix preserves the manual event for visible snapshot/SSE when replay regenerates the same logical substitution. This keeps real outgoing/incoming player names.

The focused player-name mapping tests pass.

No frontend files changed in the audited range.

Assessment: SSE/UI naming contract is preserved.

## 13. Synchronization

Synchronized methods:

- `tick()`
- `snapshot()`
- `recordManualSubstitution(...)`
- `recordTacticalChange(...)`
- `replayFromMinute(...)`
- `mutateContext(...)`

Non-synchronized public methods:

- `finalResult()`
- `isFinished()`
- `currentMinute()`
- `context()`
- `accumulatedEvents()`

Risk assessment:

- Most mutation-heavy live paths are synchronized.
- `finalResult()` can mutate `cachedResult`, goals, and `engineTimeline` without acquiring the same monitor as `tick()` and replay operations.
- `accumulatedEvents()` iterates mutable lists without synchronization.
- This is not shown to cause cross-session contamination and does not fail the current suite, but it is an intra-session race risk if the same LiveSession instance is accessed concurrently through mixed public methods.

Severity: important but bounded. It should be remediated before claiming full same-instance thread safety, but it does not invalidate the closed multi-session isolation work.

## 14. Repetitions

Executed:

- `mvn -q -DskipTests test-compile`
- `LiveSessionIsolationStressTest` repeated 10 separate Maven runs
- focused LiveSession/SSE/minute/golden/architecture test set
- full backend suite `mvn -q test`

Result:

- 10/10 stress repetitions passed
- no observed flakiness
- no observed timeouts
- executor shutdown assertions passed

## 15. Tests

Final Surefire aggregation after full suite:

- Reports: 275
- Tests: 2503
- Failures: 0
- Errors: 0
- Skipped: 4

Comparison to prior 2492 tests:

- Delta: +11 tests
- No new skipped tests observed
- No evidence of deleted tests in the audited range

The +11 count matches the new stress coverage: five regular test methods plus six repetitions from the repeated same-seed stress.

## 16. Architecture

No evidence of reintroduced:

- `MinuteMatchState` owning an engine;
- shared composition in state;
- mutable static simulation state;
- service locator;
- web/Redis/persistence dependency in detailed simulation core;
- singleton stateful LiveSession composition.

Architecture focused tests passed.

Assessment: acceptable for the audited scope.

## 17. Hygiene

Checks:

- `git diff --check 15265065..ac16c3cf`: passed
- `git diff --check`: passed before this report
- frontend working tree: clean before this report
- no code/test/frontend/database/dataset files changed by this audit

The attachment text had mojibake in terminal display, but audited repository files inspected for this closure are readable UTF-8 English documents/source files.

## 18. Documentation

Audited documentation:

- `docs/architecture/POST_MVP1_MINUTE_PIPELINE_FINAL_COHESION_DEFINITIVE_AUDIT.md`

The document now records:

- closure commits;
- test counts;
- LiveSession concurrency closure;
- duplicate substitution fix;
- `APPROVED` verdict.

Independent audit assessment:

- The documentation is directionally correct about inter-session isolation and duplicate substitution closure.
- It is slightly stronger than the code evidence regarding full LiveSession thread safety because it does not mention the remaining non-synchronized public readers/mutator (`finalResult()`).

## 19. Critical findings

None.

## 20. Important findings

1. Intra-session synchronization is incomplete: `finalResult()` mutates state without synchronization, and `accumulatedEvents()` iterates mutable lists without synchronization. The current closure proves inter-session isolation, not full same-instance concurrent access safety.

## 21. Minor findings

1. The visible substitution deduplication key omits `teamId`. The current player ids appear unique enough in tested flows, but including team id would make the key more explicit and future-proof.
2. Same seed + same mutation determinism is covered by existing replay/live tests and baseline comparisons, but the new stress class focuses more strongly on same seed + different mutations.

## 22. Readiness

Ready to continue feature work that depends on multiple simultaneous LiveSession instances and replay/mutation isolation.

Before marketing LiveSession as fully thread-safe per instance, close the intra-session synchronization issue by making public state readers/mutators consistently synchronized or by returning immutable snapshots from a single synchronized path.

## 23. Conclusion

The original important gap in concurrent LiveSession replay/mutation coverage is closed. The duplicate-substitution bug in visible snapshot/SSE behavior is reproduced by tests and corrected. Repeated stress and the full backend suite are green. No cross-session contamination was found.

Final verdict: `APPROVED WITH ISSUES`.

## 24. Same-instance thread-safety remediation status

Date: 2026-07-30

This section records the follow-up remediation performed after the historical audit verdict above. The original verdict and evidence remain unchanged.

Remediation commits:

- `7e1a6f24` Make LiveSession public state access thread safe
- `d003da24` Strengthen substitution event identity
- `839a0581` Add same-instance LiveSession concurrency coverage

Resolved items:

- `LiveSession.finalResult()` now uses the same instance monitor as `tick()`, replay, tactical mutation, and snapshot reads.
- `LiveSession.isFinished()`, `currentMinute()`, `context()`, and `accumulatedEvents()` are synchronized public reads.
- `accumulatedEvents()` returns an immutable snapshot produced under the session monitor.
- The visible substitution deduplication key now includes a stable team component in addition to minute, event type, outgoing player id, and incoming player id.
- Same-instance concurrent coverage now exercises tick/snapshot reads, tick/accumulated-event reads, replay/readers, repeated final-result callers, tick/final-result convergence, and same seed + same mutation concurrent determinism.
- Architecture guards now reject mutable static state, global lock shortcuts, concurrent collection shortcuts, and adapter/infrastructure dependency drift in `LiveSession`.

Validation after remediation:

- backend full suite `mvn -q test`: passed;
- Surefire aggregation: 2517 tests, 0 failures, 0 errors, 4 skipped;
- `git diff --check`: passed;
- frontend remained untouched and clean.

Remediation verdict: `APPROVED`.
