# MVP 1 Known Limitations

Date: 2026-07-31

## Promotion/relegation

Status: `IMPLEMENTED BUT NOT FULLY CERTIFIED`

Promotion/relegation exists in the product surface, but a dedicated browser/API E2E proving promotion/relegation across season transition has not been completed as part of the MVP 1 freeze.

## npm audit

Status: pending remediation.

`npm audit --json` reports:

- Critical: `3`.
- High: `32`.
- Moderate: `19`.
- Low: `1`.

No `npm audit fix` was executed and no dependency versions were changed during this freeze.

## Historical detailed-match API evidence

Status: release-evidence warning.

The prior two-season evidence recorded that historical detailed-match API probes for certified season 1 and season 2 match ids returned `404/400`, while frontend routes rendered a fallback page. This does not block the MVP 1 playable baseline, but it should be clarified before a production release promise around historical match detail persistence.
