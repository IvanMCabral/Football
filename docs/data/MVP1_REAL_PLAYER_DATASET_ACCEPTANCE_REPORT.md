# MVP 1 Real Player Dataset Acceptance Report

Verdict: APPROVED AFTER REMEDIATION

Date: 2026-07-29.

Scope validated:

- Countries: 3.
- Leagues: 3.
- Clubs: 70.
- Player files: 70.
- Players: 1680.
- Players per club: 24.
- Special traits per player: exactly 2.
- Duplicate external IDs: 0.
- Fictitious generated identity patterns: 0.

Policy conclusion:

The product owner accepts use of publicly visible real player names and club affiliation for this MVP. MANAGER does not claim redistribution is legally guaranteed. All ratings, attributes, values, heights when unavailable, dates of birth when unavailable, tactical normalization, and special traits are generated or estimated by MANAGER and marked in metadata.

Validation evidence:

- Source dataset validation: 70 files, 1680 players, 70 clubs, 0 duplicate IDs, 0 invalid trait counts.
- Focal backend import/runtime tests: `ThreeLeagueDatasetImporterTest` and `ThreeLeagueDatasetRuntimeAcceptanceE2ETest` passed after replacing the rollback fixture with a current real dataset identity.

Principal database runtime validation:

- `.env` was loaded in the same PowerShell session used by PostgreSQL tools and runtime commands; secret values were not printed.
- Backup created before import: `backups/football_manager_before_mvp1_real_dataset_20260729.dump`.
- Import command: `mvn -q spring-boot:run "-Dspring-boot.run.arguments=--app.world.import.three-league=true --spring.main.web-application-type=none"`.
- Import result in local `football_manager`: 3 countries, 3 leagues, 70 clubs, 70 teams, 1680 players, 3360 player traits.
- Database validation after import: 0 invalid trait counts, 0 orphan traits, 0 duplicate `external_id`, 0 old fictional/generated identities, 0 corrupt names, 0 club-dependent player IDs.
- Live runtime smoke against backend/frontend on the principal DB: Spain, Argentina and Brazil league/team/squad loading passed; Spanish career creation, auto-select, lineup recovery, fixtures, standings, live round start, persisted match query and detailed match retrieval passed.
