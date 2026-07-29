# MVP 1 Three-League Runtime Acceptance Report

## Verdict

`APPROVED WITH ISSUES`.

Runtime acceptance passes for the explicit fictional dataset. It does not prove real roster licensing.

## Covered runtime flow

The focused E2E covers Spain, Argentina and Brazil:

- import dataset;
- list leagues;
- list teams;
- list team players;
- create career;
- load career squad;
- auto-select lineup;
- validate exactly two trait rows per imported player;
- rollback on a mid-import conflict.

## Focused evidence

```bash
mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test
```

Result: green.

## Remaining runtime scope for a future real-roster closure

The current test does not yet cover a full minute-by-minute detailed match, backend restart and recovery for the real roster because no licensed real roster exists in the repository.
