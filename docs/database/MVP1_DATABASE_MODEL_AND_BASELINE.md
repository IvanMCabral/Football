# MVP 1 Database Model and Clean Flyway Baseline

## 1. MVP 1 objectives

MANAGER is still before its first public release. The database model can therefore be consolidated into a clean Flyway baseline instead of preserving internal experimental migrations.

MVP 1 needs a professional relational foundation for:

- countries, leagues, divisions and seasons;
- clubs and their league/division membership;
- players, squads, football attributes and exactly two special attributes per dataset player;
- users, careers, fixtures, matches, events and standings used by the current application;
- repeatable future imports for at least three complete leagues.

Redis remains the runtime store for career snapshots and detailed live-match payloads. PostgreSQL owns durable catalog, auth, club, player, fixture and statistics structures.

## 2. Domain model

### Country

`countries` is the canonical country catalog. It owns the normalized country code, display name and demonym/nationality label. Leagues, clubs and players reference it through `country_id` while current legacy-facing tables keep textual `country` columns where the existing code still reads them.

### League and division

`leagues` represents a competition container in one country. `divisions` represents a tier inside a league pyramid. A league can have multiple divisions over time, and a club belongs to a division through `club_division_memberships`.

The current `teams.league_id` and `teams.division` columns remain for compatibility with the existing R2DBC repositories and seed services, but the documented MVP 1 source of truth is the normalized club/division membership model.

### Club, team and squad

For MVP 1, a club and the playable team are the same operational concept in current code. The `teams` table remains the application-facing table. `clubs` is introduced as the normalized club catalog prepared for future separation of academy/reserve teams, identities and stadium ownership.

`team_squad` stores the active relation between a team and its players. It now has a surrogate id plus a unique `(team_id, player_id)` constraint.

### Player

`players` remains the active table consumed by repositories. It is extended by normalized tables for secondary positions and special attributes. The existing numeric attributes stay in columns because the engine, lineup, ratings and frontend consume them directly and frequently.

`skill_levels_json` remains as an optional compact metadata field for the detailed engine skill map. The MVP 1 relational model adds a clear player attribute catalog and constraints for the six core numeric attributes used today.

### Career, fixtures and detailed match persistence

The current career runtime is mostly Redis-backed. PostgreSQL owns:

- users;
- games/career entry points;
- matches;
- match events;
- standings;
- persistent club/player catalog needed to bootstrap careers.

Detailed match snapshots remain in Redis for this MVP phase.

## 3. Textual relationship diagram

```text
countries
  ├─ leagues
  │   └─ divisions
  │       └─ club_division_memberships ── clubs
  ├─ clubs
  └─ players

teams
  ├─ team_squad ── players
  ├─ league_teams ── leagues
  └─ games ── users

games
  └─ matches
      └─ match_events

seasons
  ├─ standings ── teams
  └─ season_competitions

players
  ├─ player_secondary_positions
  ├─ player_special_attributes ── special_attributes
  └─ contracts
```

## 4. Tables

### Catalog and competition

- `countries`: normalized countries.
- `leagues`: active league table plus country relation and stable code.
- `divisions`: league tier catalog.
- `seasons`: season status and league relation.
- `season_competitions`: future-safe relation between seasons and competitions.
- `league_teams`: current application relation between leagues and teams.
- `club_division_memberships`: normalized club membership per division/season.

### Club/team/player

- `clubs`: normalized club catalog.
- `stadiums`: optional stadium catalog for club identity.
- `teams`: current playable team table used by existing code.
- `players`: current player table used by existing code.
- `team_squad`: active roster membership.
- `player_secondary_positions`: normalized secondary positions.
- `player_attribute_catalog`: metadata for football attributes.
- `special_attributes`: reusable qualitative trait catalog.
- `player_special_attributes`: exactly two traits per dataset player.
- `contracts`: basic contract data.

### Match/career/statistics

- `users`: auth users.
- `games`: career/game entries.
- `matches`: fixtures and match results.
- `match_events`: persisted match events.
- `standings`: league standings.
- `transfers`: basic transfer workflow.
- `player_match_statistics`: future-friendly player match stats.
- `player_season_statistics`: future-friendly player season stats.

## 5. Player football attributes

The MVP 1 numeric attribute scale is `1..99`.

| Attribute | Column | Default | Consumers |
| --- | --- | ---: | --- |
| Attack | `attack` | 50 | detailed engine, league engine, ratings, frontend |
| Defense | `defense` | 50 | detailed engine, league engine, lineup, frontend |
| Technique | `technique` | 50 | detailed engine, ratings, auto-select |
| Speed | `speed` | 50 | detailed engine, ratings, auto-select |
| Stamina | `stamina` | 50 | match fatigue, lineup, ratings |
| Mentality | `mentality` | 50 | detailed engine, pressure/events, ratings |

