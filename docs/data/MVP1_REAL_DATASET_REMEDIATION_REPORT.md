# MVP1 Real Dataset Remediation Report

Verdict: APPROVED

## Scope corrected

- Corrupt identity text corrected (`Aitor Fernández`).
- Osasuna duplicate Aitor record remediated into a distinct public identity entry (`Unai García`) to preserve playable squad size without duplicate same-player records.
- Player IDs no longer include club codes.
- Each player has `identitySourceName`, `identitySourceRef`, `sourceEntityId`, `positionSourceRef`, `positionCheckedAt`, and `positionEstimated`.
- Fixed index position template removed from the data. Remaining unverified positions are explicit MANAGER estimates, not presented as sourced facts.
- Manifest now lists every file with counts and SHA-256 checksums.

## Evidence

- Clubs: 70
- Players: 1680
- Position review: `docs/data/MVP1_PLAYER_POSITION_REVIEW.md` (80 verified, 1600 estimated)
- Traceability review: `docs/data/MVP1_IDENTITY_TRACEABILITY_REPORT.md`
- Audit files retained with original conclusions and remediation addenda.

## Principal DB closure evidence

- Backup created before import: `backups/football_manager_before_mvp1_real_dataset_20260729.dump`.
- Import command executed against `football_manager`:
  `mvn -q spring-boot:run "-Dspring-boot.run.arguments=--app.world.import.three-league=true --spring.main.web-application-type=none"`.
- Imported counts: 3 countries, 3 leagues, 70 clubs, 70 teams, 1680 players, 3360 player traits.
- Integrity checks: 0 invalid trait counts, 0 orphan traits, 0 duplicate `source_id`, 0 old fictional/generated identities, 0 corrupt name markers, 0 club-dependent player IDs.
- Runtime smoke: backend and frontend started from the runbook stack; Spain, Argentina and Brazil loaded through public APIs; a real career was created, auto-select saved 11 players and 11 slots, fixtures and standings loaded, a live round persisted a detailed match, and the detailed match endpoint returned the played match.
