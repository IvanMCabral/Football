# PB1.2.1 Final Evidence Integrity Remediation

Date: 2026-08-02

Historical source of truth preserved:

- `docs/deployment/PB12_ACTUAL_SMOKE_LIFECYCLE_FINAL_INDEPENDENT_AUDIT.md`
- Historical verdict: `PB1.2.1 PRODUCTION RUNTIME REJECTED`

This document records the remediation performed after that audit. It does not rewrite the historical verdict.

## Previous defect

The production JAR smoke runner reported `safeSummaryExists=false` as a literal value. That was not acceptable evidence because the field was not derived from a path check and historical files named `manager-prod-jar-smoke-*-summary.json` still existed under `%TEMP%`.

## Current implementation

The official runner remains:

- `tools/run-production-jar-smoke.ps1`

The runner now:

- uses one execution workspace under `%TEMP%\manager-prod-jar-smoke-<runId>\`;
- writes the current run summary only inside that workspace;
- verifies the summary path physically;
- removes the current run summary before PASS;
- verifies the path no longer exists;
- cleans historical summaries matching the exact pattern `manager-prod-jar-smoke-*-summary.json` only under `%TEMP%`;
- emits calculated fields instead of a hardcoded summary flag.

## Evidence fields

Normal PASS now includes:

```json
{
  "currentRunSummaryPath": "C:\\Users\\ichu_\\AppData\\Local\\Temp\\manager-prod-jar-smoke-<runId>\\current-run-summary.json",
  "currentRunSafeSummaryExists": false,
  "historicalSafeSummaryCountBefore": 0,
  "historicalSafeSummaryCountAfter": 0,
  "safeSummaryCleanupVerified": true
}
```

The first remediation execution found and removed 9 historical summaries. Later verified runs reported `historicalSafeSummaryCountBefore=0` and `historicalSafeSummaryCountAfter=0`.

## Negative mode

Added executable lifecycle mode:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -LifecycleTestMode safe-summary-delete-fails
```

Observed result:

- `status=FAIL`
- `currentRunSafeSummaryExists=true`
- `safeSummaryCleanupVerified=false`
- exit code non-zero
- residual processes: 0
- residual ports: 0

The mode uses a real locked file handle to prevent summary deletion. It does not simulate the failure by assigning a boolean.

## Verdict for this remediation item

P0 closed locally. The safe summary evidence is calculated from controlled paths and fails closed when deletion cannot be verified.
