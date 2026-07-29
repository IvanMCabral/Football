# MVP 1 Three-League Import Design

Verdict: APPROVED

The importer reads explicit resources under `src/main/resources/data/initial`. It no longer creates final player identities at runtime.

## Dataset layout

- `countries.json`
- `leagues.json`
- `catalogs/player-attributes.json`
- `catalogs/special-attributes.json`
- `clubs/spain.json`
- `clubs/argentina.json`
- `clubs/brazil.json`
- `players/identity-manifest.json`
- `players/{country}/{club-code}.json`

## Player identity model

Every player includes:

- `externalId`;
- `fullName`;
- `displayName`;
- `clubExternalId`;
- `primaryPosition`;
- `identitySource`;
- `identityCheckedAt`;
- MANAGER-estimated attributes;
- exactly two special traits;
- provenance metadata.

External IDs use:

`public-player:<normalized-name>:<date-of-birth>:<nationality>`

IDs are deterministic and remain stable across reimports as long as the identity namespace remains unchanged.

## Runtime import

The importer:

- validates league and club counts;
- validates 24 players per club;
- validates playable squad balance;
- validates trait count and compatibility;
- persists countries, leagues, clubs, teams, players and player traits;
- uses deterministic UUIDs;
- remains idempotent;
- participates in a transaction for rollback on failure.

## Known design tradeoff

The importer still enforces a fixed 24-player MVP roster. Public squads with more players are intentionally cut to 24 for MVP consistency.
