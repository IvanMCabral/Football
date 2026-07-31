# MVP 1 Baseline Freeze

Date: 2026-07-31
Branch: `feat/v25d99.20.3.1-runtime-fixes`
Baseline commit before release docs: `df7c13e0`
Suggested tag: `v1.0.0-mvp1`

## Final MVP state

MVP 1 is frozen as a clean playable baseline. This freeze does not add features, does not change gameplay, does not change match probabilities and does not reopen broad architectural refactors.

Approved areas:

- Architecture.
- LiveSession ownership and encapsulation.
- Same-instance thread safety.
- Detailed match minute pipeline.
- MVP 1 dataset and import baseline.
- Browser/user flow for the current MVP loop.
- Two short seasons E2E evidence.
- Runtime restart/recovery evidence.

## Final reproducibility validation

Backend:

- `mvn -q -DskipTests test-compile`: PASS.
- `mvn -q test`: PASS.
- Tests: `2529`.
- Failures: `0`.
- Errors: `0`.
- Skipped: `4`.

Frontend:

- `npm run build -- --configuration development`: PASS.
- `npm run build`: PASS.
- `npm test -- --watch=false --browsers=ChromeHeadless`: PASS.
- Visible text encoding guard: PASS, `381` files scanned.
- Tests: `1026 SUCCESS`.
- Failures: `0`.
- Skipped: `2`.

## Dataset baseline

Main PostgreSQL database `football_manager`:

- Countries: `3`.
- Leagues: `3`.
- Clubs: `70`.
- Teams: `70`.
- Players: `1680`.
- Player traits: `3360`.
- Traits per player: `2`.

Countries/leagues covered:

- Spain.
- Argentina.
- Brazil.

## Runtime baseline

- PostgreSQL primary dataset validated.
- Redis authenticated runtime validated.
- Backend local runtime validated.
- Angular frontend builds and tests validated.
- Browser smoke and two-season evidence exist in active QA reports.

## Two-season E2E baseline

The short-league E2E evidence validates:

- UI career creation.
- Squad/lineup setup.
- Fixture generation for a four-team division.
- Season 1 completion.
- Season 2 completion.
- Standings reconciliation.
- Live match tactical interaction.
- Substitution evidence.
- Restart/recovery evidence.

## Legacy review

No risky legacy deletion was performed during this freeze.

Observed legacy/compatibility elements are either:

- actively used,
- test-covered,
- compatibility-preserving,
- dataset/import safety related,
- or documented historical audit material.

Examples retained intentionally:

- `infrastructure.world.legacyseed`: protected by `LegacySeedPrincipalDatabaseGuard` and still covered by tests.
- `DivisionScheduler` legacy fallback: active compatibility path for non-division or non-60-team leagues.
- Deprecated `FixtureAdminService` methods: compatibility wrapper; retained because callers/tests may still depend on the public API.
- Frontend detailed-match compatibility constants: retained as isolated backend compatibility naming.

## Known limitations

See `docs/release/MVP1_KNOWN_LIMITATIONS.md`.

## MVP 2 backlog

The MVP 2 backlog starts from known limitations only:

- Fully certify promotion/relegation E2E.
- Classify and remediate npm audit findings.
- Decide whether detailed historical match persistence must be PostgreSQL-backed or Redis/runtime-backed for release policy.
- Continue feature work only after creating the MVP 1 tag.
