# PB1.2.3H7.9F — One-owner canary capacity plan

**Status:** not admitted.  The plan is intentionally incomplete because the
runtime precheck failed before a safe owner could be inspected.

## Admission prerequisites

1. Render service identity must be `srv-d9nvldtaeets73coqiog` at the exact
   expected live SHA for the gate.
2. The service must remain single-instance with autoscaling OFF and health
   green.
3. Exactly one proven test owner must be selected; a human account is not a
   substitute.
4. That owner's exact `world:{owner}` key must be LEGACY, owner-matching and
   reference-complete.
5. The semantic dry-run must compare LEGACY → planned PREPARED → planned
   COMMITTED and return semantic equality.
6. Provider accounting must supply a conservative upper bound, not only the
   integer `253 MB` display.

## Required measurements (not executed)

The following fields remain `NOT_MEASURED` because the runtime stop condition
forbade candidate inspection:

`candidateLegacyPhysical`, `preparedPhysical`, `committedPhysical`,
`catalogPhysical`, `metadataPhysical`, `providerUsedLowerBound`,
`providerUsedUpperBound`, `providerSpecificAdditionalUncertainty`,
`plannedWorstCasePeak`, and `remainingAfterAllReserves`.

The governing model must include, conservatively, old representation plus the
larger of PREPARED/COMMITTED, a missing catalog, metadata, 32,768 bytes of
provider safety margin, 65,536 bytes of local accounting uncertainty, and any
provider-specific uncertainty.  No same-key replacement credit may be assumed
without provider evidence.

## Capacity verdict

`NOT_ADMITTED — RUNTIME_IDENTITY_CHANGED`

There is no owner-specific peak, no plan hash, and no authorization to write.

## Reconciled status (2026-08-13)

Runtime identity is now `RUNTIME_EQUIVALENT` and health is green. The capacity
gate remains unadmitted because provider byte bounds are still unknown. More
importantly, canonical owner evidence provides zero eligible disposable test
owners: the 144 exact Redis owner IDs all collide with current PostgreSQL
users, while the 21 proven audit owners have no current owner-scoped Redis
keys. No candidate-specific representation measurements can be produced
safely, so `canaryPlanSha256` remains `NOT_PRODUCED` and the verdict is
`BLOCKED_OWNER`.

## H7.9F provenance recovery update (2026-08-13)

The owner gate is no longer blocked on provenance discovery: four current
owners were positively tied to the PB123G audit by controlled identity
conventions and the 2026-08-04 audit timestamp. The first owner-specific read
found a legacy world of 2,483,493 Redis physical bytes, with no career root or
catalog key. The semantic planner therefore remains unadmitted and the plan
hash remains `NOT_PRODUCED`. Provider accounting is independently
`PROVIDER_ACCOUNTING_TOO_COARSE` (`253 MB / 256 MB`, `DBSIZE=9555`).

The canary is not authorized and no migration or cleanup was executed. See
`evidence/pb123h79f/safe-test-owner-provenance/` and
`PB123H79F_SAFE_TEST_OWNER_PROVENANCE.md`.

## Selected-owner capacity update (2026-08-13)

The selected-owner semantic plan is now available. Exact in-memory
representations are 525,284 bytes PREPARED, 980,748 bytes COMMITTED and
1,356,780 bytes for the canonical catalog, with the observed legacy key at
2,483,493 physical bytes. The owner-specific logical replacement is smaller
than the legacy representation. This does not remove the provider gate:
Upstash still exposes only the rounded `253 MB / 256 MB` usage and does not
provide an exact conservative upper bound for write admission.

Capacity status: `READY_NOT_AUTHORIZABLE_ACCOUNTING`.
Semantic status: `SEMANTIC_PLAN_PASS`.
Execution remains explicitly unauthorized; see
`evidence/pb123h79f/selected-owner-semantic-dry-run/`.

## Upstash accounting closure (2026-08-13)

Fresh authenticated read-only provider evidence is recorded in
`PB123H79F_UPSTASH_ACCOUNTING_CLOSURE.md` and
`evidence/pb123h79f/upstash-accounting-closure/`.

The dashboard still exposes `253 MB / 256 MB`, `DBSIZE=9555`, `PING=PONG` and
the selected owner key remains `2,483,493` bytes with `PTTL=-1`. The rendered
progress width (`98.7082%`) is not treated as an exact byte measurement. The
official stats schema documents a byte-valued `current_storage` field, but no
authenticated response containing that value was available in the connected
dashboard session. Display rounding, byte unit, exact quota conversion and
overwrite transient accounting are therefore `UNKNOWN`.

Known required headroom is `2,436,344` bytes (`2,338,040` candidate overlap +
`32,768` provider reserve + `65,536` local uncertainty). Provider-specific
uncertainty and total required headroom remain `UNKNOWN_TOTAL`; minimum
available headroom and cushion are `UNKNOWN`.

**Capacity verdict:** `PROVIDER_ACCOUNTING_STILL_UNBOUNDED`.
**Canary execution:** `NO`.
**Bulk migration:** `NO`.
**Cleanup:** `NO`.

Fresh health GETs also timed out with HTTP `000` for both liveness and
readiness; no current health pass is claimed.
