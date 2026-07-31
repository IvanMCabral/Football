# MVP 1 Real Player Identities Final Independent Audit

Date: 2026-07-29
Repository: `D:\ProyectosOpenCode\MANAGER`
Frontend: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`
Mode: technical independent audit, read-only except for this new report.

## 1. Verdict

`REJECTED`

The dataset has the declared scale and many public real names, and the automated suites pass. However, the final real-identity claim is not acceptable for MVP 1 closure because the identity data is not reliably tied to real club/position facts at cutoff date. The main blockers are:

- The main `football_manager` database is not populated with the final dataset.
- `primaryPosition` is largely an artificial tactical template and creates obvious wrong positions for real players.
- Player IDs include the current club slug and are therefore not transfer-stable.
- One Osasuna identity is corrupted: `Aitor Fern?ndez`.
- Per-player source traceability is only generic (`Wikipedia` or `TheSportsDB`), not exact page/endpoint-level.
- The importer remains an application-layer Spring/JDBC/SQL class, not a hexagonal import flow.
- Runtime acceptance is partial and does not prove full match progression/detailed-match behavior for the three leagues.

## 2. Commits

All declared commits exist:

- `b307dded Define real player identity dataset policy`
- `307eeaad Replace generated identities with explicit player rosters`
- `3c103bdb Estimate MANAGER player attributes and special traits`
- `1cf078ee Validate real identity roster import`
- `5564feab Complete three-league real player runtime acceptance`
- `7ce70c8b Close MVP 1 real player dataset audit`

Current audited commit: `7ce70c8b Close MVP 1 real player dataset audit`.

## 3. Git state

`git status --short` was not clean at the start of this audit because a previous independent audit report was already untracked:

- `?? docs/data/MVP1_REAL_PLAYER_DATASET_FINAL_INDEPENDENT_AUDIT.md`

This audit created only:

- `docs/data/MVP1_REAL_PLAYER_IDENTITIES_FINAL_INDEPENDENT_AUDIT.md`

`git diff --check ad34a103..7ce70c8b` returned no whitespace errors for the declared commit range.

## 4. Inventory

Calculated directly from `src/main/resources/data/initial/players`.

| Metric | Actual |
| --- | ---: |
| Player club files | 70 |
| Spain players | 480 |
| Argentina players | 720 |
| Brazil players | 480 |
| Total players | 1680 |
| Files not containing exactly 24 players | 0 |
| Missing required identity fields | 0 |
| Null required identity fields | 0 |
| Duplicate external IDs | 0 |
| Duplicate full names | 38 |
| Placeholder/generated-name patterns | 0 |
| Invalid internal position codes | 0 |
| Invalid club references | 0 |
| Invalid trait selections | 0 |

Source distribution:

- Wikipedia: 1041 players.
- TheSportsDB: 639 players.

Every player has `identityCheckedAt = 2026-07-29`.

Encoding issue:

- `src/main/resources/data/initial/players/spain/osasuna.json` contains `Aitor Fern?ndez`.

## 5. Manifest

`identity-manifest.json` exists and declares:

- dataset: `MANAGER MVP 1 real player identity dataset`
- cutoffDate: `2026-07-29`
- identityCheckedAt: `2026-07-29`
- countries: 3
- leagues: 3
- clubs: 70
- players: 1680
- playersPerClub: 24
- sources: TheSportsDB public team/player pages, Wikipedia public club squad pages, individual public player pages when needed

Limitations:

- It does not list club files individually.
- It does not include checksums.
- It does not include per-player source URL or endpoint.
- It does not allow reconstructing exact provenance for every individual identity without redoing external lookup.

Manifest traceability is adequate for dataset-level intent but not enough for final identity verification.

## 6. Identities

Positive findings:

- The dataset no longer uses obvious generated placeholders such as `Player 1` or `manager-initial`.
- All players have `fullName`, `displayName`, `externalId`, `clubExternalId`, `identitySource`, and `identityCheckedAt`.
- Many names are recognizable public football identities.

Blocking findings:

- At least one identity is corrupted: `Aitor Fern?ndez`.
- `Aitor Fernández` also appears in the same Osasuna file, producing an apparent duplicate/variant around the same real player.
- 38 full names appear in more than one club. Some may be real homonyms or transfers, but the dataset lacks source entity IDs to disambiguate them.
- Several audited players are assigned impossible or wrong primary positions, which makes the identity record not factually reliable as "player + club + position".

## 7. External contrast

Public sources checked during the audit included Wikipedia pages and TheSportsDB references. Representative sources:

- `https://en.wikipedia.org/wiki/Andriy_Lunin`
- `https://en.wikipedia.org/wiki/Aitor_Fern%C3%A1ndez_%28footballer%2C_born_1991%29`
- `https://en.wikipedia.org/wiki/Boca_Juniors`
- `https://en.wikipedia.org/wiki/2026_CR_Flamengo_season`
- `https://en.wikipedia.org/wiki/Club_Atl%C3%A9tico_Independiente`
- `https://en.wikipedia.org/wiki/2026_SE_Palmeiras_season`
- `https://en.wikipedia.org/wiki/Girona_FC`
- `https://www.thesportsdb.com/`

