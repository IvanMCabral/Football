# PB1.2.3H7.9E — World V2 durable boundary authority

## Scope

This document records the local closure of the durable-writer discovery P1. It
does not authorize a provider migration or any public Redis/PostgreSQL action.

## Authority contract

`DurablePersistenceBoundaryDiscovery` enumerates compiled product classes from
the complete `com.footballmanager` classpath, including repository interfaces.
It determines whether a type is durable from its hierarchy and bytecode
references to Redis/R2DBC APIs before reading any World V2 annotation. Member
and compiler-generated helpers are excluded from default discovery unless they
are explicitly supplied to the inspection API. `WorldPersistedWriter` is
classification metadata, never the discovery filter.

Every discovered boundary has exactly one classification:

- `WORLD_REFERENCE_RELEVANT`;
- `OUT_OF_SCOPE_CANONICAL_SOURCE`;
- `OUT_OF_SCOPE_NON_WORLD_IDENTITY`;
- `OUT_OF_SCOPE_WITH_EXPLICIT_REASON`; or
- `UNCLASSIFIED` (fail-closed).

Conflicting World writer and boundary classifications are rejected. The root
authority exposes all boundaries and `requireComplete()` fails when an
unclassified boundary is present. This closes the prior blind spot where an
unannotated writer could be invisible while the registry passed.

## Local evidence

The focused authority and reference-inventory tests report:

`writers=19 roots=4 models=33 fields=238 containers=82 identityLeaves=126 references=57 validators=57 unresolved=0 unclassifiedWriters=0`

The explicit synthetic negative control injects an unannotated durable writer
alongside a known writer. Discovery reports that writer and `requireComplete()`
rejects it; it cannot silently become a World root.

## Boundary separation

Redis World V2 writers remain graph roots. PostgreSQL/R2DBC repositories and
canonical catalog adapters are explicitly classified as canonical-source or
identity boundaries, so they remain visible without being mistaken for
owner-scoped World V2 writers.
