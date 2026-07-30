# MVP 1 Three-League Runtime Acceptance Report

Verdict: APPROVED FOR CURRENT AUTOMATED RUNTIME COVERAGE

Cutoff date: 2026-07-29.

The backend focal runtime acceptance imports the full real-identity MVP dataset and verifies playable career setup for:

- Spain;
- Argentina;
- Brazil.

Validated by automated E2E:

- import dataset;
- list leagues;
- list teams;
- create career;
- load squad;
- validate attributes;
- validate exactly two traits;
- auto-select lineup;
- rollback on import conflict.

Principal database runtime closure:

- Backup created before import: `backups/football_manager_before_mvp1_real_dataset_20260729.dump`.
- Final dataset imported into local `football_manager` with the documented importer runner.
- Database counts after import: 3 countries, 3 leagues, 70 clubs, 70 teams, 1680 players and 3360 player traits.
- Integrity checks after import: 0 invalid trait counts, 0 orphan traits, 0 duplicate `source_id`, 0 old fictional/generated identities, 0 corrupt name markers and 0 club-dependent player IDs.
- Final runtime trait smoke after frontend exposure: `TRAITS_RUNTIME_OK league=Spanish Primera Division team=Valencia CF squad=24 withTwoTraits=24`.
- Runtime smoke against the live local backend/frontend passed for Spain, Argentina and Brazil loading.
- Playable-flow smoke passed for Spanish career creation, squad load, 4-4-2 auto-select, 11-player/11-slot lineup recovery, fixtures, standings, live round start, persisted match query and detailed match retrieval.

The data/import/runtime path is green for MVP 1. Manual browser review remains useful for visual polish, but no dataset/runtime blocker remains in this closure.
