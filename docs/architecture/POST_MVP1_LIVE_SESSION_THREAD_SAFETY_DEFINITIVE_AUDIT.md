# MANAGER - LiveSession Same-Instance Thread-Safety Definitive Audit

Date: 2026-07-31

Scope: independent read-only audit of the LiveSession same-instance thread-safety closure.

Audited repository:

- Root: `D:\ProyectosOpenCode\MANAGER`
- Frontend: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`

Audited head:

- `ad77040e` Close LiveSession thread-safety audit

Audited remediation commits:

- `7e1a6f24` Make LiveSession public state access thread safe
- `d003da24` Strengthen substitution event identity
- `839a0581` Add same-instance LiveSession concurrency coverage
- `ad77040e` Close LiveSession thread-safety audit

## 1. Verdict

REJECTED

The implemented synchronization changes substantially improve the same-instance `LiveSession` model: the public session methods now share the instance monitor, `finalResult()` is synchronized and idempotent in the tested flows, `accumulatedEvents()` copies under lock and returns an unmodifiable list, substitution deduplication now includes team identity, and new same-instance concurrency tests exercise real parallel access.

However, the audit cannot approve the closure because two blocking criteria are not satisfied:

1. `LiveSession.context()` still returns the live `MatchContext` reference. `MatchContext` defensively copies its collections, but it stores and returns mutable `SessionTeam` and `SessionPlayer` entity instances. A caller can obtain those objects through `context()` and mutate them outside the `LiveSession` monitor through public setters.
2. The full backend suite did not pass in this audit run. `mvn -q test` failed because Redis was not reachable at `localhost:6379`, producing 200 Redis connection errors. The declared green suite could not be independently reproduced.

Per the requested approval rules, either of those findings prevents `APPROVED`.

## 2. Git and commits

Commands executed:

- `git status --short`
- `git -C front-ciber\project status --short`
- `git log --oneline -12`
- `git show --stat --oneline 7e1a6f24`
- `git show --stat --oneline d003da24`
- `git show --stat --oneline 839a0581`
- `git show --stat --oneline ad77040e`
- `git diff ac16c3cf..ad77040e --name-status`
- `git diff --check ac16c3cf..ad77040e`

Commit order confirmed:

- `ad77040e` Close LiveSession thread-safety audit
- `839a0581` Add same-instance LiveSession concurrency coverage
- `d003da24` Strengthen substitution event identity
- `7e1a6f24` Make LiveSession public state access thread safe
- `ac16c3cf` Close LiveSession concurrency audit

Commit stats:

- `7e1a6f24`: modified `LiveSession.java`; 14 insertions, 6 deletions.
- `d003da24`: modified `LiveSession.java`; added `LiveSessionSubstitutionEventIdentityTest.java`; 229 insertions.
- `839a0581`: modified `SimulationArchitectureBoundaryTest.java`; added `LiveSessionSameInstanceConcurrencyTest.java`; 478 insertions.
- `ad77040e`: added three architecture documents; 747 insertions.

Range `ac16c3cf..ad77040e`:

- added `docs/architecture/POST_MVP1_LIVE_SESSION_CONCURRENCY_DEFINITIVE_AUDIT.md`;
- added `docs/architecture/POST_MVP1_LIVE_SESSION_THREAD_SAFETY_CLOSURE.md`;
- added `docs/architecture/POST_MVP1_LIVE_SESSION_THREAD_SAFETY_FINAL_AUDIT.md`;
- modified `src/main/java/com/footballmanager/application/service/simulation/detailed/LiveSession.java`;
- modified `src/test/java/com/footballmanager/application/architecture/SimulationArchitectureBoundaryTest.java`;
- added `src/test/java/com/footballmanager/application/service/simulation/detailed/LiveSessionSameInstanceConcurrencyTest.java`;
- added `src/test/java/com/footballmanager/application/service/simulation/detailed/LiveSessionSubstitutionEventIdentityTest.java`.

`git diff --check ac16c3cf..ad77040e`: passed.

Root working tree before writing this report was not clean because `docs/architecture/POST_MVP1_LIVE_SESSION_THREAD_SAFETY_DEFINITIVE_AUDIT.md` already existed as the single untracked allowed audit artifact.

Frontend working tree was clean.

## 3. Method-by-method audit

Audited file:

- `src/main/java/com/footballmanager/application/service/simulation/detailed/LiveSession.java`

### `tick()`

- Reads: `finished`, `currentMinute`, `effectiveContext`, cached random/index, cached result/timeline, manual events through internal snapshot paths.
- Modifies: `currentMinute`, `ticksRun`, score fields, `cachedResult`, `cachedResultFinal`, `engineTimeline`, `finished`.
- Lock: `synchronized` instance method.
- Return type: `LiveSnapshot`, built under the same monitor.
- Race assessment: mutually exclusive with replay, mutation, `finalResult()`, snapshot, scalar readers, and accumulated-event reads.
- Memory visibility: guaranteed by monitor enter/exit.

### `snapshot()`

- Reads: current minute, score, context, timeline, manual events.
- Modifies: no session fields directly.
- Lock: `synchronized` instance method.
- Return type: snapshot value assembled while holding the monitor.
- Race assessment: cannot observe concurrent partial mutation from tick/replay/finalResult.
- Memory visibility: guaranteed by monitor enter/exit.

### `finalResult()`

- Reads: `cachedResult`, `cachedResultFinal`, `effectiveContext`, random state.
- Modifies: `cachedResult`, `cachedResultFinal`, score fields, `engineTimeline`, `currentMinute`, `ticksRun`, `finished`.
- Lock: `synchronized` instance method.
- Return type: cached `DetailedMatchResult`.
- Race assessment: mutually exclusive with tick, replay, mutation, snapshot, and readers.
- Memory visibility: guaranteed by monitor enter/exit.

### `isFinished()`

- Reads: `finished`.
- Modifies: nothing.
- Lock: `synchronized` instance method.
- Return type: instantaneous scalar.
- Race assessment: mutually exclusive with writers.
- Memory visibility: guaranteed by monitor enter/exit.

### `currentMinute()`

- Reads: `currentMinute`.
- Modifies: nothing.
- Lock: `synchronized` instance method.
- Return type: instantaneous scalar.
- Race assessment: mutually exclusive with writers.
- Memory visibility: guaranteed by monitor enter/exit.

### `context()`

- Reads: `effectiveContext`.
- Modifies: nothing.
- Lock: `synchronized` instance method.
- Return type: live `MatchContext` reference.
- Race assessment: the reference read is synchronized, but the returned object graph is not fully immutable.
- Memory visibility: monitor enter/exit safely publishes the reference itself, but callers can mutate nested entities outside the monitor.

### `accumulatedEvents()`

- Reads: `engineTimeline`, `manualEvents`, current visible dedup identity.
- Modifies: no session fields.
- Lock: `synchronized` instance method.
- Return type: `Collections.unmodifiableList(combined)`, where `combined` is a new list.
- Race assessment: no concurrent iteration of mutable session lists while another synchronized method mutates them.
- Memory visibility: guaranteed by monitor enter/exit.

### `recordManualSubstitution(...)`

- Reads: `finished`, current `effectiveContext`, current manual events and visible key paths.
- Modifies: `manualEvents`, `effectiveContext`, cached result/timeline state through replay preparation paths.
- Lock: `synchronized` instance method.
- Return type: void.
- Race assessment: mutually exclusive with all other public methods.
- Memory visibility: guaranteed by monitor enter/exit.

### `recordTacticalChange(...)`

- Reads: `finished`, current session state.
- Modifies: `manualEvents`.
- Lock: `synchronized` instance method.
- Return type: void.
- Race assessment: mutually exclusive with all other public methods.
- Memory visibility: guaranteed by monitor enter/exit.

### `replayFromMinute(...)`

- Reads: `finished`, `effectiveContext`, current timeline/manual events.
- Modifies: cached result, score fields, `engineTimeline`, replay-related state.
- Lock: `synchronized` instance method.
- Return type: void.
- Race assessment: mutually exclusive with tick, snapshot, `finalResult()`, mutation, and readers.
- Memory visibility: guaranteed by monitor enter/exit.

### `mutateContext(...)`

- Reads: `finished`, `effectiveContext`.
- Modifies: `effectiveContext`; may affect cached replay state.
- Lock: `synchronized` instance method.
- Return type: void.
- Race assessment: mutually exclusive with all other public methods while applying the passed mutator.
- Memory visibility: guaranteed by monitor enter/exit.

## 4. Lock model

The final lock model is simple and understandable:

- one monitor per `LiveSession` instance;
- all public methods are synchronized;
- no volatile/atomic/concurrent collection mixture was introduced for session state;
- no global lock was introduced;
- no shared mutable static session state was found;
- no `ReentrantLock`, `ConcurrentHashMap`, or `CopyOnWriteArrayList` shortcut was found in `LiveSession`.

This is a coherent improvement over the previous mixed state where writers were synchronized but some readers and `finalResult()` were not.

## 5. finalResult

`finalResult()` is now synchronized and guarded by `cachedResultFinal`.

Positive findings:

- it is mutually exclusive with `tick()`, replay, mutation, snapshot, and readers;
- repeated calls after finalization return the same cached object;
- concurrent callers are tested through `sameInstanceFinalResultIsIdempotentUnderConcurrentCallers`;
- tick/finalResult convergence is tested through `sameInstanceTickAndFinalResultConvergeToOneFinalState`;
- no partial `cachedResult` publication was found because the method holds the monitor for the full construction/update sequence.

Concern:

- `finalResult()` holds the session monitor while running a full deterministic simulation. This can block readers of the same session for the duration of full simulation. In the current implementation this is not a deadlock by itself and the test suite did not time out, but it is a performance/latency concern for live UI readers if full simulation becomes expensive.

Assessment: thread-safety accepted; latency risk is minor.

## 6. accumulatedEvents

`accumulatedEvents()` now:

- runs under the session monitor;
- builds a new combined list;
- filters replay duplicates using visible event keys;
- appends manual events only when a visible duplicate is absent;
- returns `Collections.unmodifiableList(combined)`.

Positive findings:

- the session-owned `engineTimeline` and `manualEvents` lists are not iterated without the monitor;
- callers cannot add to the returned list;
- new tests assert immutable returned lists;
- deduplication remains centralized through `visibleEventKey`.

Assessment: accepted for list-level safety.

Limit:

- the returned list is an immutable list container, but its `DetailedMatchEvent` elements must remain value-like for deep immutability. The audited code treats them as records/value objects.

## 7. context

`context()` is the main remaining blocker.

`LiveSession.context()` returns `effectiveContext` directly.

`MatchContext` itself has final fields and defensively copies:

- starting-player lists;
- bench-player lists;
- slot maps;
- manual substitutions.

But `MatchContext` also stores and returns:

- `SessionTeam homeTeam`;
- `SessionTeam awayTeam`;
- lists containing `SessionPlayer` instances.

`SessionTeam` exposes public setters such as:

- `setSessionTeamId`;
- `setName`;
- `setCountry`;
- `setBudget`;
- `setFormation`;
- `setStyle`;
- `setMorale`;
- `setReputation`.

`SessionPlayer` exposes public setters such as:

- `setName`;
- `setPosition`;
- `setAttack`;
- `setDefense`;
- `setTechnique`;
- `setSpeed`;
- `setStamina`;
- `setEnergy`;
- `setForm`;
- `setInjured`;
- `setYellowCards`;
- `setRedCards`;
- `setSuspended`.

Therefore, a caller can execute:

1. `MatchContext context = liveSession.context();`
2. `context.homeTeam().setStyle(...)` or mutate a player from `context.homeStartingPlayers().get(0).setStamina(...)`

Those mutations happen outside the `LiveSession` monitor and bypass `mutateContext(...)`.

Caller search found production usages:

- `SubstitutionCommandUseCaseImpl`;
- `TacticalChangeService`;
- `MatchSession`.

The production usages observed during this audit mostly read the context or pass it to controlled service logic, but the API still exposes a mutable object graph.

Assessment: important finding. This violates the requested criterion that `context()` must not expose mutable internal state and prevents approval.

## 8. Readers

`isFinished()` and `currentMinute()` are synchronized scalar readers.

Positive findings:

- visibility is consistent through the monitor;
- values are not read through unsynchronized paths in these methods;
- tests assert valid minute range and finished implies minute 90.

Assessment: accepted.

Minor test limitation:

- monotonicity is only checked per reader loop under concurrent access. Because replay can intentionally reset/rebuild state, a universal monotonic guarantee is not generally valid for all concurrent replay scenarios. The test does not overclaim this for replay paths.

## 9. Deduplication key

`visibleEventKey(DetailedMatchEvent event)` now special-cases substitutions with:

- minute;
- event type;
- normalized substitution team key;
- outgoing player id;
- incoming player id.

It does not use:

- player names;
- localized text;
- description.

`visibleSubstitutionTeamKey(...)` first uses `event.teamId()` when it matches the current context home/away id. If not, it infers home or away from player membership in the current context. This supports replay-generated events whose raw team id may not match the live context id.

Tests cover:

- same player ids on different teams remain distinct;
- same team, same minute, different pairs remain distinct;
- exact replay duplicate keeps the manual event once;
- accumulated events are immutable snapshots.

Assessment: accepted.

Minor limitation:

- the request asked to explicitly distinguish manual and automatic events when they have the same improbable pair. The current dedup design intentionally collapses exact logical duplicates to preserve visible replay behavior. This is acceptable for replay duplicates but should be documented as product semantics: same team/minute/out/in substitution is considered the same visible logical substitution.

## 10. Same-instance tests

Audited file:

- `src/test/java/com/footballmanager/application/service/simulation/detailed/LiveSessionSameInstanceConcurrencyTest.java`

Coverage present:

- tick vs snapshot readers;
- tick vs accumulated-event readers;
- replay vs snapshot/event/scalar readers;
- concurrent `finalResult()` callers;
- tick vs `finalResult()`;
- same seed + same mutation across concurrent sessions compared with sequential baseline.

The tests use:

- `ExecutorService`;
- `CountDownLatch`;
- `Future.get` with timeouts;
- JUnit `@Timeout`;
- public APIs only.

Search result:

- no `Thread.sleep`;
- no `setAccessible`;
- no `getDeclaredMethod`;
- no `getDeclaredField`;
- no reflective private invocation.

Assessment: materially real same-instance coverage.

Limitations:

- `sameInstanceTickAndFinalResultConvergeToOneFinalState` selects the ticking worker by checking whether the thread name ends with `"1"`. In the observed JDK executor naming this provides one ticker, but it is more brittle than passing an explicit worker index.
- The tests focus on absence of exceptions, duplicate logical events, and final convergence. They do not prove deep immutability of `context()`.

## 11. Same seed and same mutation

The test `sameSeedSameMutationConcurrentSessionsMatchSequentialBaseline` explicitly creates:

- multiple `LiveSession` instances;
- same seed;
- same context fixture;
- same manual substitution;
- same tactical mutation;
- parallel execution;
- comparison to sequential baseline through goals, final timeline hash, snapshot hash, and active lineup key.

The helper sets deterministic session team ids in fixtures, reducing incidental randomness in the comparison.

Assessment: accepted.

## 12. Stress

Stress evidence:

- 10 repeated executions of `LiveSessionSameInstanceConcurrencyTest` passed.
- Focused combined command for same-instance, isolation stress, substitution identity, SSE names, and architecture passed.
- Executors use shutdown and await-termination assertions.
- Futures use finite timeouts.
- Exceptions propagate through `Future.get`.

Observed result:

- no deadlocks;
- no timeouts;
- no `ConcurrentModificationException`;
- no residual executor failure.

Assessment: accepted for the focused same-instance scope.

## 13. Deadlocks

Production `LiveSession` uses one instance monitor.

No evidence found of:

- I/O inside `LiveSession`;
- Redis calls inside `LiveSession`;
- DB calls inside `LiveSession`;
- futures waited inside `LiveSession`;
- callbacks registered inside `LiveSession`;
- locks of multiple sessions acquired together;
- global lock acquisition.

Reentrancy is present because synchronized public methods call private helpers, and public tests may call synchronized methods in sequence. This is ordinary Java monitor reentrancy and not a deadlock finding.

Concern:

- `mutateContext(UnaryOperator<MatchContext> mutator)` executes caller-supplied code while holding the session monitor. Existing production use appears controlled, but unknown future mutators could do slow work or call external code under lock. This is not an immediate failure in the audited commits but should be tightened by convention or API design.

## 14. Public immutability

Accepted:

- `accumulatedEvents()` returns an unmodifiable list copy.
- `MatchContext` returns unmodifiable list/map containers.
- `manualSubstitutions()` returns an unmodifiable list.
- New tests attempt to modify returned accumulated-event lists.

Not accepted:

- `context()` returns a live `MatchContext` containing mutable `SessionTeam` and `SessionPlayer` instances.
- The unmodifiable player lists prevent list structural mutation, but not mutation of contained player objects.

Assessment: important finding.

## 15. Architecture

Audited file:

- `src/test/java/com/footballmanager/application/architecture/SimulationArchitectureBoundaryTest.java`

Positive coverage:

- ArchUnit rule rejects mutable static fields in `LiveSession`;
- source guard checks synchronized `tick`, `finalResult`, and `accumulatedEvents`;
- source guard rejects `static final Object`, `ReentrantLock`, `ConcurrentHashMap`, `CopyOnWriteArrayList`;
- source guard rejects textual adapter/infrastructure markers;
- existing rules protect simulation core boundaries.

Limitations:

- the synchronization guard is partly textual and could be eluded by formatting or equivalent code changes;
- it does not verify all public methods are synchronized;
- it does not detect mutable entity exposure through `context()`;
- it does not deeply verify collection immutability or object graph immutability.

Assessment: useful but incomplete. It should not be treated as full architectural proof.

## 16. Determinism and regression

Executed/reviewed:

- focused same-instance tests;
- isolation stress;
- substitution identity;
- SSE player-name mapping;
- architecture boundary tests.

Focused command passed:

- `mvn -q "-Dtest=com.footballmanager.application.service.simulation.detailed.LiveSessionSameInstanceConcurrencyTest,com.footballmanager.application.service.simulation.detailed.LiveSessionIsolationStressTest,com.footballmanager.application.service.simulation.detailed.LiveSessionSubstitutionEventIdentityTest,com.footballmanager.adapters.in.web.career.simulation.MatchEventPlayerNameMappingTest,com.footballmanager.application.architecture.SimulationArchitectureBoundaryTest" test`

The audit did not find expected-value changes hiding regressions in the audited commits.

Full functional equivalence cannot be accepted because `mvn -q test` failed due Redis unavailability in this audit run.

## 17. Tests

Commands executed:

- `mvn -q -DskipTests test-compile`: passed.
- `LiveSessionSameInstanceConcurrencyTest` repeated 10 times: passed 10/10.
- Focused LiveSession/isolation/substitution/SSE/architecture command: passed.
- `mvn -q test`: failed.

Full-suite failure:

- Root cause: `org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis`.
- Low-level cause: `Connection refused: getsockopt: localhost/127.0.0.1:6379`.
- Affected tests: Redis-backed E2E/integration tests, beginning with `AuthControllerE2ETest.cleanRedis`.

Surefire aggregation after the failed full run:

- report files: 277;
- tests: 2517;
- failures: 0;
- errors: 200;
- skipped: 4.

The Maven console summary reported:

- tests run: 2520;
- failures: 0;
- errors: 200;
- skipped: 4.

The difference between console total and XML aggregation likely comes from report state after the interrupted/failing full run. In either case, the suite is not green.

Delta from declared baseline:

- declared prior baseline: 2503 tests, 0 failures, 0 errors, 4 skipped;
- declared new target: 2517 tests, 0 failures, 0 errors, 4 skipped;
- expected delta: +14 tests from new substitution identity, same-instance concurrency, and architecture coverage.

This delta is plausible, but could not be independently accepted because the full suite failed.

## 18. Documentation

Audited documents:

- `docs/architecture/POST_MVP1_LIVE_SESSION_CONCURRENCY_DEFINITIVE_AUDIT.md`
- `docs/architecture/POST_MVP1_LIVE_SESSION_THREAD_SAFETY_CLOSURE.md`
- `docs/architecture/POST_MVP1_LIVE_SESSION_THREAD_SAFETY_FINAL_AUDIT.md`

Positive findings:

- the historical `APPROVED WITH ISSUES` verdict remains present in the prior definitive audit;
- the remediation section was appended after the historical conclusion;
- the closure documents accurately describe the new synchronization and dedup strategy at a high level;
- test counts in the documents match the implementer's declared green suite state.

Issues:

- the final audit document says `context()` returns an immutable context reference. That is overstated because nested `SessionTeam` and `SessionPlayer` objects remain mutable.
- the final audit document claims final `APPROVED`, but this independent audit cannot reproduce full-suite green and found mutable context exposure.

Assessment: documentation is useful but too optimistic.

## 19. Hygiene

Checks:

- `git diff --check ac16c3cf..ad77040e`: passed.
- New same-instance and identity tests contain no reflection or `Thread.sleep`.
- `LiveSession.java` contains no `static final Object`, `ReentrantLock`, `ConcurrentHashMap`, or `CopyOnWriteArrayList`.
- Frontend working tree: clean.
- No frontend files changed in audited range.
- No push performed by this audit.

Current root status after this audit contains only the allowed untracked report:

- `docs/architecture/POST_MVP1_LIVE_SESSION_THREAD_SAFETY_DEFINITIVE_AUDIT.md`

## 20. Critical findings

None.

## 21. Important findings

1. `LiveSession.context()` exposes a live `MatchContext` whose nested `SessionTeam` and `SessionPlayer` objects are mutable through public setters. This allows callers to mutate session-relevant state outside the `LiveSession` monitor and bypass the synchronized mutation API.
2. The full backend suite is not green in this independent audit run. `mvn -q test` failed with Redis connection errors against `localhost:6379`, producing 200 errors.
3. The existing architecture guard does not detect the mutable object graph exposed by `context()` and does not verify every public `LiveSession` method participates in the lock policy.

## 22. Minor findings

1. `finalResult()` holds the session monitor during a full simulation. This is safe from a race perspective but can block same-session readers while the full result is computed.
2. `mutateContext(...)` executes caller-supplied `UnaryOperator` code while holding the session monitor. Current usages appear controlled, but the API shape can allow slow or external work under lock.
3. One concurrency test chooses the ticking worker using executor thread-name suffix matching. It passed repeatedly, but an explicit worker index would be less brittle.
4. The substitution dedup semantics intentionally collapse exact same team/minute/out/in substitution events. That is correct for replay deduplication, but it should remain documented as the visible-event identity policy.

## 23. Readiness

Not ready to declare the same-instance thread-safety closure final.

The synchronized public method model is much closer to production quality, and focused concurrency tests are healthy. But the closure should not be marketed as definitive until:

- `context()` no longer exposes a mutable object graph that can be changed outside the session monitor;
- the full backend suite is rerun successfully with Redis available;
- the architecture guard is strengthened to catch mutable context exposure and full public synchronization drift;
- documentation is corrected to avoid overstating immutability and approval.

## 24. Conclusion

The implementation fixed the main intra-session synchronization gap for scalar state, final-result construction, accumulated-event reads, and visible substitution identity. The new tests are real and valuable, and repeated focused stress did not reveal flakiness.

The closure still fails the definitive acceptance criteria because `context()` exposes mutable nested entities and the full suite did not pass in the audit environment due Redis unavailability.

Final verdict: `REJECTED`.

---

## Remediation addendum - LiveSession context encapsulation

Date: 2026-07-31

This addendum preserves the historical `REJECTED` audit above. The rejection was valid for audited head `ad77040e`, because the public `LiveSession.context()` method exposed a live `MatchContext` graph containing mutable `SessionTeam` and `SessionPlayer` objects, and the full suite could not be reproduced while Redis was unavailable.

The repository was remediated after that audit through the following commits:

- `9597678a` Encapsulate mutable LiveSession context
- `61369a8b` Migrate LiveSession context consumers
- `73cbc646` Guard LiveSession context immutability

### Context exposure closure

`LiveSession` no longer exposes the live mutable `MatchContext` graph through its public API.

The public read model is now `contextView()`, which returns `LiveSessionContextView` value records. The view contains immutable containers and scalar/value data only. It does not expose internal `SessionTeam`, `SessionPlayer`, services, adapters, repositories, or the live `MatchContext`.

The remaining `context()` method is package-level and returns a defensive copy of the current `MatchContext`, including copied session teams and players. Mutating the returned copy does not mutate the live session.

### Mutation closure

Production consumers no longer mutate session context by obtaining mutable nested objects from `context()`.

The migrated consumers use explicit synchronized `LiveSession` methods:

- `changeTeamStyle(...)`
- `changeFormation(...)`
- `scheduleManualSubstitution(...)`
- `replayCurrentMinute()`

`mutateContext(...)` is no longer public. It is package-level and applies mutators to a defensive copy before publishing a copied next context.

### Architecture guard closure

Architecture tests now verify:

- public `LiveSession` return types do not expose `MatchContext`, `SessionTeam`, or `SessionPlayer`;
- parameterized public return types do not mention mutable session entities;
- `LiveSessionContextView` record components do not contain mutable session entities;
- the same-instance synchronization and no-global-lock guards remain active.

### Validation after remediation

Commands executed after the remediation:

- `mvn -q -DskipTests test-compile`: passed.
- Focused LiveSession, tactical-change, substitution-command, context-encapsulation, and architecture tests: passed.
- Redis was started and authenticated successfully with repository `.env` values loaded in the same PowerShell session.
- Full backend suite `mvn -q test`: passed.
- `git diff --check`: passed.
- Frontend working tree remained clean and untouched.

Final Surefire aggregation:

- reports: 278;
- tests: 2521;
- failures: 0;
- errors: 0;
- skipped: 4.

### Addendum verdict

APPROVED

The historical `REJECTED` verdict for `ad77040e` remains accurate. The later remediation closes the mutable-context exposure, secures context mutation behind the `LiveSession` monitor, adds architecture and behavior guards, reproduces the full backend suite with Redis available, and satisfies the definitive LiveSession context-encapsulation closure criteria.
