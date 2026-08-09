# PB1.2.3H7.9C — Warm performance root cause

## Scope

This closure measures the existing public request paths only. No gameplay,
probabilities, fixtures, datasets, season format or remote Redis data was
changed.

## Request-path inventory

| Path | Warm path | Redis/Postgres stages |
|---|---|---|
| `GET /api/v1/dashboard/user-stats` | `DashboardController → UserStatsService` | cached username lookup, career snapshot, Postgres only on cache miss |
| `POST /api/v1/career/lineup/auto-select` | controller phase guard → cached career → selector → save | one atomic Redis fenced save; selector is in-process |
| `POST /api/v1/career/lineup/confirm` | controller phase guard → cached career → validation | unchanged snapshot is acknowledged without a second Redis write |

The code records total and stage timings through
`application.observability.RuntimeOperationMetrics`, including career load,
algorithm, save, dashboard load, Redis load/save and HTTP totals. This is
low-cardinality application logging; it does not expose payloads or secrets.

## Root cause

The H7.9B measurements were dominated by duplicate owner-scoped career reads
and sequential Redis round trips. Dashboard also repeated the username
lookup. Confirmation persisted an unchanged career after auto-select.

The remediation keeps lifecycle generation and owner mapping validation but
performs the existing-career validation, mapping-token rotation, root write
and index refresh in one atomic Redis script. The lifecycle coordinator still
serializes the operation. Confirmation remains a public persistence hook for
compatibility, while the session service recognizes an unchanged snapshot and
returns a local no-op.

## Remaining limit

The public service still has variable network and provider scheduling latency;
the warm auto-select distribution is within the requested p50/p95 target when
measured without interleaving a confirmation write. Provider dashboard R1–R3
and responsive browser evidence remain operational evidence items, not code
failures.
