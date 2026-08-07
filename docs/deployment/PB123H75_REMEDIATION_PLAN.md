# PB1.2.3H7.5 — Remediation plan

## Scope

This local remediation closes the three P0 findings recorded by the H7.4
independent audit. It changes only lifecycle fencing and persistence safety;
gameplay, simulation rules, fixtures, datasets, frontend and remote services
are out of scope.

## Invariants

1. A derived writer must carry an immutable `CareerWriteContext` containing
   owner, career and lifecycle generation.
2. Mapping, index, generation and tombstone validation occurs inside the
   career coordinator immediately before the child write.
3. A missing or contradictory ownership record fails closed; no callback can
   repair ownership after reset.
4. World creation is an explicit pre-career operation and is rejected when a
   career root or owner index exists.
5. Mapping compensation is compare-and-delete by operation token and cannot
   remove a later save's mapping.
6. Coordination is single-instance only. No distributed-lock guarantee is
   claimed.

## Validation

The real Redis integration suite covers stale career/world writers, tokenized
compensation, owner isolation, root-last cleanup, TTL renewal, index limits and
batch accounting. The final local review records the complete suite and any
remaining external gates without changing the historical audit.
