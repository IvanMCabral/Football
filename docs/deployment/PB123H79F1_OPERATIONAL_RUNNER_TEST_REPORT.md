# PB1.2.3H7.9F.1 — Operational runner final remediation test report

## Review lineage

The latest independent review remains historically `PB1.2.3H7.9F.1
OPERATIONAL RUNNER REJECTED`. This report records the subsequent targeted local
remediation and does not rewrite that verdict.

## Fresh focused results

| Group | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Runner | 48 | 0 | 0 | 0 |
| Production authority | 8 | 0 | 0 | 0 |
| Canonical owner hashing | 3 | 0 | 0 | 0 |
| Provider parsing | 9 | 0 | 0 | 0 |
| Canary contexts and activation | 7 | 0 | 0 | 0 |
| Migration/reference/physical supporting suites | 55 | 0 | 0 | 0 |
| `WorldStorageV2NegativeControlsTest` | 34 | 0 | 0 | 0 |
| **Total** | **164** | **0** | **0** | **0** |

No security-critical test was skipped.

## F1 evidence

- The production authority test uses `WorldV2CanaryCertifiedAuthority.h79f()` directly.
- The production authority digest is 64-character lowercase hexadecimal SHA-256.
- Synthetic unit tests use an explicitly named synthetic authority and no longer claim production binding.
- The dedicated Spring context uses the production authority bean without a test override.
- A local private-evidence smoke recomputed the same canonical owner digest with two implementations and matched production authority.
- The raw owner was not written to source, tests, documentation, evidence, logs, or the commit.

## F2 evidence

The provider tests accept only an integral, non-negative `current_storage` that
fits in a Java `long`. Missing, null, textual, fractional, negative, overflow,
and malformed JSON inputs fail closed. A valid-looking `total_monthly_storage`
never substitutes for a missing or invalid certified metric.

## F3 evidence

The concurrency regression uses two worker threads, a two-party ready latch,
and a shared start latch. Both fully armed calls race against the same runner.
The observed global limits are one source probe, one capacity sample, and one
orchestrator invocation; the loser receives `RUNNER_ALREADY_INVOKED`.

## Production activity

Render deploys, public `VALIDATE_ONLY`, public `EXECUTE`, public Redis writes,
public PostgreSQL writes, catalog writes, cleanup, provider changes, credential
changes, and billing changes: all zero.

`PUBLIC CANARY EXECUTION AUTHORIZED = NO`.
