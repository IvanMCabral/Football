# MVP 1 Real Player Dataset Final Independent Audit

Date: 2026-07-29  
Repository: `D:\ProyectosOpenCode\MANAGER`  
Frontend: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`  
Auditor mode: read-only except for this report.

## 1. Verdict

`REJECTED`

The implementation is functionally close and both backend/frontend suites are green, but it cannot be approved as the final MVP 1 real-player dataset closure because several approval blockers remain:

- The local main database `football_manager` does not contain the final `manager-mvp1-explicit` dataset.
- Player IDs are deterministic but club-dependent, so a transfer changes identity.
- One source dataset identity has corrupted text: `Aitor Fernández`.
- The importer is still an application-layer Spring/JDBC class, not a clean hexagonal application service with persistence/resource adapters.
- Runtime E2E does not prove full match progression/detailed match/event/stat/rating behavior for all three leagues.
- Position data is largely MANAGER tactical normalization, not public roster position verification.

## 2. Commits

All declared commits exist:

- `b307dded Define real player identity dataset policy`
- `307eeaad Replace generated identities with explicit player rosters`
- `3c103bdb Estimate MANAGER player attributes and special traits`
- `1cf078ee Validate real identity roster import`
- `5564feab Complete three-league real player runtime acceptance`
- `7ce70c8b Close MVP 1 real player dataset audit`

Current commit during audit: `7ce70c8b Close MVP 1 real player dataset audit`.

## 3. Git state

Before creating this report, `git status --short` was clean in the root repository and clean in the frontend repository.  
`git diff --check ad34a103..7ce70c8b` returned no whitespace errors.

After this audit, the only expected change is this new report file.

## 4. Inventory

Inventory was recalculated from `src/main/resources/data/initial`, not copied from implementation reports.

| Item | Actual |
| --- | ---: |
| Countries | 3 |
| Leagues | 3 |
| Club files | 3 |
| Player club files | 70 |
| Spain players | 480 |
| Argentina players | 720 |
| Brazil players | 480 |
| Total players | 1680 |
| Players per club file | 24 min / 24 max |
| Files not containing 24 players | 0 |

Every player has these fields: `externalId`, `fullName`, `displayName`, `dateOfBirth`, `nationalityCode`, `clubExternalId`, `primaryPosition`, `secondaryPositions`, `preferredFoot`, `heightCm`, `shirtNumber`, `attributes`, `marketValue`, `specialAttributes`, `identitySource`, `identityCheckedAt`, `provenance`, `estimatedFields`.

## 5. Identities

Dataset-level checks:

- `identitySource` present for 1680/1680.
- `identityCheckedAt` present for 1680/1680.
- Source distribution: Wikipedia 1041, TheSportsDB 639.
- `identityCheckedAt`: all records use `2026-07-29`.
- Placeholder patterns such as `Player 1`, generated names, `manager-initial`, or `:p01` IDs: 0 found.
- Duplicate `externalId`: 0 found.
- Duplicate `fullName`: 38 names appear more than once across clubs.

The duplicate-name count is not automatically a defect because football has real homonyms and transfers, but the dataset does not provide source URLs or immutable source entity IDs per player, so duplicates are harder to disambiguate.

Important identity defect:

- `src/main/resources/data/initial/players/spain/osasuna.json` contains `Aitor Fernández` and `Fernández`. This is corrupted identity text and prevents full approval.

External spot-checks:

- Public source spot checks found plausible matches for examples such as Real Madrid/Andriy Lunin, Boca Juniors/Miguel Merentiel, and Flamengo/Agustín Rossi/Pedro/Giorgian de Arrascaeta.
- Sources consulted during audit included public Wikipedia pages and TheSportsDB references. Examples: `https://en.wikipedia.org/wiki/Andriy_Lunin`, `https://es.wikipedia.org/wiki/Anexo:Temporada_2026_del_Club_Atl%C3%A9tico_Boca_Juniors`, `https://en.wikipedia.org/wiki/2026_CR_Flamengo_season`, `https://www.thesportsdb.com/`.

This is enough to confirm that the implementation is using many public real names, but not enough to certify all 1680 identities as independently verified because per-player source URLs/endpoints are not stored.

## 6. Cutoff date

The cutoff is explicit and consistent:

- Manifest cutoff: `2026-07-29`.
- Player metadata `identityCheckedAt`: 1680/1680 use `2026-07-29`.
- Player provenance cutoff date: present in player records.

No mixed cutoff dates were found.

## 7. Sources

