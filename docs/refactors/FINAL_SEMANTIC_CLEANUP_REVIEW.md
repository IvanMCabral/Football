# MANAGER - Final Semantic Cleanup and Compatibility Closure

## Verdict

APPROVED

## 1. Baseline

- Starting commit: `b3b32008 Consolidate simulation domain naming and retire versioned legacy code`.
- Starting working tree: clean.
- Baseline compile: `mvn -q -DskipTests test-compile` passed.
- Baseline focused tests: detailed match, match sessions and league simulation tests passed.

## 2. APIs renamed

| Previous name | Current name | Scope |
| --- | --- | --- |
| `StartMatchUseCaseImpl.executeV24` | `executeDetailedMatch` | Application match start path for live detailed simulation. |
| `MatchSessionRegistry.getOrCreateSessionWithV24` | `getOrCreateDetailedSession` | Session registry creation for detailed live sessions. |
| `MatchSession.refreshV24Snapshot` | `refreshDetailedSnapshot` | Current state refresh from detailed live snapshot. |
| `MatchSession.adaptV24Snapshot` | `adaptDetailedSnapshot` | Snapshot adapter from detailed live model to domain state. |
| `MatchFinishedResult.isV24` | `isDetailedMatch` | Domain-facing result classifier. |

No compatibility wrappers with old method names were kept because these are internal Java APIs and all known callers were updated.

## 3. Variables and fields renamed

- `v24LiveSession` became `detailedMatchSession`.
- `v24RoundProcessed` became `detailedRoundProcessed`.
- Session comments and warnings now use detailed-match/classic terminology.

## 4. Beans and qualifiers renamed

| Previous bean/qualifier | Current bean/qualifier |
| --- | --- |
| `v24DetailedMatchDataRedisTemplate` | `detailedMatchDataRedisTemplate` |
| `v24MatchBaselineStateRedisTemplate` | `matchBaselineStateRedisTemplate` |

Redis key names were not changed. They are persisted storage contracts and already use semantic namespaces such as `match-detail` and `match-baseline`.

## 5. Properties and aliases

Current properties are preferred:

- `app.simulation.league.use-classic-engine`
- `app.simulation.league.use-detailed-match-engine`
- `app.simulation.detailed.persist-detail`
- `app.simulation.detailed.mutate-career-state`
- `app.simulation.detailed.persist-injuries`
- `app.simulation.detailed.persist-fatigue`
- `app.simulation.detailed.persist-discipline`
- `app.simulation.detailed.persist-form`
- `app.simulation.detailed.expose-detail-api`

Deprecated aliases remain accepted only in configuration boundary classes:

- `app.simulation.league.use-v23-engine`
- `app.simulation.league.use-v24-detailed-engine`
- `app.simulation.v24.*`

When a deprecated alias is used, `SimulationConfig` or `DetailedSimulationConfig` logs a warning and prioritizes the current property if both are present. These aliases are retained for existing deployments and can be removed once deployment configs have migrated.

## 6. Persisted compatibility

The stored value `engineVersion = "V24"` remains only as a Redis/JSON discriminator for existing match detail and baseline snapshots. It is isolated in:

- `PersistedEngineVersions.LEGACY_DETAILED_MATCH`

The current simulation domain does not use `V24` as a business concept.

## 7. Remaining production V23/V24 references

| Reference | Classification | Justification |
| --- | --- | --- |
| `SimulationConfig` deprecated property literals | Deprecated property alias | Existing deployments may still set these names. Warning emitted when used. |
| `DetailedSimulationConfig` deprecated property literals | Deprecated property alias | Existing deployments may still set these names. Warning emitted when used. |
| `PersistedEngineVersions.LEGACY_DETAILED_MATCH = "V24"` | Persisted compatibility | Required to preserve Redis/JSON snapshots already stored with this discriminator. |

No unclassified V23/V24 production references remain.

## 8. Validation

Completed during this closure:

- `mvn -q -DskipTests test-compile`
- Focused detailed match/session/league tests passed.
- Focused Redis/config/controller tests passed.
- Full suite `mvn -q test` passed.
- Surefire result count: 3750 tests, 0 failures, 0 errors, 8 skipped.
- `git diff --check` passed.

## 9. Deferred non-scope items

The following were intentionally not reopened, per closure scope:

- `ReactiveLifecycleExecutor`
- `LeagueSimulator.block(DETAIL_PERSIST_TIMEOUT)`
- internal composition of `DetailedMatchEngine`
- `LineupDtoAssembler`

## Final decision

The semantic cleanup is approved because versioned terminology is removed from active internal APIs, fields and qualifiers; legacy property names and persisted discriminators are isolated at compatibility boundaries; behavior is preserved by compilation, focused tests and the complete test suite.
