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
