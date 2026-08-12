# PB1.2.3H7.9E World V2 negative-control authority V2

## Rule

Each canonical control maps one-to-one to a unique material invariant and records its fixture, keys, mutation, product pipeline entry, and expected rejection. The executable authority is `WorldV2NegativeControlAuthorityV2`; `WorldStorageV2NegativeControlsTest` executes every row and rejects duplicate invariant IDs or blank trace fields.

## Result

| Metric | Value |
|---|---:|
| Canonical controls | 33 |
| Unique material invariants | 33 |
| Redis physical controls | 27 |
| Source-proven controls | 6 |
| Duplicate credit | 0 |
| Material proxies | 0 |
| False passes | 0 |

The source-proven set is restricted to canonical-ID stability, namespace separation, normalization, semantic fingerprint distinction, field-authority completeness, and reference-registry completeness. All invariants involving Redis state, serialization, aliases, owner isolation, catalog state, PREPARED/COMMITTED state, capacity, and semantic deltas use ephemeral Redis and the product migration/reload path.

The former proxy cases for custom teams, custom players, league relations, aliases, unresolved lineup, unresolved fixture, unresolved standings, deterministic alias collision, and duplicate custom entities now persist physical state and are rejected by product code.

The historical false-pass condition is reproduced in the graph tests as `beforeCurrentGuard=PASS_BAD_STATE`; V4 produces `afterNewGuard=REJECT_BAD_STATE` for inherited and external-holder cases.