`MVP1_DATA_SOURCES_AND_LICENSING.md` documents the accepted product risk and the source categories. Per-player `identitySource` is present, but per-player URL/page/endpoint is not. That means the dataset can trace a record to "Wikipedia" or "TheSportsDB", but not to the exact source page without redoing inference.

This is an important audit issue for final data quality, though licensing was explicitly excluded as a blocker by product direction.

## 8. IDs

`externalId` is globally unique across the 1680-player dataset.

However, all 1680 player IDs include the current club slug, for example:

`public-identity:esp:real-madrid:andriy-lunin`

The importer persists UUIDs with:

`deterministicUuid("player:" + source.externalId())`

Therefore:

- Reordering a JSON file does not change IDs.
- Reimporting does not duplicate IDs.
- Changing display name may or may not change IDs depending on whether the slug is changed.
- Moving a player to another club changes `externalId` under the current convention.

This is an important issue because stable identity should not depend on the current club if transfers are part of the product model.

## 9. Clubs and squads

Counts match the declared MVP 1 scope:

- Spain: 20 clubs x 24 players = 480.
- Argentina: 30 clubs x 24 players = 720.
- Brazil: 20 clubs x 24 players = 480.

The dataset is complete by count. Independent full roster correctness was not proven for all 1680 players because exact per-player source URLs are absent and because a 2026 squad can change frequently.

## 10. Positions

Global position distribution:

- GK: 140
- LB: 70
- CB: 210
- RB: 70
- LWB: 70
- RWB: 70
- CDM: 70
- CM: 210
- CAM: 70
- LM: 70
- RM: 70
- LW: 140
- RW: 140
- ST: 210
- CF: 70

Every club has the same tactical template:

- 2 GK
- 7 DEF
- 7 MID
- 4 WINGER
- 4 ATT

This is valid for gameplay balance, but it is not evidence that each real player's public position was verified. `estimatedFields` marks `primaryPosition` as estimated for 1608/1680 players.

## 11. Biographics

All players have biographic fields, but most are estimated:

- `dateOfBirth`: estimated for 1680/1680.
- `secondaryPositions`: estimated for 1680/1680.
- `preferredFoot`: estimated for 1680/1680.
- `heightCm`: estimated for 1680/1680.
- `shirtNumber`: estimated for 59/1680.

No nulls were found for required fields. Estimated data is mostly marked as estimated, which is good. The corrupted `Fernández` value remains a quality issue.

## 12. Attributes

All 1680 players have the six imported attributes:

- `attack`
- `defense`
- `technique`
- `speed`
- `stamina`
- `mentality`

No missing or out-of-range values were found in JSON. The importer validates range `1..99`.

The broader game may consume additional derived stats in match/detail/lineup systems, but for the MVP dataset import contract these six attributes are fully populated.

## 13. Attribute distributions

Calculated from JSON:

| Attribute | Min | Max | Avg | Median | Std dev | P10 | P90 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| attack | 35 | 95 | 67.95 | 70 | 15.56 | 45 | 87 |
| defense | 37 | 92 | 64.91 | 67 | 14.51 | 46 | 83 |
| technique | 51 | 94 | 72.70 | 74 | 8.68 | 61 | 83 |
| speed | 55 | 95 | 74.28 | 75 | 8.76 | 62 | 85 |
| stamina | 61 | 95 | 77.68 | 78 | 6.28 | 70 | 86 |
| mentality | 60 | 89 | 73.49 | 73 | 4.96 | 67 | 80 |

The distribution has playable variety. Position shapes are clearer than club individuality. The identical per-club squad position template is artificial but acceptable as an MVP gameplay normalization if documented.

## 14. Economics

Every player has `marketValue`; `weeklySalary` is computed by importer as `marketValue / 250`. The JSON inventory did not include `weeklySalary` as a source field. No negative or missing market values were observed during importer tests.

The unit policy is documented as MANAGER-estimated, not external copied data.

## 15. Special traits

Trait checks:

- 0 players with 0 traits.
- 0 players with 1 trait.
- 1680 players with exactly 2 traits.
- 0 players with 3+ traits.
- 0 invalid trait codes.
- 0 duplicate trait selections per player.

The traits are explicit in JSON, not generated by the importer. The importer validates trait count, uniqueness, known codes, and position-group compatibility.

## 16. Importer

Relevant class:

- `ThreeLeagueDatasetImporter.java`: approximately 470 lines.

Responsibilities observed in the same application-layer class:

- Reads classpath JSON resources.
- Parses source records.
- Validates countries/leagues/clubs/squads.
- Performs JDBC writes.
- Owns SQL statements.
- Owns transaction annotation.
- Computes deterministic UUIDs.
- Maps source records to DB player records.
- Performs global DB validation.