Representative contrast table:

| Liga | Club | Jugador | Dataset | Fuente pública | Coincide | Observación |
| --- | --- | --- | --- | --- | --- | --- |
| Spain | Real Madrid | Andriy Lunin | GK | Wikipedia: Real Madrid GK | Yes | Identity and position plausible |
| Spain | Real Madrid | Antonio Rüdiger | GK | Public records: defender | No | Wrong position |
| Spain | Real Madrid | Arda Güler | LB | Public records: attacking midfielder/winger | No | Wrong position |
| Spain | Barcelona | Alejandro Balde | GK | Public records: left back | No | Wrong position |
| Spain | Barcelona | Lamine Yamal | CM | Public records: winger | No | Wrong position |
| Spain | Osasuna | Aitor Fern?ndez | LB | Wikipedia: Aitor Fernández, Osasuna goalkeeper | No | Corrupt text and wrong position |
| Spain | Girona | Cristhian Stuani | RB | Public records: forward | No | Wrong position |
| Spain | Real Oviedo | Aarón Escandell | GK | Public records: goalkeeper | Yes | Plausible |
| Argentina | Boca Juniors | Carlos Palacios | CB | Boca public/Wikipedia: midfielder/forward | No | Wrong position |
| Argentina | Independiente | Rodrigo Rey | not in first 12 sample; club source confirms GK | Public source lists GK | Partial | Dataset contains many wrong role assignments in same club sample |
| Argentina | Independiente | Federico Mancuello | LB | Public records: midfielder | No | Wrong position |
| Argentina | River Plate | Gonzalo Montiel | CM | Public records: right back | No | Wrong position |
| Argentina | Deportivo Riestra | sample names | templated positions | Public verification limited | Partial | Smaller-club traceability weak |
| Brazil | Flamengo | Agustín Rossi | GK | Wikipedia Flamengo season: GK | Yes | Plausible |
| Brazil | Flamengo | Giorgian de Arrascaeta | CM | Public records: attacking midfielder | Partial | Broadly midfield, but source-level URL not stored |
| Brazil | Palmeiras | Gustavo Gómez | CDM | Public records: centre-back | No | Wrong position |
| Brazil | Palmeiras | Weverton | not in first 12 sample | Public records: goalkeeper | Partial | Sample shows other wrong role assignments |
| Brazil | Santos | Gabriel Barbosa | CB | Public records: forward | No | Wrong position |
| Brazil | Mirassol | Alex Muralha | GK | Public records: goalkeeper | Partial | Plausible, exact source URL not stored |
| Brazil | Remo | Zé Ivaldo | RWB | Public records: defender/centre-back | Partial/No | Over-normalized tactical role |

The external check confirms the dataset uses many real names, but it also proves that `primaryPosition` cannot be treated as verified real roster data.

## 8. Cutoff date

The date is explicit and coherent across manifest and player metadata:

- `2026-07-29`

The issue is not missing date metadata. The issue is that the dataset does not preserve exact source pages and uses broad tactical normalization that can contradict public positions.

## 9. Sources

`MVP1_DATA_SOURCES_AND_LICENSING.md` documents:

- public identity fields;
- MANAGER-estimated fields;
- excluded commercial/protected data;
- accepted product risk.

Important source issue:

- Source is per player only as a broad label (`Wikipedia` or `TheSportsDB`).
- Exact source page/endpoint is not stored per player.
- Exact source field origin is not stored.

This is insufficient for a final independent verification of all 1680 identities.

## 10. IDs

`externalId` is globally unique, but not stable enough:

- All 1680 player IDs include the current club slug.
- Example pattern: `public-identity:esp:real-madrid:andriy-lunin`.
- Import UUID is derived from `deterministicUuid("player:" + source.externalId())`.

Consequences:

- Reordering JSON does not change IDs.
- Reimporting does not duplicate rows.
- Moving a player to another club changes the ID.
- Homonym strategy is not explicit beyond club/name slug.

This blocks approval for a manager game where transfers are expected.

## 11. Clubs

All club files exist and counts match:

- Spain: 20 club files.
- Argentina: 30 club files.
- Brazil: 20 club files.

Club references in player files all point to known club codes.

## 12. Squads

Every club has 24 players and can form a playable squad by count.

Issues:

- 38 duplicate names across clubs.
- Many club files have duplicate shirt numbers.
- Squad positions are identical across all 70 clubs.

