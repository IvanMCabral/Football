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

## Numeric quota forensic closure (2026-08-14)

The provider quota gate is now satisfied by class-B provider frontend evidence:
the loaded Upstash console asset `1izufz7lt4vhf.js` defines
`REDIS_PLAN_METRICS.free.max_data_size=0x10000000` (268,435,456 bytes), asset
SHA-256
`2169717202ada35bcbf78e68cfacbc947acb279b59f3f2dcf69b1b859ba4fd28`.
The official API schemas document the related `db_disk_threshold` and
`current_storage` fields in bytes, but the quota was not returned by the
successful stats response itself.

Retained exact usage is 264,967,931 bytes. Therefore headroom is 3,467,525
bytes and the cushion after the 2,436,344-byte certified minimum is 1,031,181
bytes. Binary arithmetic reproduces the dashboard's 98.7082% progress; the
decimal 256,000,000-byte interpretation does not. No additional unbounded
provider transient bound is evidenced beyond the conservative overlap model.

Current verdict: `PROVIDER_CAPACITY_PASS` and
`PB1.2.3H7.9F ONE-OWNER CANARY READY FOR EXECUTION AUTHORIZATION`. Execution,
bulk migration and cleanup remain explicitly unauthorized.

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

## Management stats recovery (2026-08-13)

The authenticated Account Settings → Developer API surface showed
`NO_API_KEY`; only the non-mutating **Create API key** action was available and
it was not selected. Consequently the documented stats endpoint could not be
called, `current_storage` remains unknown and the capacity classification stays
`PROVIDER_ACCOUNTING_UNBOUNDED`.

Fresh bounded health rounds are now recorded as liveness `2/2` HTTP 200 and
readiness `0/2` HTTP 503 (`database=DOWN`, `redis=UP`). The one-owner canary,
bulk migration and cleanup remain unauthorized. See
`PB123H79F_UPSTASH_MANAGEMENT_STATS_RECOVERY.md`.

## Definitive stats and health recheck (2026-08-13)

The Developer API console now lists an existing key, but its secret is not
recoverable and is not available to the runtime. The documented stats endpoint
was not called, so exact `current_storage`, exact quota and available headroom
remain unknown. Capacity remains `PROVIDER_ACCOUNTING_UNBOUNDED`.

Fresh read-only checks confirm `PING=PONG`, `DBSIZE=9555` and catalog absence.
Liveness and readiness are both `2/2` HTTP 200 with `database=UP` and
`redis=UP`; recovery is classified as `TRANSIENT_DATABASE_RECOVERY`.

Canary execution, bulk migration and cleanup remain unauthorized. A secure
runtime injection of the already-created key is required before the stats gate
can close; no new key should be created.

## Secure credential stats recheck (2026-08-13)

Both secure runtime variable names were present. The single documented
`GET /v2/redis/stats/{databaseId}` returned HTTP `401 Unauthorized`; no secret
or Authorization header was recorded. Exact usage, exact quota and calculable
headroom remain unknown, so the canary stays blocked on accounting access. The
required minimum known headroom is `2,436,344` bytes.

After bounded warm-up, health is green in two consecutive rounds (liveness
`2/2`, readiness `2/2`, database and Redis `UP`), but health does not
substitute for provider accounting. No canary, cleanup, migration, catalog
write or provider change was authorized.

## Authenticated Chrome recovery attempt (2026-08-13)

The existing authenticated native Upstash browser session confirmed the
Personal account, Manager database, Free Tier and AWS `sa-east-1`. The
Developer API page showed existing metadata rows `Manager2` and `ManagerKey`,
but no secret was available to the browser or runtime. No credential mutation
was performed.

The documented stats GET returned HTTP `401 Unauthorized` with the secure
runtime pair. The browser's cross-origin request path was blocked by the
client, so no alternate session authentication exists for this endpoint.
Exact usage, quota and headroom remain unknown; canary, migration and cleanup
remain unauthorized. Health passed after bounded retry: liveness `2/2` and
readiness `2/2` HTTP 200, database and Redis `UP`.

## Key replacement and definitive stats (2026-08-13)

Exactly one authorized Developer API key named `H79FAccounting` was created;
its secret was never recorded. Database list and stats GETs returned HTTP 200.
The stats response reported exact `current_storage=264,967,931` bytes and no
exact quota/max-storage byte field. The dashboard `256 MB` display is rounded
and is not converted; `total_monthly_storage` is usage, not a quota, leaving
headroom and final cushion unknown.

The Manager database remains healthy (`PING=PONG`, `DBSIZE=9555`, catalog
absent), and two consecutive liveness/readiness health rounds are HTTP 200
with database and Redis `UP`. Accounting remains unbounded; no canary,
migration or cleanup is authorized.

## Numeric quota forensic closure (2026-08-14)

The provider quota gate is now satisfied by class-B provider frontend evidence:
the loaded Upstash console asset `1izufz7lt4vhf.js` defines
`REDIS_PLAN_METRICS.free.max_data_size=0x10000000` (268,435,456 bytes), asset
SHA-256
`2169717202ada35bcbf78e68cfacbc947acb279b59f3f2dcf69b1b859ba4fd28`.
The official API schemas document the related `db_disk_threshold` and
`current_storage` fields in bytes, but the quota was not returned by the
successful stats response itself.

Retained exact usage is 264,967,931 bytes. Therefore headroom is 3,467,525
bytes and the cushion after the 2,436,344-byte certified minimum is 1,031,181
bytes. Binary arithmetic reproduces the dashboard's 98.7082% progress; the
decimal 256,000,000-byte interpretation does not. No additional unbounded
provider transient bound is evidenced beyond the conservative overlap model.

Current verdict: `PROVIDER_CAPACITY_PASS` and
`PB1.2.3H7.9F ONE-OWNER CANARY READY FOR EXECUTION AUTHORIZATION`. Execution,
bulk migration and cleanup remain explicitly unauthorized.
