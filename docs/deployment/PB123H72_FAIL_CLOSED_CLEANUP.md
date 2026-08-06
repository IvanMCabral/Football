# PB1.2.3H7.2 — Fail-closed cleanup contract

`CareerDataCleanupResult` exposes sanitized counters and one explicit status:

- `NOT_STARTED`
- `IN_PROGRESS`
- `PARTIAL_RETRYABLE`
- `COMPLETED`
- `REJECTED_OWNERSHIP`
- `FAILED`

UNLINK runs sequentially in batches of at most 100. When Redis reports fewer
deletions than requested, the adapter checks only that batch with `EXISTS`:

- absent after UNLINK: `missingAtDelete` / concurrent expiry;
- still present: `unexplainedShortfall`, `PARTIAL_RETRYABLE`, and an error.

An unexplained shortfall or any Redis operation error prevents deletion of the
career root. The service can safely retry; no atomicity is claimed for keys
already removed before a later failure. Logs contain only a short owner hash,
family, counters and sanitized failure reason.
