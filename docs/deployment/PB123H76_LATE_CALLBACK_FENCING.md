# PB1.2.3H7.6 — Late callback fencing

## Contract

Every persistence callback derived from a round or match carries
`CareerWriteContext(ownerId, careerId, expectedGeneration)`. The callback enters
the career coordinator first. The coordinator verifies the owner mapping, root,
index, generation and reset tombstone, renews ownership, and only then invokes
the child write. The coordinator is released after completion, error or
cancellation.

After reset, the previous generation is invalid. A callback with that token is
rejected and cannot recreate the mapping, index, root, state, runtime, command,
baseline or detail key. A new career with the same owner and career identifier
receives a new generation, so an old engine cannot write into it.

## Surfaces covered

Round controller, match engine/session context, state and runtime repositories,
baseline and detailed-match persistence, substitution persistence, career/world
updates and test-harness replay all require the fenced context. No productive
`touchBeforeWrite` call captures ownership outside the coordinator.

## Registry isolation

Reset removes the old lifecycle registry entry and late callbacks are still
fenced by generation; registry removal alone is not treated as safety. Owner B
uses a separate context and continues independently.

## Evidence

Real-Redis tests cover stale contexts, owner preservation, generation reuse and
tokenless fail-closed writes. Mock-only timeout/driver failures are identified
as such in the final review; no mock is presented as Redis evidence.
