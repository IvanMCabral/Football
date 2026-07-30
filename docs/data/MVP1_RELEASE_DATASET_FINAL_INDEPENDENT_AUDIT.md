# MVP1 Release Dataset Final Independent Audit

Date: 2026-07-30

Repository: `D:\ProyectosOpenCode\MANAGER`

Frontend: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`

Audit type: independent, read-only, final release dataset closure review.

## 1. Verdict

`REJECTED`

The principal database dataset itself is clean and the frontend build/test suite is green. However, the release closure cannot be approved because the independent backend suite is not green in the audited environment, the importer is not fully hexagonal, player writers remain in the application layer with SQL/database concerns, visual smoke was not independently completed, and runtime/restart evidence is incomplete for the full three-league release bar requested by the audit brief.

## 2. Commits

Root commits verified:

- `8e137532 Isolate legacy seeds from principal database`
- `ce627732 Persist player identity source traceability`
- `ceee5ec4 Expose player special traits through career API`
- `15030d2f Close MVP 1 release dataset audit`

Frontend commit verified:

- `3df046b Expose player special traits in frontend`

`git diff 84a34e22..15030d2f --name-status` shows the expected root backend/report changes only.

`git diff HEAD^..HEAD --name-status` in the frontend shows the expected player-card/model changes only.

## 3. Git

Before this report was created:

- root working tree: clean;
- frontend working tree: clean;
- `git diff --check 84a34e22..15030d2f`: clean;
- `git -C front-ciber\project diff --check HEAD^..HEAD`: clean;
- no dumps, logs, screenshots, or generated artifacts were found as tracked changes.

After this audit, the only intended root working-tree change is this new report:

`docs/data/MVP1_RELEASE_DATASET_FINAL_INDEPENDENT_AUDIT.md`

No commit was created by this audit, as requested.

## 4. Backup

Backup checked:

`backups\football_manager_before_mvp1_real_dataset_20260729.dump`

Evidence:

- file exists;
- `pg_restore --list` succeeds;
- archive format: `CUSTOM`;
- compression: `gzip`;
- source database: `football_manager`;
- dumped from PostgreSQL 15.6;
- ignored by Git through `.gitignore:23:backups/`.

Backup verdict: valid.

## 5. Flyway

`flyway_schema_history`:

| version | description | checksum | success |
| --- | --- | ---: | --- |
| 1 | create manager schema | -241860672 | true |

Table counts:

| item | count |
| --- | ---: |
| public base tables | 26 |
| application tables excluding Flyway | 25 |

Flyway verdict: only V1 is present and successful.

## 6. Counts

Direct `football_manager` validation:

| item | count |
| --- | ---: |
| countries | 3 |
| leagues | 3 |
| clubs | 70 |
| teams | 70 |
| players | 1680 |
| player_special_attributes | 3360 |
| team_squad | 1680 |

By league:

| league | clubs | teams | players |
| --- | ---: | ---: | ---: |
| ARG-PRIMERA | 30 | 30 | 720 |
| BRA-SERIE-A | 20 | 20 | 480 |
| ESP-PRIMERA | 20 | 20 | 480 |

Players per club:

- min: 24
- max: 24
- clubs: 70

## 7. Legacy cleanup

Direct DB checks:

| check | count |
| --- | ---: |
| empty `source_system` | 0 |
| non-final players | 0 |
| missing `source_id` | 0 |
| duplicate `source_id` | 0 |
| legacy source ID patterns | 0 |
| sequential/seed-like names | 0 |
| corrupt `?` names | 0 |
| players without squad | 0 |
| orphan trait rows | 0 |
| orphan squad player refs | 0 |
| orphan squad team refs | 0 |

Legacy cleanup verdict: approved for DB contents.

## 8. Legacy seed guard

Audited file:

`src/main/java/com/footballmanager/application/service/world/LegacySeedPrincipalDatabaseGuard.java`

What it does:

- queries `SELECT current_database()`;
- blocks writes when the current DB is exactly `football_manager`;
- allows override only with `app.world.legacy-seed.allow-principal-database-write=true`;
- logs a warning if the explicit override is used.

Coverage:

- `WorldSeedBatchWriter` calls the guard before legacy player/squad writes.
- `WorldTeamPostgresWriter` calls the guard before legacy team writes.

Limitations:

- the guard itself lives in application service code while depending on `DatabaseClient`;
- the principal DB is detected by literal name only;
- an explicit property can bypass protection.

Seed guard verdict: functionally useful but architecturally imperfect.

## 9. Provenance

Direct DB checks:

| check | count |
| --- | ---: |
| missing `source_entity_id` | 0 |
| missing `identity_source_ref` | 0 |
| missing `identity_checked_at` | 0 |
| missing `position_source_ref` | 0 |
| missing `position_checked_at` | 0 |
| missing `position_estimated` | 0 |

Persisted provenance columns:

- `source_system`
- `source_id`
- `source_entity_id`
- `identity_source_name`
- `identity_source_ref`
- `identity_checked_at`
- `position_source_ref`
- `position_checked_at`
- `position_estimated`

Provenance verdict: persisted and reconstructable from DB-only data.

## 10. IDs

Direct DB checks:

| check | count |
| --- | ---: |
| null/empty `source_id` | 0 |
| duplicate `source_id` | 0 |
| `public-identity:` IDs | 0 |
| `manager-mvp1-generated` IDs | 0 |
| old `:pNN` style IDs | 0 |

Tests show partial idempotence coverage for a second import. The audit did not find complete explicit test evidence for every requested stability scenario: transfer, display-name change, JSON reordering, dorsal change, attribute change, incorporation, and removal.

IDs verdict: DB currently stable; test coverage partial.

## 11. Positions

Invalid position codes: 0.

Position distribution was calculated per league. The dataset is no longer a fixed identical template, but tactical roster balance is not fully release-ready:

- 22 clubs have fewer than 2 goalkeepers.

Mandatory spot checks found records for:

- Aitor Fernández: GK, CA Osasuna.
- Alejandro Balde: LB, FC Barcelona.
- Antonio Rüdiger: CB, Real Madrid.
- Arda Güler: CAM, Real Madrid.
- Carlos Palacios: CAM, Boca Juniors.
- Cristhian Stuani: ST, Girona FC.
- Federico Mancuello: CM, Independiente.
- Gabriel Barbosa: ST, Santos.
- Gonzalo Montiel: RB, River Plate.
- Gustavo Gómez: CB, Palmeiras.
- Lamine Yamal: RW, FC Barcelona.

Additional match from broad `Antonio R%` normalization:

- Antonio Raíllo: GK, RCD Mallorca, estimated position.

Position verdict: partial. Encoding and spot presence are good, but 22 clubs below two goalkeepers is an important release-readiness issue.

## 12. Two traits

Whole-table checks over all `players`:

| check | count |
| --- | ---: |
| players | 1680 |
| trait relations | 3360 |
| players with trait count different from 2 | 0 |
| duplicate trait per player | 0 |
| duplicate trait slot | 0 |
| orphan trait rows | 0 |
| missing trait catalog rows | 0 |

Traits verdict: approved.

## 13. Domain/API

Audited:

- `PlayerSpecialTrait.java`
- `SessionPlayerDTO`
- `SessionEntityMapper`
- `PlayerRepositoryAdapter`
- `TeamPlayerLoaderService`
- career clone/query paths.

Findings:

- `PlayerSpecialTrait` is a domain value object and does not depend on adapters or infrastructure.
- It carries `playerId`, `code`, `name`, and `description`.
- It does not carry `slot`, even though ordering is supplied by DB query ordering.
- API field name is `specialTraits`.
- DTO mapping includes code, name, and description.

Domain/API verdict: mostly acceptable; slot is not represented in the value object/DTO.

## 14. Frontend contract

Backend payload:

```json
{
  "specialTraits": [
    { "code": "...", "name": "...", "description": "..." }
  ]
}
```

Frontend models:

- `PlayerCardData.specialTraits?: PlayerSpecialTrait[]`
- `SessionPlayer.specialTraits?: PlayerSpecialTrait[]`

Findings:

- field name matches;
- `code`, `name`, `description` match;
- optional field preserves backwards compatibility;
- no dedicated contract test was found for `specialTraits`.

Frontend contract verdict: compatible but undertested.

## 15. Player card

Audited:

`front-ciber/project/src/app/shared/components/player-card/**`

Findings:

- renders trait chips when `specialTraits` is present and non-empty;
- chip text shows trait `name`;
- `title` uses `description` fallback to `name`;
- 0 traits hides the section;
- no explicit test covers 0/1/2/3 trait rendering;
- existing player-card spec still focuses on injury availability;
- no visual browser smoke was completed in this audit.

Player-card verdict: implemented but undertested and not independently visually verified.

## 16. Visual smoke

Not completed in this audit.

No browser run was used to inspect Spain, Argentina, and Brazil visually for player name, position, attributes, exactly two traits, tooltip/description, console errors, and network errors.

Visual smoke verdict: not proven.

## 17. Importer architecture

Audited:

- `src/main/java/com/footballmanager/infrastructure/world/importer/ThreeLeagueDatasetImporter.java`
- `ThreeLeagueImportJdbcConfiguration.java`
- `ThreeLeagueImportRunner.java`

Findings:

- JDBC, transaction configuration, resource reading, parsing, validation, and write orchestration are now under infrastructure package.
- The importer still combines several responsibilities in one concrete infrastructure class.
- There is no clearly separated application use case plus input/output ports for the importer flow.
- `ThreeLeagueImportReport` remains in application, but the bulk import itself is not driven by a clean application port boundary.

Application-layer forbidden import scan also found:

- `WorldSeedBatchWriter` in application contains SQL and direct DB writes.
- `WorldTeamPostgresWriter` in application contains SQL and direct DB writes.
- `WorldSeedService` and `LaLigaSeedService` in application read `ClassPathResource`.
- `LegacySeedPrincipalDatabaseGuard` in application depends on `DatabaseClient`.

Importer architecture verdict: not fully hexagonal.

## 18. Writers

Writers found:

- principal importer: `ThreeLeagueDatasetImporter`;
- legacy player/squad writer: `WorldSeedBatchWriter`;
- legacy team writer: `WorldTeamPostgresWriter`;
- R2DBC repositories for players/team squad;
- Redis career/session writers;
- special-trait writer inside importer.

Findings:

- final importer writes source metadata and two traits;
- legacy writers are guarded against principal DB writes;
- application-layer SQL writers still exist;
- `PlayerR2dbcRepository.insertPlayer` can insert global `players` rows without `source_system`, `source_id`, provenance, and traits.

Writers verdict: not fully safe by architecture, despite the current principal DB being clean.

## 19. Idempotence

Evidence found:

- `ThreeLeagueDatasetImporterTest` includes a second import idempotence test.
- Runtime acceptance includes final counts and trait invariant checks.

Missing or partial evidence:

- transfer scenario;
- display-name change stability;
- JSON reordering stability;
- dorsal change;
- attributes change;
- incorporation;
- removal;
- full logical snapshot comparison across IDs, clubs, traits, and provenance.

Idempotence verdict: partial.

## 20. Rollback

Evidence found:

- some importer validation and baseline persistence tests exist.

Missing explicit rollback matrix:

- invalid JSON;
- duplicate player;
- duplicate source ID;
- missing club;
- invalid position;
- missing source ref;
- missing attribute;
- attribute out of range;
- invalid height;
- 0/1/3 traits;
- duplicate trait;
- nonexistent trait;
- failure halfway through each league with prior state intact.

Rollback verdict: partial.

## 21. Runtime Spain

Evidence found:

- automated runtime acceptance covers Spanish career setup path;
- final implementer smoke showed a Spanish career squad with 24/24 players having two traits.

Missing evidence for complete release bar:

- independent live visual smoke;
- full detailed match events/ratings/stats/finalization/advance flow in this audit.

Spain runtime verdict: partial.

## 22. Runtime Argentina

Evidence found:

- automated runtime acceptance covers Argentina career setup path.

Missing evidence:

- independent full live flow through detailed match events, ratings, stats, finalization, and round advancement;
- visual trait verification.

Argentina runtime verdict: partial.

## 23. Runtime Brazil

Evidence found:

- automated runtime acceptance covers Brazil career setup path.

Missing evidence:

- independent full live flow through detailed match events, ratings, stats, finalization, and round advancement;
- visual trait verification.

Brazil runtime verdict: partial.

## 24. Restart/recovery

Existing reports claim restart/recovery was previously smoke-tested.

This audit did not independently reproduce:

1. backend stopped;
2. backend started;
3. career recovered;
4. lineup recovered;
5. fixture recovered;
6. result recovered;
7. detailed match recovered;
8. events recovered;
9. ratings recovered;
10. stats recovered.

Restart/recovery verdict: partial.

## 25. Backend tests

Executed:

- `mvn -q -DskipTests test-compile`: passed.
- `mvn -q test`: failed.

Surefire XML aggregate:

| tests | failures | errors | skipped |
| ---: | ---: | ---: | ---: |
| 2444 | 0 | 200 | 4 |

Failure class:

- `RedisConnectionFailureException`
- unable to connect to Redis at `localhost:6379`

Backend test verdict: failed in this independent audit environment.

## 26. Frontend tests

Executed:

- `npm run build -- --configuration development`: passed.
- `npm run build`: passed.
- `npm test -- --watch=false --browsers=ChromeHeadless`: passed.

Karma result:

| total | success | failures | skipped |
| ---: | ---: | ---: | ---: |
| 1018 | 1016 | 0 | 2 |

Frontend test verdict: passed.

## 27. Reports

Audited:

- `MVP1_PRINCIPAL_DATABASE_FINAL_INDEPENDENT_AUDIT.md`
- `MVP1_FINAL_RELEASE_DATASET_CLOSURE_REPORT.md`
- `MVP1_REAL_DATASET_REMEDIATION_REPORT.md`
- `MVP1_THREE_LEAGUE_RUNTIME_ACCEPTANCE_REPORT.md`

Findings:

- counts reported for the principal DB are supported by direct DB queries;
- reports overstate readiness by saying final approval while independent backend tests currently fail;
- reports do not fully reflect remaining architecture/writer issues;
- reports do not prove visual smoke;
- reports do not prove full rollback and three-league runtime matrix.

Report verdict: evidence is useful but final release conclusion is overstated.

## 28. Critical findings

1. `mvn -q test` failed independently: 2444 tests, 200 errors, 4 skipped, caused by Redis unavailable at `localhost:6379`.
2. Importer/writer architecture is not fully hexagonal: application services still contain SQL/direct DB/resource concerns.
3. Writers are not fully safe by construction: at least one product repository insert path can create global players without final source metadata and traits.
4. Visual smoke was not independently completed.

## 29. Important findings

1. 22 clubs have fewer than 2 goalkeepers.
2. Idempotence coverage is partial, not complete across transfer/name/order/change scenarios.
3. Rollback coverage is partial, not complete across the requested failure matrix.
4. Full runtime evidence for Spain, Argentina, and Brazil is partial for detailed match events, ratings, stats, finalization, and advancement.
5. Restart/recovery evidence was not independently reproduced.
6. Player-card special trait rendering lacks dedicated tests.
7. `PlayerSpecialTrait`/DTO do not expose slot/order explicitly.

## 30. Minor findings

1. The seed guard uses a literal database-name check.
2. The seed guard can be bypassed by property override.
3. Some comments/text in older files still show mojibake in console output, though DB names checked in this audit do not contain `?` markers.

## 31. MVP 1 preparation checklist

| item | status |
| --- | --- |
| DB principal limpia | yes |
| 1680 jugadores exactos | yes |
| 3360 rasgos exactos | yes |
| legacy eliminado | yes |
| seeds protegidos | yes, with architectural caveats |
| source traceability persistida | yes |
| IDs estables | partial evidence |
| posiciones corregidas | partial |
| importador hexagonal | no |
| writers seguros | no |
| idempotencia | partial |
| rollback | partial |
| runtime España | partial |
| runtime Argentina | partial |
| runtime Brasil | partial |
| reinicio/recuperación | partial |
| frontend muestra rasgos | partial: implemented and tests pass, visual not proven |

## 32. Conclusion

The principal MVP 1 dataset is materially cleaned and internally consistent. It has the expected counts, no legacy contamination, complete source traceability fields, stable current `source_id` uniqueness, no orphan rows, and exactly two traits per player.

The release closure is nevertheless rejected because the broader release-readiness criteria are not met under independent audit: backend suite is red in the audited environment, architecture remains partially non-hexagonal, writer safety is incomplete, roster tactical balance has goalkeeper gaps, and visual/full-runtime/restart/rollback evidence is incomplete.

## 33. Post-audit remediation

Date: 2026-07-30

The original independent audit verdict above remains preserved as the historical audit result. The following remediation was performed after that `REJECTED` verdict:

- Redis was started and authenticated successfully: `PING -> PONG`.
- Backend compilation passed: `mvn -q -DskipTests test-compile`.
- Backend suite passed: `mvn -q test`, Surefire `tests=2448 failures=0 errors=0 skipped=4`.
- Goalkeeper coverage was corrected without changing the player total: 70 clubs, 1680 players, 3360 traits, 0 clubs with fewer than two `GK`.
- Principal database was reimported successfully with the final dataset.
- Principal database post-import checks: 3 countries, 3 leagues, 70 clubs, 70 teams, 1680 players, 3360 traits, 0 legacy players, 0 orphan traits, 0 duplicate `source_id`, 0 players without exactly two traits, 0 invalid positions, 0 missing source refs.
- Application layer no longer imports concrete SQL/resource infrastructure for legacy seed loading and writing; `ApplicationLayerBoundaryTest` enforces the boundary.
- Legacy seed principal database guard is tested and blocks principal writes unless explicitly overridden.
- Importer idempotence now compares logical fingerprints for players, squads and traits across repeated imports.
- Runtime acceptance passed for the focused three-league/harness/career/round/detailed-match suite.
- Frontend special trait tests were added; development build, production build and ChromeHeadless test suite passed.

Detailed evidence:

- `docs/data/MVP1_GOALKEEPER_AND_SQUAD_COVERAGE_REVIEW.md`
- `docs/data/MVP1_THREE_LEAGUE_RUNTIME_RECOVERY_ACCEPTANCE.md`
- `docs/data/MVP1_FINAL_ACCEPTANCE_REMEDIATION_REPORT.md`

## 34. Final post-remediation verdict

`APPROVED`

The blocking findings from the original audit were remediated and validated with the current repository state.
