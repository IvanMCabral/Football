# PB1.2.3H7.6 — Writer and generation inventory

| Writer surface | Generation source | Captured before write | Validated in coordinator | Tokenless behavior |
|---|---|---:|---:|---|
| Career root update | `CareerOwnershipTouchService.capture` | yes | yes | rejected |
| Initial career creation | new Redis generation | n/a (bootstrap) | atomic absence checks | explicit bootstrap only |
| Match state | `CareerWriteContext` | yes | yes | rejected |
| Runtime match | `CareerWriteContext` | yes | yes | rejected |
| Match commands | `CareerWriteContext` | yes | yes | rejected by adapter |
| Baseline | `CareerWriteContext` | yes | yes | rejected |
| Detailed match | `CareerWriteContext` | yes | yes | rejected |
| World snapshot update | `CareerWriteContext` | yes | yes | rejected; initial seed is guarded |
| Round/start persistence | round context | yes | yes | rejected |
| `extendTTL` | active context | yes | yes | cannot revive reset state |
| Test-harness replay | active context | yes | yes | rejected |

Generation is created at `career-generation:{careerId}` during explicit initial
creation. It is captured by the ownership service, propagated through round,
engine, session and persistence objects, and compared exactly before any child
write. The token is never returned in HTTP responses or logs.

Legacy registry overloads remain only as compatibility/test seams; productive
match-management paths now reject missing career context before an engine or
writer can be created.
