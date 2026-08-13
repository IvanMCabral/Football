# PB1.2.3H7.9F — Selected owner semantic dry-run

**Run mode:** read-only, owner-scoped, no migration execution

**Selected owner:** `PROVEN_TEST_OWNER`, hash `6d963e62a2a6095b976ca78156a7ef0a`

## Result

`SEMANTIC_PLAN_PASS`

The previous `BLOCKED_SEMANTIC` gate was a `DRY_RUN_GATE_MISAPPLIED`. The
legacy representation is not required to contain a catalog fingerprint. The
productive path is `RedisWorldRepository` legacy inspection →
`DurableCanonicalWorldCatalogSource.rebuild` → `WorldStorageMigrationPlanner`
→ `WorldSnapshotOverlay` PREPARED/COMMITTED material → product semantic
reconstruction. The planner was executed locally against the exact selected
payload, with no public Redis or PostgreSQL mutation.

## Runtime and source

- productive authority: `836c98a69ce9d12f67ed603f1f1ea58b8462a82a`
- live runtime: `ccb2723f9e5718d5e15150463b35756336eef260`
- runtime classification: `RUNTIME_EQUIVALENT`
- instance contract: single instance
- source state: `LEGACY`
- source SHA-256: `2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f`
- source serialized bytes: `2,483,461`
- public Redis physical usage for the owner key: `2,483,493` bytes
- source PTTL: `-1` (legacy immortal key; migration admission requires a positive
  replacement TTL)
- post-read health: liveness `2/2`, readiness `2/2`, database `UP`, Redis `UP`

The source owner matches the selected PostgreSQL owner. It contains 3 leagues,
70 teams and 1,680 players, with no career root, career index, runtime, state,
command or active-match reference.

## Migration contract

`LEGACY` does **not** require a pre-existing catalog fingerprint. The catalog
fingerprint is derived during migration from the canonical PostgreSQL source.
The prior fingerprint prerequisite was therefore a dry-run gate error, not a
product requirement.

The canonical path is:

`DurableCanonicalWorldCatalogSource` → `LoadBaseDataService.loadCanonical` →
`WorldStorageMigrationPlanner` → `WorldSnapshotOverlay.fromSnapshot` →
`PreparedWorldMigrationEnvelope` → `WorldStorageEnvelope(COMMITTED)`.

The planner returned `READY`; the reference graph was empty and had no
unresolved references.

The canonical PostgreSQL SELECT-only cross-check used the same `public`
catalog consumed by `loadCanonical`: 3 leagues, 70 teams, 1,680 players and
3,360 player-attribute rows. Teams and leagues matched the owner payload;
canonical height/skill data differs as expected and is captured in the
owner overlay so the reconstructed legacy semantics remain lossless.

## Canonical and overlay results

- canonical leagues: 3
- canonical teams: 70
- canonical players: 1,680
- catalog fingerprint: `1e654bec389796d232aba91685ac87d9ef1de08bcf3f5a7da563fdedbfb27000`
- catalog key: `world-catalog:v2:{fingerprint}`
- catalog key hash (evidence): `5f264f778e608421`
- canary plan SHA-256 (canonical plan with the self-hash field null):
  `298b32c98e0052269896f3f9caa9a89e1f70e3fa15019ee061d7247c751ebc7a`
- team deltas: 70 (city/owner projection normalization)
- player deltas: 1,680 (height/skill-level normalization)
- league deltas: 0
- custom teams: 0
- custom players: 0
- canonical removals: 0
- legacy aliases: 1,680
- metadata fields preserved: `createdAt`, `lastUpdated`
- explicit-null fields preserved: `heightCm` (1,680 players); snapshot timestamps
  are explicit non-null values
- alias collisions, cycles and foreign targets: 0

Every real legacy player resolves to one deterministic canonical ID generated
by `WorldPlayer.stableCanonicalWorldPlayerId`; no self-alias or duplicate
semantic identity was found.

## Semantic verification

The product `WorldSemanticComparator` compared the exact legacy snapshot with a
fresh reconstruction from the planned canonical catalog and overlay:

- product comparator: **PASS**
- independent field-level cross-check: **PASS**
- mismatch count: `0`
- mismatch paths: none
- fields checked: `29,279` snapshot/entity fields plus `1,680` alias relations
- local isolated reconstruction runs: `10/10 PASS`
- catalog fingerprints: `10/10 identical`
- canonical IDs: `10/10 stable`
- owner match: `PASS`

The existing real-Redis World V2 physical semantic matrix also remains green:
49 tests, 0 failures, 0 errors, 0 skipped (test profile, isolated Redis).

## Physical sizes and capacity

The exact candidate material generated in memory was:

| Representation | UTF-8 bytes | Local Redis string estimate |
|---|---:|---:|
| legacy source | 2,483,461 | 2,483,493 (observed) |
| PREPARED envelope | 525,284 | 525,540 |
| COMMITTED envelope | 980,748 | 981,004 |
| canonical catalog | 1,356,780 | 1,357,036 |
| overlay JSON | 980,378 | informational |

The owner-specific logical replacement is smaller than the legacy payload. The
local capacity model therefore calculates no positive net delta after the old
owner key is replaced; however Upstash exposes only a rounded/coarse `253 MB /
256 MB` display. Exact provider bytes and provider-side write admission remain
unknown, so execution is not authorized.

Capacity figures: logical steady-state delta `-145,453` bytes; local model peak
with same-key replacement credit `0` additional bytes; conservative old-plus-new
overlap `2,338,040` bytes. The latter is the minimum known headroom requested
before any provider-specific uncertainty reserve.

## Public mutation boundary

- Redis durable writes/deletes: `0`
- PostgreSQL writes: `0`
- catalog writes: `0`
- accounts/careers created: `0`
- provider or billing changes: `0`
- cleanup/migration authorization: `NO`

Read-only provider checks confirmed the source SHA, owner key, `DBSIZE=9555`,
and the previously observed missing catalog keys. No public state changed.

## Capacity gate

`SEMANTIC_PLAN_PASS` is established. The canary remains
`READY_NOT_AUTHORIZABLE_ACCOUNTING` because provider accounting is
`PROVIDER_ACCOUNTING_TOO_COARSE`; this is a capacity authorization blocker,
not a semantic blocker. The next gate must obtain an exact provider headroom
measurement or explicit authorization policy before any public write.
