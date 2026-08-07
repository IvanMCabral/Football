# PB1.2.3H7.7 — Productive stale-callback matrix

| Path | Generation source | Validation | Stale result |
|---|---|---|---|
| Runtime advance | RuntimeMatch loaded from Redis | exact context in Redis adapter | rejected; no child write |
| Match-state advance | MatchState loaded from Redis | exact context in Redis adapter | rejected; no child write |
| Commands | session/career context | context-aware command repository | rejected on stale lifecycle |
| Detailed match | Career lifecycle context | context-aware detail adapter | rejected on stale lifecycle |
| Baseline | Career/session lifecycle context | context-aware baseline adapter | rejected on stale lifecycle |
| Substitution baseline append | MatchSession stored generation | context-aware baseline adapter | rejected on stale lifecycle |
| World mutation | explicit `CareerWriteContext` | `saveWithContext` | failure propagated |
| Career root | explicit initial/existing operation | initial creation or expected generation | stale update rejected |

The only productive `capture` call remaining is the start of a new round. It
does not run in an already-created callback.

Expected stale mutation counters for the Redis fixture are all zero:

```
oldGenerationMutationCount = 0
G2RootChanged = false
G2MappingChanged = false
G2IndexChanged = false
G2GenerationChanged = false
G2TtlChanged = false
```
