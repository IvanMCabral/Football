# MVP 1 Import Safety Matrix

Date: 2026-07-30

Scope: final MVP 1 roster import idempotence and rollback remediation.

## Executable evidence

Command:

```powershell
mvn -q "-Dtest=ThreeLeagueDatasetImporterTest" test
```

Result:

- tests: 7
- failures: 0
- errors: 0
- skipped: 0

## Idempotence matrix

| Scenario | Evidence | Result |
| --- | --- | --- |
| Fresh Flyway schema plus first import | `importsCompleteDatasetAndValidatesTraits` imports 3 countries, 3 leagues, 70 clubs, 70 teams, 1680 players and 3360 trait rows. | passed |
| Identical second import | `secondImportIsIdempotentAcrossLogicalSnapshot` compares counts and logical fingerprints before/after second import. | passed |
| Stable player IDs | `stablePlayerIdentitiesKeepClubIndependentIdsAndCorrectedPositions` and mutation repair test assert public player source IDs remain stable. | passed |
| Display name correction | Mutation test changes `display_name`; re-import restores dataset value without changing player UUID. | passed |
| Shirt number correction | Mutation test changes `shirt_number`; re-import restores dataset value. | passed |
| Primary position correction | Mutation test changes position to an invalid tactical value; re-import restores dataset value. | passed |
| Attribute correction | Mutation test changes attack/defense/technique/speed/stamina/mentality; re-import restores dataset values. | passed |
| Source reference correction | Mutation test changes `source_entity_id`, `identity_source_ref` and `position_source_ref`; re-import restores source/provenance values. | passed |
| Transfer / squad relation repair | Mutation test moves a player to another team; importer now removes old squad relations for the player before inserting the authoritative team relation. | passed |
| Trait repair | Mutation test deletes all player traits; re-import restores exactly two backend traits. | passed |

## Rollback matrix

The rollback matrix uses temporary mutations inside a transaction against the isolated importer database. Each failure validates the previous snapshot, counts, orphan count and transaction rollback.

| Scenario | Failure path | Result |
| --- | --- | --- |
| Missing source ref | clears `identity_source_ref`, then `validateGlobal()` fails. | rolled back |
| Missing source entity ID | clears `source_entity_id`, then `validateGlobal()` fails. | rolled back |
| Invalid primary position | sets position to `BAD`, then `validateGlobal()` fails. | rolled back |
| Zero traits | deletes all traits for one imported player, then `validateGlobal()` fails. | rolled back |
| One trait | deletes one trait row for one imported player, then `validateGlobal()` fails. | rolled back |
| Broken trait coverage inside import transaction | existing `failedValidationRollsBackPartialDatasetWrites` verifies failed validation keeps counts/fingerprint intact. | rolled back |

## Product fixes discovered by the matrix

- `validateGlobal()` previously counted only players that already had trait rows, so a zero-trait player was not detected. It now starts from `players` and left-joins `player_special_attributes`.
- `validateGlobal()` now also validates persisted source refs and primary position values for MVP 1 imported players.
- `upsertTeamSquad(...)` now removes stale squad relations for a player before inserting the authoritative relation, so transfer/reassignment repairs do not leave duplicate squad memberships.

## Remaining policy note

The MVP 1 importer is authoritative for the current explicit roster. Unknown extra players that are not part of the official source are outside this closure and are not silently treated as official roster removals.
