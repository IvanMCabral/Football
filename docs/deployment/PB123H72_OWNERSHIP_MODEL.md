# PB1.2.3H7.2 — Redis ownership model

## Canonical mapping

Every saved career writes the immutable mapping:

```text
career-owner:{careerId} -> {owner UUID}
```

The mapping uses `SETNX` semantics with a 31-day TTL. A later save may reuse
the same career ID only for the same owner; a conflicting owner fails closed.
The owner index remains `user:{owner}:career-ids`, but it is now only a
candidate-discovery index, never proof of ownership.

## Cleanup rule

Before any career-scoped pattern is constructed, every indexed ID (and the
explicit career ID, when supplied) must have a mapping equal to the requested
owner. Missing or contradictory mappings produce `REJECTED_OWNERSHIP`; no
career-scoped SCAN or deletion runs. Foreign owners are therefore protected
even when the owner Set is stale or corrupted.

The cleanup never reads detail payloads to infer ownership and never uses
`career:*`, `KEYS`, or a global scan.

## Partial lifecycle cases

- root written, mapping failed: the new mapping is removed when possible and
  the root is not considered safely indexed;
- mapping written, index failed: the mapping/root remain retryable and the
  next career read repairs the index;
- mapping missing or contradictory: reset is rejected and the career root is
  preserved;
- repeated reset: exact mappings and keys make the operation idempotent;
- owner index with several valid career IDs: all validated historical IDs are
  cleaned, bounded at 256 entries;
- legacy orphan without mapping: it is protected and not guessed; a separate
  migration/inventory is required.
