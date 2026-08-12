# World V2 two-JVM durability proof

The proof uses two separate Java processes and the product orchestrator.

JVM A independently builds its fixture, writes legacy state, runs the product
migration and stops at durable `PREPARED`. It emits only the owner/test
identifier and the result marker. The test waits for process exit and verifies
that PID A is no longer alive before starting JVM B.

JVM B creates a new Spring component graph, connects to the same ephemeral
Redis and calls the product orchestrator resume path. It reconstructs its own
canonical/legacy expectations in B; no snapshot, plan, catalog, aliases,
prepared envelope or semantic payload is transmitted by A.

Allowed coordination fields are host, port, logical database, owner ID and the
test Redis credential. `semanticStatePassed=false` is intentional evidence
that semantic state was not passed between JVMs; B performs the semantic
reload locally.

Fresh run:

`processA=4044`, `processAExited=true`, `pidAAliveBeforeB=false`,
`processB=30728`, `productPath=true`, `recovery=true`.

No public Redis, PostgreSQL, Render or billing mutation occurred.
