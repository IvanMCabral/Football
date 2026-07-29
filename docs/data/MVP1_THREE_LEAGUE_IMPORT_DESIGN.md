# MVP 1 Three-League Import Design

## Verdict

`APPROVED WITH ISSUES`.

The importer now reads explicit player files from `src/main/resources/data/initial/players`. It no longer creates the definitive roster in runtime. The current explicit roster is fictional and redistributable, not a licensed real-player roster.

## Source layout

```text
src/main/resources/data/initial/
  countries.json
  leagues.json
  catalogs/
    player-attributes.json
    special-attributes.json
  clubs/
    spain.json
    argentina.json
    brazil.json
  players/
    spain/<club-code>.json
    argentina/<club-code>.json
    brazil/<club-code>.json
    explicit-player-policy.json
```

Each club has one explicit JSON file with 24 player records.

## Stable identifiers

Player source IDs use:

```text
manager-initial:<country-code>:<club-code>:p<two-digit-number>
```

Database UUIDs are deterministic from:

```text
player:<externalId>
```

This keeps repeated imports stable. For a future real roster, external IDs must be based on a licensed provider namespace or an internal private registry, not array position.

## Import behavior

- Countries, leagues, divisions, clubs, teams, seasons and season competitions are upserted.
- Players are upserted by stable source identity.
- Squad membership is linked to the club team.
- Secondary positions are rewritten from the explicit file.
- Special attribute slots are rewritten from the explicit file.
- The whole import runs inside the configured transaction manager.

## Validation

The importer validates:

- club count by league;
- player count per club;
- valid country references;
- valid club reference per player file;
- complete attributes;
- attribute range 1-99;
- height range 160-210;
- concrete supported domain position;
- balanced tactical position groups;
- exactly two special attributes;
- no duplicate trait assignment;
- trait catalog existence;
- trait position compatibility;
- duplicate player external IDs.

## Dataset status

| Requirement | Status |
| --- | --- |
| Explicit files player by player | satisfied |
| Runtime generation removed from definitive import | satisfied |
| 3 countries / 3 leagues / 70 clubs | satisfied |
| 1680 players | satisfied |
| Exactly two explicit traits per player | satisfied |
| Real-player roster identities | not satisfied |
| Redistribution-safe public dataset | satisfied only for fictional data |

## Operational command

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.world.import.three-league=true"
```
