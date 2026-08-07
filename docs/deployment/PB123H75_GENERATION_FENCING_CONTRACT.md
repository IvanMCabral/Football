# PB1.2.3H7.5 — Lifecycle generation fencing contract

`CareerWriteContext(ownerId, careerId, expectedGeneration)` is captured at the
operation boundary by `CareerOwnershipTouchService.capture`. Capture validates
the owner mapping, owner index membership and generation while holding the
career queue. The context is then propagated through round start, runtime and
state persistence, detailed match/baseline persistence, commands and the
round/session metadata.

Every context-aware write re-enters the career coordinator, checks the owner
tombstone, career tombstone, mapping owner, index membership and exact
generation, renews ownership TTLs, and only then invokes the write publisher.
The queue is released on success, error or cancellation.

`CareerSave` loaded from Redis retains its generation in memory. A save with a
captured generation must match the current generation and an existing mapping;
it cannot recreate lifecycle records after reset. A save without a generation
is reserved for creating a new career.

Reset removes the old generation before releasing the reset coordination. A
callback with the old generation therefore fails closed and cannot recreate a
root, mapping, index or child. Generation tokens are internal metadata and are
not returned by HTTP contracts.

The coordinator is deliberately an in-process single-instance contract. A
multi-instance deployment requires a Redis fencing lock before it can claim
horizontal lifecycle guarantees.
