# V24D8-BUG-004 Fix Report: Squad Placeholders After Career Creation

## Bug Description
After calling `POST /api/v1/world/seed-la-liga` followed by `POST /api/v1/career/start`,
the squad of the user's team showed placeholder names like "Player 1 MAD", "Player 2 MAD"
instead of real La Liga player names (Vinicius Jr., Bellingham, Mbappe, etc.).

**Root Cause:** Multiple cascading issues in the seed + WorldView rebuild chain.

---

## Root Cause Analysis

### Issue 1: Players NOT persisted to PostgreSQL
`LaLigaSeedService.persistPlayerNamesInPostgres()` attempted to persist player names to the
`players` table, but the `@Query` INSERT used SpEL syntax (`:#{#entity.id}`) which
Spring Data R2DBC does not support. All 406 INSERTs silently failed with:
```
executeMany; SQL [INSERT INTO players ...]
```
The players table remained empty of seeded data.

### Issue 2: WorldView rebuild loaded placeholder players from PostgreSQL
`BuildWorldViewUseCaseImpl.getOrCreateSnapshot()` deletes the Redis snapshot after the seed,
forcing a rebuild from PostgreSQL. Since the `players` table had no seeded data,
`TeamPlayerLoaderService.loadTeamsAndPlayers()` loaded pre-existing placeholder players
(with names like "Player N MAD") that existed in the `players` and `team_squad` tables
from previous system setup.

### Issue 3: team_squad entries pointed to wrong player IDs
The `team_squad` table (no userId — global table) had entries for placeholder players.
When `playerRepository.findByTeamId(teamId)` was called, it joined `players` with `team_squad`
and returned the placeholder players instead of the seeded players (which had different UUIDs).

### Issue 4: SpEL `@Query` in R2DBCRepository doesn't work
Spring Data R2DBC does NOT support SpEL expressions like `:#{#entity.field}` in `@Query`
methods. The INSERT query failed at the query parsing level.

---

## Fix Applied

### Fix 1: DatabaseClient instead of @Query SpEL
Replaced `PlayerR2dbcRepository` dependency with `DatabaseClient` for direct SQL execution.
`DatabaseClient.sql(String)` with named bind parameters works correctly.

### Fix 2: Clean old team_squad entries
Before inserting new team_squad entries, the seed now deletes all existing team_squad
entries for the La Liga teams:
```sql
DELETE FROM team_squad WHERE team_id = ANY(:teamIds)
```

### Fix 3: Insert seeded players + team_squad
Each seeded player is now:
1. Inserted into `players` with `ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name`
   (upsert pattern — handles re-runs)
2. Associated with their team in `team_squad` with `ON CONFLICT DO NOTHING`

### Fix 4: Player.Position mapping
Added `mapPosition()` to handle JSON position names that don't match `Player.Position`
enum (MID→CDM, WINGER→LW, ATT→CF, etc.).

---

## Files Changed

### `src/main/java/.../LaLigaSeedService.java`
- Replaced `PlayerR2dbcRepository` with `DatabaseClient` dependency
- New `persistPlayerNamesInPostgres()`:
  - Deletes old `team_squad` entries for La Liga teams
  - Inserts/upserts 406 players with real names into `players`
  - Inserts team_squad associations
  - Uses `DatabaseClient.sql().bind()...fetch().rowsUpdated().block()`
- Added `mapPosition()` to handle JSON position variants
- Removed unused imports (`Player`, `PlayerId`, `PlayerAttributes`, `PlayerEntity`)

### `src/main/java/.../PlayerR2dbcRepository.java`
- Replaced `@Query` SpEL INSERT with named parameter method signature:
  ```java
  @Query("INSERT INTO players (...) VALUES (:id, :name, ...)")
  Mono<Void> insertPlayer(UUID id, String name, int age, String position, ...);
  ```
- Added `BigDecimal` and `Instant` imports

### `src/test/java/.../LaLigaSeedServiceTest.java`
- Updated constructor to use `DatabaseClient` instead of `PlayerR2dbcRepository`
- Added mock setup for `DatabaseClient` fluent API chain
- Removed `PlayerR2dbcRepository.existsById` and `save` stubs

---

## Test Results

### Smoke Test (v13 — fixed)
```
Real names: 22/22
Placeholders: 0
Squad sample:
 LW | Rodrygo Goes    | overall=78
 CM | Lucas Vazquez   | overall=78
 GK | Kepa Arrizabalaga| overall=75
 CM | Raul Asencio     | overall=68
 GK | Andriy Lunin    | overall=74
```

### Full Test Suite
```
Tests run: 932, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Test Coverage
- `LaLigaSeedServiceTest`: 8 tests pass (seed counts, idempotency, Postgres persist)
- `CareerSquadPopulationE2ETest`: 5 tests pass (BUG-003 regression tests)
- E2E smoke: 5 tests pass

---

## Sequence After Fix

```
1. POST /api/v1/world/seed-la-liga
   └─ LaLigaSeedService.execute()
      ├─ ensurePlayers() → creates 406 WorldPlayers with real names in Redis snapshot
      ├─ persistPlayerNamesInPostgres()
      │  ├─ DELETE FROM team_squad WHERE team_id = ANY(:laLigaTeamIds)  [clears old entries]
      │  ├─ INSERT INTO players ... ON CONFLICT DO UPDATE SET name  [upserts 406 players]
      │  └─ INSERT INTO team_squad ... ON CONFLICT DO NOTHING       [associates players]
      ├─ snapshotService.saveSnapshot() → saves updated WorldSnapshot to Redis
      └─ worldRepository.deleteByUserId() → invalidates Redis snapshot

2. POST /api/v1/career/start
   └─ BuildWorldViewUseCase.build()
      └─ getOrCreateSnapshot()
         ├─ redisWorldRepository.findByUserId() → returns EMPTY (just deleted)
         └─ loadBaseDataService.load()
            └─ TeamPlayerLoaderService.loadTeamsAndPlayers()
               ├─ teamRepository.findAllByUserId() → loads WorldTeams from Redis
               └─ playerRepository.findByTeamId(teamId) → loads seeded players from Postgres
                  (findByTeamId joins players + team_squad → returns real players)
         └─ CreateCareerSnapshotUseCaseImpl.create()
            └─ cloneWorldPlayerToSessionPlayer()
               └─ SessionPlayer.fromWorldPlayer(worldPlayer.name) → REAL NAMES IN SQUAD
```

---

## Related: BUG-003 (already fixed)
**V24D8-BUG-003** (`careerCreation_withFreshUser_hasValidUserSessionTeamId`) was fixed
separately and committed in `8c72871`. BUG-003 blocked career start (null userSessionTeamId).
Both BUG-003 and BUG-004 are pushed together.

---

## Fix Verified
Date: 2026-06-14
Backend: Spring Boot (local,v24-mutations profile)
PostgreSQL: ciberfootbolt_local
Redis: flushed before each test