The identical structure supports auto-select mechanically, but it is artificial rather than source-accurate roster construction.

## 13. Positions

The dataset uses exactly one repeated position template for every club:

- 2 GK
- 1 LB
- 3 CB
- 1 RB
- 1 LWB
- 1 RWB
- 1 CDM
- 3 CM
- 1 CAM
- 1 LM
- 1 RM
- 2 LW
- 2 RW
- 3 ST
- 1 CF

This produces obvious wrong assignments:

- Antonio Rüdiger as GK.
- Alejandro Balde as GK.
- Aitor Fernández as LB/CB instead of GK.
- Cristhian Stuani as RB.
- Carlos Palacios as CB.
- Federico Mancuello as LB.
- Gonzalo Montiel as CM.
- Gustavo Gómez as CDM.
- Gabriel Barbosa as CB.

`estimatedFields` marks `primaryPosition` for 1608/1680 players, which is honest metadata, but the product request asks for real identities with reasonable club/position accuracy. This implementation does not satisfy that standard.

## 14. Biographics

Estimated fields:

- `dateOfBirth`: 1680/1680 estimated.
- `secondaryPositions`: 1680/1680 estimated.
- `preferredFoot`: 1680/1680 estimated.
- `heightCm`: 1680/1680 estimated.
- `attributes`: 1680/1680 estimated.
- `marketValue`: 1680/1680 estimated.
- `specialAttributes`: 1680/1680 estimated.
- `primaryPosition`: 1608/1680 estimated.
- `shirtNumber`: 59/1680 estimated.

No nulls were found, but estimated data dominates the dataset. Estimated fields are generally marked, which is good. Position estimates, however, are too inaccurate to support the real-identity claim.

## 15. Attributes

All 1680 players have the six expected MANAGER attributes:

- attack
- defense
- technique
- speed
- stamina
- mentality

No missing or out-of-range attribute values were found in JSON.

## 16. Distributions

Attribute distributions:

| Attribute | Min | Max | Avg | Median | Std dev | P10 | P90 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| attack | 35 | 95 | 67.95 | 70 | 15.56 | 45 | 87 |
| defense | 37 | 92 | 64.91 | 67 | 14.51 | 46 | 83 |
| technique | 51 | 94 | 72.70 | 74 | 8.68 | 61 | 83 |
| speed | 55 | 95 | 74.28 | 75 | 8.76 | 62 | 85 |
| stamina | 61 | 95 | 77.68 | 78 | 6.28 | 70 | 86 |
| mentality | 60 | 89 | 73.49 | 73 | 4.96 | 67 | 80 |

The numeric distribution is playable, but wrong tactical positions can make attributes and traits semantically wrong for real players.

## 17. Economics

Every player has `marketValue` in JSON. `weeklySalary` is computed by importer as `marketValue / 250`.

No negative values were observed in dataset validation. Values are documented as MANAGER estimates, not copied commercial data.

## 18. Special traits

Trait validation from JSON:

- 0 players with 0 traits.
- 0 players with 1 trait.
- 1680 players with exactly 2 traits.
- 0 players with 3+ traits.
- 0 invalid trait codes.
- 0 duplicate trait lists.
- 0 position-group incompatibilities against the internal, normalized position group.

Caveat: because many primary positions are wrong, internally-compatible traits may still be semantically wrong for the real player.

## 19. Importer

`ThreeLeagueDatasetImporter.java` remains an application-layer class with these dependencies/responsibilities:

- `JdbcTemplate`
- `ClassPathResource`
- `@Service`
- `@Transactional`
- SQL statements
- resource reading
- parsing
- validation
- persistence
- UUID generation
- report construction

This is not a clean hexagonal implementation. Application owns infrastructure details directly.

No `block()` or `subscribe()` was found inside the importer itself. Blocking JDBC is used as batch/admin behavior rather than WebFlux request flow.

## 20. Alternative writers

Search found additional production writers that can write player/team-squad data outside the three-league importer, including:

- `PlayerR2dbcRepository`
- `TeamSquadR2dbcRepository`
- `WorldSeedBatchWriter`
- `WorldTeamPostgresWriter`
- legacy world seed services

These paths do not uniformly enforce the new real-identity dataset guarantees such as exactly two traits, stable real identity IDs, and source metadata.

## 21. Idempotence

Validated by tests:

- `ThreeLeagueDatasetImporterTest.secondImportIsIdempotent`

Covered:

- import twice;
- count equality for clubs, teams, players and special-trait rows;
- no duplicate rows by count.

Not covered:

- transfer identity stability;
- display name corrections preserving identity;
- hash comparison of records/relations;
- controlled player additions/removals.

Idempotence is partial but acceptable for count-based import.

## 22. Rollback

