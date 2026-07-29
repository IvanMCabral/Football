# MVP 1 Database Baseline Closure Report

## Verdict

`APPROVED`

The MVP 1 database baseline is now a clean Flyway `V1` that can be applied to an empty PostgreSQL database and supports the first public MVP model without relying on the removed internal migration chain.

## Audit findings addressed

| Finding | Resolution |
| --- | --- |
| Exactly two special attributes were not guaranteed end-to-end. | Added `PlayerSpecialAttributeSelectionValidator` for importer/dataset validation. It rejects zero, one, more than two, duplicate, blank and unknown special-attribute codes. PostgreSQL keeps FK, slot and unique constraints. |
| `Mvp1DatabaseBaselineContractTest` was textual and superficial. | Replaced it with a real PostgreSQL/Flyway contract test using a temporary database. It validates metadata, constraints, valid inserts, invalid rejections and round-trips. |
| Normalized tables were partially disconnected. | Documented a table connection matrix. Structural tables remain because they are required for the three-league importer and are no longer described as runtime-complete. |
| `TournamentEntity` pointed to a missing table. | Removed the unused persistent entity. Runtime tournament state remains a Redis/domain concept, not a PostgreSQL table. |
| `SeasonEntity.year` mismatched `seasons.year`. | Replaced ambiguous `year` with canonical integer `season_year` and updated `SeasonEntity` mapping. |
| `height_cm = 0` fallback could violate PostgreSQL. | Removed the zero fallback. Unknown height is SQL `NULL`; valid height is `160..210` in SQL and Java. |

## Tournament and competition model

No PostgreSQL `tournament` table is required for MVP 1. The removed `TournamentEntity` had no repository or production consumer and duplicated runtime/domain tournament state. MVP 1 durable competition data is represented by `leagues`, `divisions`, `seasons`, `season_competitions`, `matches` and `standings`.

## Season model

The canonical season field is `seasons.season_year INTEGER CHECK (season_year BETWEEN 1900 AND 2200)`. `SeasonEntity` maps this as `seasonYear`. The contract test inserts and reads a season row and compares `season_year`, `league_id`, `status`, `starts_at` and `ends_at`.

## Height policy

The MVP 1 policy is:

- known player height must be `160..210`;
- unknown height is nullable;
- `0` is never a valid placeholder.

The baseline contract test proves `NULL` and `180` are accepted, while `0`, `159` and `211` are rejected.

## Special attributes

The two-special-attribute rule is split deliberately:

- Database: FK to `players`, FK to `special_attributes`, unique `(player_id, special_attribute_id)`, unique `(player_id, slot)` and slot check `IN (1, 2)`.
- Application/import: `PlayerSpecialAttributeSelectionValidator` requires exactly two existing, different catalog codes before final dataset rows are written.

The contract test proves valid and invalid database cases, and validates the application rule for zero, one, three, duplicates and unknown codes.

## Normalized tables

The baseline intentionally keeps structural tables required for the three-league MVP import: countries, leagues, divisions, stadiums, clubs, seasons, season competitions, club/division memberships, secondary player positions, special attributes, player special attributes, match statistics and season statistics.

Current runtime remains centered on `teams`, `players`, `team_squad`, `league_teams`, `games`, `matches`, `match_events` and `standings`. The documentation now labels structural tables honestly and does not claim that all of them are consumed by runtime flows today.

## PostgreSQL contract tests

`Mvp1DatabaseBaselineContractTest` now:

- creates a temporary PostgreSQL database;
- runs Flyway from an empty schema;
- confirms the single `V1 | create manager schema` migration;
- confirms the table count;
- verifies entity table targets;
- confirms `tournament`/`tournaments` are absent;
- validates `seasons.season_year`;
- performs a season round-trip;
- validates player height constraints;
- validates special-attribute FK, slot and uniqueness constraints;
- validates importer-level special-attribute selection rules;
- validates key unique constraints and indexes for three-league import.

No database mocks are used for this baseline contract.

## Java to SQL alignment

| Entity | Table | Status |
| --- | --- | --- |
| `UserEntity` | `users` | aligned |
| `LeagueEntity` | `leagues` | aligned |
| `LeagueTeamEntity` | `league_teams` | aligned |
| `SeasonEntity` | `seasons` | aligned |
| `TeamEntity` | `teams` | aligned |
| `TeamSquadEntity` | `team_squad` | aligned |
| `PlayerEntity` | `players` | aligned |
| `GameEntity` | `games` | aligned |
| `MatchEntity` | `matches` | aligned |
| `StandingEntity` | `standings` | aligned |

No persistent entity points to a missing tournament table.

## Preparation for three leagues

The schema is ready for a future transactional importer that stages or validates stable country/league/club/player source ids, idempotent upserts, valid league/division/season memberships, valid player numeric attributes, valid nullable/known height policy, exactly two player special attributes and no orphan player/team/squad rows.

The full three-league dataset is not loaded in this closure.

## Remaining non-blocking risks

- Free-agent transfer semantics still need a product decision because `transfers.from_team_id` and `transfers.to_team_id` are required.
- `standings` still contains both `won/drawn/lost` and `wins/draws/losses` compatibility columns.
- Detailed injuries, suspensions, morale and form remain future relational modeling work.
- Detailed match payloads remain Redis-backed in this MVP phase.

None of these risks blocks the MVP 1 clean baseline approval.

## Validation

Validation completed during the closure:

- initial Git status/log/diff checks;
- backend compile: `mvn -q -DskipTests test-compile` passed;
- focused persistence/baseline tests: passed;
- focused baseline/Flyway contract test: passed;
- focused world seed/player height tests: passed;
- full backend suite: `mvn -q test` passed with 2439 tests, 0 failures, 0 errors and 4 skipped;
- local Flyway/database recreation: baseline version `1`, description `create manager schema`, 26 public tables including `flyway_schema_history`, `seasons.season_year` exposed as integer;
- runtime smoke: backend started with local career mutation profile; public API flow passed for register, seed LaLiga, read leagues/teams, start career, auto-select and confirm 11-player lineup, read round fixture and read player stats.

Frontend validation was not rerun because this closure did not modify frontend code or visible API contracts.

Final verdict: `APPROVED`.
