# PB1.2.3H7.7 — Durable generation contract

`RuntimeMatch` and `MatchState` carry `ownerId`/`userId`, `careerId` and
`lifecycleGeneration` across their Redis round-trip. A writer must use the
stored generation; it may not call `capture(owner, career)` for an existing
job. The Redis adapter validates the exact context inside the lifecycle
coordinator before renewing ownership or writing a child key.

Generation is captured only when a new round/runtime/state job is created.
Reset rotates the generation. A callback from the previous generation fails
closed and cannot renew G2 keys or recreate G1 children.

The public HTTP layer maps `RuntimeMatch` to a response DTO that deliberately
omits `lifecycleGeneration`. Match state is an internal application/Redis
model and is not returned as a public DTO. The token is not logged, emitted by
SSE, or included in error bodies.

Required static invariants:

```
TOKENLESS_PRODUCTIVE_WRITERS_REMAINING = 0
UNFENCED_OWNER_ONLY_FALLBACKS_REMAINING = 0
LATE_GENERATION_CAPTURE_PRODUCTIVE_PATHS_REMAINING = 0
```