Rollback is partially tested:

- `ThreeLeagueDatasetRuntimeAcceptanceE2ETest.importerRollsBackWhenWriteFails`
- `ThreeLeagueDatasetImporterTest.validationDetectsBrokenTraitCoverage`

Not exhaustively tested:

- corrupt fixture matrix for all requested failure modes;
- duplicate identity source entity;
- mid-league failure for Argentina/Brazil;
- missing source metadata;
- wrong/corrupt position semantics.

Rollback remains partial.

## 23. Main database

Read-only query against local `football_manager`:

- countries `ESP/ARG/BRA`: 0
- leagues `ESP-PRIMERA/ARG-PRIMERA/BRA-SERIE-A`: 0
- clubs with `source_system='manager-mvp1-explicit'`: 0
- players with `source_system='manager-mvp1-explicit'`: 0

The main DB is not imported with the final dataset. This is a critical blocker.

## 24. Runtime

`ThreeLeagueDatasetRuntimeAcceptanceE2ETest` covers:

- import;
- league exposure for Spain, Argentina, Brazil;
- team count per league;
- first-team squad count;
- six attributes present;
- career start;
- squad retrieval;
- auto-select;
- special-trait coverage query.

It does not cover:

- names are real in runtime;
- positions are real;
- saving lineup;
- fixture generation;
- full round;
- standings;
- detailed match;
- events;
- ratings;
- stats;
- finalization;
- restart/recovery.

Runtime is partial.

## 25. Frontend

Frontend commands passed:

- `npm run build -- --configuration development`
- `npm run build`
- `npm test -- --watch=false --browsers=ChromeHeadless`

Result:

- `TOTAL: 1016 SUCCESS`
- 2 skipped

Search found display-name usage in squad/live/substitution flows, but did not prove real-player trait display with descriptions in a live browser. Test mocks still use placeholder-like names such as `Bench 1`, which is acceptable in tests but not evidence of real dataset display.

## 26. Backend tests

Commands executed:

- `mvn -q -DskipTests test-compile`
- `mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test`
- `mvn -q test`

Surefire result:

- Tests: 2444
- Failures: 0
- Errors: 0
- Skipped: 4

## 27. Frontend tests

Commands executed:

- `npm run build -- --configuration development`
- `npm run build`
- `npm test -- --watch=false --browsers=ChromeHeadless`

Result:

- 1016 success
- 0 failures
- 2 skipped

## 28. Documentation

Documentation correctly states many fields are MANAGER estimates. However:

- reports conclude approval more strongly than supported by evidence;
- runtime report presents partial runtime as broader acceptance;
- importer design report does not sufficiently flag application-layer JDBC/SQL;
- identity review does not flag position-template failures;
- source documentation lacks per-player exact source URLs;
- stale wording remains in importer report construction: "fictional dataset entries".

## 29. Critical findings

1. Main DB does not contain the final dataset.
2. Position data is largely artificial and demonstrably wrong for many real players.
3. Importer architecture is not hexagonal.
4. One player identity is corrupted: `Aitor Fern?ndez`.

## 30. Important findings

1. Player IDs depend on current club and are not transfer-stable.
2. Per-player source traceability is too generic.
3. Runtime E2E is partial.
4. Rollback matrix is partial.
5. Alternative writers can bypass new dataset guarantees.
6. 38 duplicate names need disambiguating source IDs.
7. Many duplicated shirt numbers exist within club files.
8. Frontend trait display was not visually proven.

## 31. Minor findings

1. Tests contain placeholder names in mocks, acceptable for tests but not proof of real-data display.
2. All clubs have an identical squad position template, which makes squads feel synthetic.
3. The manifest lacks checksums and explicit file list.

## 32. MVP 1 preparation

| Question | Classification |
| --- | --- |
| Do the 70 clubs have players with real names? | Partial |
| Are all 1680 players verifiable? | No |
| Do club and position correspond to cutoff date? | No |
| Are attributes complete? | Yes |
| Are two traits complete? | Yes |
| Is the main DB imported? | No |
| Does complete runtime work in the three leagues? | Partial |
| Does frontend show traits? | Partial / not proven |
| Is importer professional and hexagonal? | No |
| Is import idempotent? | Partial / count-based yes |
| Is rollback complete? | Partial |

## 33. Conclusion

The implementation successfully replaced obvious generated placeholder identities with a large explicit dataset of public-looking football names and achieves the declared 70-club / 1680-player scale. The data compiles, imports in test DBs, and passes automated backend/frontend suites.

It is not ready as a final real-identity MVP 1 closure. The dataset over-normalizes positions by slot, causing severe identity inaccuracies; the local main DB is not populated; IDs are not transfer-stable; exact source traceability is insufficient; and the importer architecture remains below the requested professional hexagonal bar.

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
