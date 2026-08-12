# PB1.2.3H7.9E World V2 reference authority V4

## Identity mechanism

Durable models use repeatable `@WorldIdentityReference` metadata with an explicit domain and route. Supported domains are `WORLD_TEAM`, `WORLD_PLAYER`, `REAL_TEAM`, `REAL_PLAYER`, `SESSION_TEAM`, `SESSION_PLAYER`, `OTHER_ID`, `NON_ID_TEXT`, and `OPAQUE_VALUE`. Reference domains receive exactly one validator; non-reference domains still classify ambiguous durable strings and UUIDs.

Routes describe the exact semantic side: `VALUE`, `ELEMENT`, `MAP_KEY`, `MAP_VALUE`, and nested combinations such as `MAP_VALUE/MAP_VALUE`. Authority is never inferred from a field name.

## Graph traversal

The graph includes declared and inherited non-static/non-transient fields, generic superclass bindings, arrays, `Collection`, `List`, `Set`, `Map` keys and values, `Optional`, `AtomicReference`, nested parameterized types, wildcard bounds, and generic arrays. Unbounded or unresolved durable generics are recorded and make `requireComplete()` fail.

## Measured V4 result

| Surface | Count |
|---|---:|
| Roots | 4 |
| Models | 33 |
| Fields | 238 |
| Production inherited fields | 0 |
| Containers | 82 |
| Identity leaves classified | 126 |
| Reference paths discovered | 57 |
| Validation authorities | 57 |
| Unvalidated | 0 |
| Extra validators | 0 |
| Unresolved generics | 0 |
| Ambiguous durable paths | 0 |

The old historical count of 25 is retired. V4 has no expected-count constant: its count is computed from the reachable persisted graph.

## Self-destruction matrix

| Mutation | Required result | Result |
|---|---|---|
| Inherited reference absent from registry | Fail | PASS |
| External persisted holder absent from roots | Fail | PASS |
| Unresolved generic | Fail | PASS |
| Unclassified map value | Fail | PASS |
| Validator removed | Fail | PASS |
| Registry-only path | Fail | PASS |
| Empty discovery | Fail | PASS |
| Stale classification | Fail | PASS |

Here `PASS` means the destructive mutation was detected and the gate rejected the bad authority.