The baseline enforces the numeric range in PostgreSQL with check constraints. Domain validation remains the first line of defense in Java.

## 6. Special attributes design

Special attributes are qualitative traits, not duplicates of numeric stats. They live in:

- `special_attributes`: reusable catalog;
- `player_special_attributes`: player assignment table.

MVP 1 dataset players must have exactly two special attributes. PostgreSQL enforces:

- no duplicate trait per player;
- slot must be `1` or `2`;
- one trait per slot;
- valid foreign keys to player and catalog.

PostgreSQL cannot express "exactly two rows per player" with a simple check constraint across rows, so fixture/import validation must verify it after load. The database enforces the building blocks that make the rule reliable.

Initial allowed trait examples for fixtures:

- `clutch_finisher`
- `press_resistant`
- `aerial_specialist`
- `line_breaker`
- `leader`
- `workhorse`

## 7. Constraints

Key constraints:

- UUID primary keys for durable catalog and app entities.
- Unique normalized codes for countries, leagues, divisions and special attributes.
- Unique team/player squad relation.
- Player numeric attributes constrained to `1..99`.
- Player energy constrained to `0..100`.
- Height constrained to a football-realistic nullable range.
- Match round positive when present.
- Possession and shot counters constrained to valid non-negative ranges.
- Special attribute slots constrained to `1..2`.

## 8. Indexes

Essential indexes:

- lookup by country code, league code, team league/division;
- team name and player name searches;
- `team_squad(team_id)` and `team_squad(player_id)`;
- `matches(game_id, round)`;
- `matches(home_team_id)` and `matches(away_team_id)`;
- `standings(season_id, team_id)`;
- `player_special_attributes(player_id)`.

## 9. Flyway strategy

The clean MVP 1 baseline is consolidated into:

- `V1__create_manager_schema.sql`

`V1` is Flyway's technical schema order, not a game version. The prior internal migration chain is removed because there are no public databases to preserve.

## 10. Local recreation strategy

Before recreation:

1. export schema-only backup outside the repo;
2. export Flyway history outside the repo;
3. confirm backend/frontend are green from the pre-change state.

Recreation:

1. stop backend/frontend processes;
2. drop local `football_manager`;
3. create local `football_manager`;
4. run the application or Flyway migration from scratch;
5. seed minimal development data through existing seed endpoints;
6. run backend, frontend and runtime smoke.

## 11. Fixtures

Fixtures are deliberately minimal in this phase. They must support:

- backend start;
- auth;
- world seed;
- career start;
- lineup auto-select;
- round simulation;
- detailed match persistence.

The full three-league dataset is explicitly deferred.

## 12. Preparation for three leagues

The baseline supports:

- stable external source ids;
- repeatable upserts;
- normalized country/league/club/player relations;
- normalized special attributes;
- players with no club;
- future transfers;
- league/division membership by season;
- validation of exactly two special attributes per imported player.

## 13. Risks

- Current application code still primarily reads `teams`, `players`, `league_teams`, `matches`, `standings`, `users` and `games`.
- Normalized MVP 1 catalog tables are ready for the next dataset/import phase but not all are consumed yet.
- `skill_levels_json` remains until the detailed engine skill map is fully relational or the compact JSON shape is intentionally kept.

## 14. Validation commands

Backend:

```powershell
mvn -q -DskipTests test-compile
mvn -q test
```

Frontend:

```powershell
npm run build -- --configuration development
npm run build
npm test -- --watch=false --browsers=ChromeHeadless
```

Runtime:

```powershell
# Start PostgreSQL, Redis, backend and frontend.
# Then run register -> seed -> career -> lineup -> round -> detailed match smoke.
```

## 15. Results

Initial validation before changing the baseline:

- backend test compile: passed;
- backend suite: passed;
- frontend development build: passed;
- frontend production build: passed;
- frontend ChromeHeadless tests: 1016 success, 0 failures, 2 skipped;
- schema backup: created outside the repository.

Final validation after the clean baseline:

- previous schema and Flyway history backup: `C:\Users\ichu_\AppData\Local\Temp\manager-db-baseline-backup-20260729-123719`;
- active migration source: only `src/main/resources/db/migration/V1__create_manager_schema.sql`;
- temporary clean database migration: passed with `V1 | create manager schema`;
- recreated local `football_manager` database: passed with one Flyway row, `V1 | create manager schema | success=true`;
- recreated local database table count: 26 public base tables;
- runtime smoke: passed through register, seed, career creation, lineup confirmation, round start, detailed match fetch and player statistics fetch;
- backend test compile: passed;
- backend suite: 2435 tests, 0 failures, 0 errors, 4 skipped;
- frontend development build: passed;
- frontend production build: passed;
- frontend ChromeHeadless tests: 1016 success, 0 failures, 2 skipped.
