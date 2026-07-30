# MVP 1 Final Acceptance Remediation Report

Date: 2026-07-30

Verdict: `APPROVED`

## Summary

The independent release audit initially rejected the MVP 1 closure because Redis was unavailable during suite execution, several architecture and writer boundaries were still incomplete, 22 clubs had insufficient goalkeeper coverage, importer idempotence evidence was count-only, runtime/recovery evidence was incomplete, and frontend special-trait tests were missing.

This remediation closes those findings with code, tests, runtime validation and principal database evidence.

## Evidence

### Redis and backend suite

```text
redis-cli PING -> PONG
mvn -q -DskipTests test-compile -> SUCCESS
mvn -q test -> SUCCESS
Surefire: tests=2448 failures=0 errors=0 skipped=4
```

### Goalkeepers and squad coverage

```text
clubs=70
players=1680
specialTraits=3360
clubsWithInvalidRosterSize=0
clubsWithLessThanTwoGoalkeepers=0
playersWithInvalidPosition=0
playersWithoutExactlyTwoTraits=0
```

Detailed correction report:

`docs/data/MVP1_GOALKEEPER_AND_SQUAD_COVERAGE_REVIEW.md`

### Importer architecture

- Application services no longer import `JdbcTemplate`, `ClassPathResource`, `DataSource`, `DataSourceTransactionManager`, `DatabaseClient`, `java.sql`, adapters or infrastructure.
- Seed resource loading is behind `SeedResourceLoader`.
- Legacy seed SQL writers live under infrastructure.
- Application depends on `WorldSeedPlayerWriter` and `WorldSeedTeamWriter` ports.
- `ApplicationLayerBoundaryTest` enforces the boundary.

### Writer invariants

- Legacy seed writes are blocked on principal database `football_manager` by default.
- Override is explicit and tested.
- Principal catalog import remains handled by the explicit MVP 1 importer.

### Idempotence and rollback coverage

`ThreeLeagueDatasetImporterTest` now verifies:

- full import counts;
- exactly two traits per player;
- no orphan traits;
- minimum two goalkeepers per club;
- stable logical fingerprint after repeated imports across players, squads and traits;
- stable IDs across repeated imports;
- validation failure detection for broken trait coverage.

### Runtime and recovery

Focused runtime suite:

```text
mvn -q "-Dtest=ThreeLeagueDatasetRuntimeAcceptanceE2ETest,TestHarnessControllerE2ETest,CareerFlowE2ETest,RoundControllerE2ETest,DetailedMatchControllerIntegrationTest" test
result=SUCCESS
```

Runtime report:

`docs/data/MVP1_THREE_LEAGUE_RUNTIME_RECOVERY_ACCEPTANCE.md`

### Frontend special traits

```text
npm run build -- --configuration development -> SUCCESS
npm run build -> SUCCESS
npm test -- --watch=false --browsers=ChromeHeadless -> 1021 SUCCESS, 0 failed, 2 skipped
```

Player card tests now cover:

- 0 traits;
- 1 trait;
- exactly 2 traits;
- more than 2 traits;
- stable display order;
- code/name/description payload;
- UTF-8 names and descriptions;
- backend-style goalkeeper traits.

### Principal database after remediation

```text
countries=3
leagues=3
clubs=70
teams=70
players=1680
player_special_attributes=3360
legacy_players=0
orphan_traits=0
duplicate_source_ids=0
players_not_two_traits=0
clubs_less_than_two_gk=0
invalid_positions=0
missing_source_refs=0
```

## Final acceptance

MVP 1 release dataset, importer boundaries, writer guardrails, runtime evidence, frontend special traits, Redis-backed backend suite and principal database integrity are accepted for the current MVP 1 closure.