This is cohesive around "import dataset" but not hexagonal enough for the stated professional architecture bar:

- `application.service.world.importer` imports `JdbcTemplate`.
- It uses `ClassPathResource`.
- It has SQL in application.
- It has `@Service` and `@Transactional`.
- It has no explicit persistence/resource adapter boundary.

No `block()` or `subscribe()` was found in this importer. The importer is blocking JDBC by design, but it is a batch/admin path rather than a WebFlux request path.

## 17. Idempotence

Verified by `ThreeLeagueDatasetImporterTest.secondImportIsIdempotent`, which imports twice into a temporary DB and compares counts for clubs, teams, players, and trait rows.

Covered:

- second import does not duplicate rows;
- trait coverage remains valid.

Not covered:

- transfer preserving identity;
- display-name correction preserving identity;
- source record reordering by hash comparison;
- attribute update hash comparison.

## 18. Rollback

Verified partially by:

- `ThreeLeagueDatasetRuntimeAcceptanceE2ETest.importerRollsBackWhenWriteFails`
- `ThreeLeagueDatasetImporterTest.validationDetectsBrokenTraitCoverage`

Covered:

- a write conflict causes transactional rollback of generated clubs/teams/players;
- broken trait coverage is detected by global validation.

Not fully covered:

- separate corrupt fixtures for duplicate player, duplicate external ID, club missing, invalid position, missing identity, missing attribute, out-of-range attribute, one trait, three traits, duplicate trait, nonexistent trait, and mid-country failure.

Rollback is partially proven, not exhaustively proven.

## 19. Main database

Read-only query against local `football_manager`:

- `players` with `source_system='manager-mvp1-explicit'`: 0.
- `clubs` with `source_system='manager-mvp1-explicit'`: 0.

Therefore the main DB was not imported with the final dataset at audit time. This is a critical MVP readiness blocker.

## 20. Runtime

`ThreeLeagueDatasetRuntimeAcceptanceE2ETest` covers all three declared leagues:

- imports dataset;
- exposes leagues and teams through web APIs;
- checks team counts;
- checks first team has at least 24 players;
- checks six base attributes are present;
- starts a playable career;
- reads career squad;
- runs lineup auto-select;
- validates generated trait coverage in DB.

It does not cover, for each league:

- fixture generation through a played round;
- standings after match completion;
- detailed match persistence;
- timeline events;
- ratings;
- player stats;
- restart/recovery;
- season simulation.

Runtime readiness is partial.

## 21. Frontend

Frontend validation:

- Development build: passed.
- Production build: passed.
- ChromeHeadless tests: passed with `TOTAL: 1016 SUCCESS`, `2 skipped`.

Source search found frontend models/services for display names and squad/lineup flows. The audit did not prove a live browser visual pass for one club per league. It also did not prove that the frontend shows the two special traits with descriptions in the MVP career squad UI.

## 22. Backend tests

Commands executed:

- `mvn -q -DskipTests test-compile`: passed.
- `mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test`: passed.
- `mvn -q test`: passed.

Surefire totals:

- Tests: 2444
- Failures: 0
- Errors: 0
- Skipped: 4

## 23. Frontend tests

Commands executed in `front-ciber/project`:

- `npm run build -- --configuration development`: passed.
- `npm run build`: passed.
- `npm test -- --watch=false --browsers=ChromeHeadless`: passed.

Karma total:

- `TOTAL: 1016 SUCCESS`
- 2 skipped

## 24. Documentation

The documentation is broadly consistent with counts and cutoff date. Issues:

- It concludes approval more strongly than the evidence allows.
- It does not sufficiently highlight that the main DB local instance was not imported.
- It does not flag club-dependent player IDs as an identity-stability issue.
- It does not flag `Aitor Fernández`.
- It describes runtime acceptance more strongly than the E2E actually proves.

## 25. Critical findings

1. Main DB is not imported with the final dataset: `0` final-source players and `0` final-source clubs in local `football_manager`.
2. The importer is not hexagonal: application class owns Spring/JDBC/resource/SQL/transaction concerns.
3. One real identity is corrupted in the source JSON: `Aitor Fernández`.

## 26. Important findings

1. Player `externalId` depends on club slug, so transfers change identity.
2. Per-player source is generic (`Wikipedia` or `TheSportsDB`) without exact page/endpoint URL.
3. 1608/1680 primary positions are estimated tactical normalization, not independently verified public positions.
4. Runtime E2E does not prove detailed match, events, ratings, stats, standings, full round finalization, or restart/recovery for all three leagues.
5. Rollback scenarios are only partially covered.
6. Frontend tests pass, but trait display in real player UI was not independently proven visually.

