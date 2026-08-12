# PB1.2.3H7.9E World V2 persisted-root authority

## Authority contract

`WorldMigrationPersistedRootAuthority` scans productive classes under the product base package, accepts only classes loaded from production output, and reads repeatable `@WorldPersistedWriter` declarations. Test fixtures are excluded from product discovery by code-source location.

A declaration contains the adapter, write method, serialized root, storage family, and durability role. A writer is either `WORLD_REFERENCE_GRAPH` or `EXPLICITLY_NON_WORLD_REFERENCE`; absence of a declaration is a build failure through `WorldMigrationPersistedWriterAuthorityTest`.

## Discovered world-reference roots

| Root | Writer | Write path | Storage family |
|---|---|---|---|
| `WorldSnapshot` | `RedisWorldRepository` | `saveInitial/persistV2` | `world:* / world-catalog:v2:*` |
| `WorldStorageEnvelope` | `RedisWorldRepository` | `persistV2/execute` | `world:*` |
| `PreparedWorldMigrationEnvelope` | `RedisWorldRepository` | `execute` | `world:*` |
| `CareerSave` | `RedisCareerRepository` | `saveInternal` | `career:*` |

The remaining 15 declarations classify runtime match, state, standings, baseline, detail, command, entity, squad, relation, ownership, and cleanup-marker writers as explicitly outside the World V2 reference graph.

## Fail-closed behavior

- No writer declarations: failure.
- No World-reference root: failure.
- New productive Redis writer without classification: source guard failure.
- New annotated product writer: automatically joins authority without editing a root list.
- New test-only writer: excluded from product discovery but can be passed directly to the authority fixture for positive and negative controls.

Measured result: 19 discovered, 19 classified, 0 unclassified, 4 graph roots, 0 manual-only roots.