## 27. Minor findings

1. 38 duplicate full names appear across clubs; likely partly legitimate homonyms/transfers, but require source entity IDs for confident disambiguation.
2. Import report text still says "fictional dataset entries" inside `ThreeLeagueDatasetImporter.importDataset()`, which is stale wording.
3. The identical 24-player position structure in every club is artificial; acceptable for MVP balance only if clearly treated as tactical normalization.

## 28. MVP 1 readiness

| Question | Classification |
| --- | --- |
| Do the 70 clubs have real names? | Yes |
| Do the 1680 players have real public-looking names? | Partial |
| Are all 1680 identities independently verifiable from stored metadata? | No |
| Do club and position correspond to cutoff date? | Partial |
| Are attributes complete? | Yes |
| Are traits complete? | Yes |
| Is the main DB imported? | No |
| Does runtime work in the three leagues? | Partial |
| Does frontend show traits? | Partial / not proven |
| Is the importer professional and hexagonal? | No |
| Is import idempotent? | Yes, for counts |
| Is rollback complete? | Partial |

## 29. Conclusion

The dataset is a meaningful step forward: counts are correct, names are no longer generated placeholders, all players have explicit sources, required attributes are complete, every player has exactly two valid traits, and the automated backend/frontend suites are green.

However, this is not ready for final MVP 1 approval. The main database is not populated, IDs are not transfer-stable, one identity is visibly corrupt, the importer still violates the requested hexagonal boundary, and runtime coverage is not as complete as the documentation claims.

Final verdict: `REJECTED`.

## Remediation addendum - 2026-07-29

This section was added after the original independent audit verdict and does not rewrite the original findings.

Remediation implemented after the audited baseline:

- Corrected the corrupted Osasuna identity to `Aitor Fernández`.
- Removed club-dependent player `externalId` values and replaced them with transfer-stable IDs using `public-player:<normalized-name>:<date-of-birth>:<nationality>`.
- Added concrete per-player traceability fields: `identitySourceName`, `identitySourceRef`, `sourceEntityId`, `positionSourceRef`, `positionCheckedAt`, and `positionEstimated`.
- Removed the fixed 24-slot position template from the generated dataset state. Remaining unverifiable positions are explicit MANAGER estimates, not presented as source-verified facts.
- Added import validation for corrupt text, old ID format, missing source refs, invalid positions, invalid attributes and invalid trait selection.
- Replaced the old artificial squad-balance validation with playable minimum validation: one goalkeeper and ten outfield players.
- Updated the rollback fixture to use the new transfer-stable ID.
- Added `MVP1_PLAYER_POSITION_REVIEW.md`, `MVP1_IDENTITY_TRACEABILITY_REPORT.md`, and `MVP1_REAL_DATASET_REMEDIATION_REPORT.md`.

Post-remediation dataset checks:

- Clubs: 70.
- Players: 1680.
- Duplicate `externalId`: 0.
- Club-dependent `externalId`: 0.
- Corrupt player-name text detected by dataset scan: 0.
- Players missing `identitySourceRef`: 0.
- Players missing `positionSourceRef`: 0.
- Clubs without goalkeeper: 0.
- Players with exactly two traits: 1680.

Post-remediation focal validation:

- `mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test`: passed.

Remediation verdict for the corrected dataset/import path: `APPROVED`.

## Principal database closure addendum - 2026-07-29

This addendum records the later closure evidence against the real local `football_manager` database.

- `.env` was loaded in-session without printing secrets.
- Backup created before import: `backups/football_manager_before_mvp1_real_dataset_20260729.dump`.
- Operational import executed against local `football_manager` using the documented importer runner:
  `mvn -q spring-boot:run "-Dspring-boot.run.arguments=--app.world.import.three-league=true --spring.main.web-application-type=none"`.
- Import log reported: 3 countries, 3 leagues, 70 clubs, 70 teams, 1680 players, 3360 traits.
- Principal DB checks after import: 0 invalid trait counts, 0 orphan traits, 0 duplicate `external_id`, 0 old fictional/generated identity IDs, 0 corrupt name markers, 0 club-dependent player IDs.
- Spot check: `public-player:aitor-fernandez:1991-07-13:esp` persists as `GK`.
- Runtime smoke against the principal DB passed for Spain, Argentina and Brazil league/team/squad loading; Spanish career creation, 4-4-2 auto-select, 11-slot lineup recovery, fixtures, standings, live round start, persisted match lookup and detailed match retrieval.

Closure verdict for the corrected dataset/import/runtime path: `APPROVED`.
