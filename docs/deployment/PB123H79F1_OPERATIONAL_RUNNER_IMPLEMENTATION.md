# PB1.2.3H7.9F.1 — Operational runner implementation

## Components

| Layer | Component | Responsibility |
|---|---|---|
| Application | `WorldV2CanaryCapacityProvider` | Fresh capacity sample and admission semantics |
| Application | `WorldV2CanarySourceProbe` | Read-only source/career/catalog proof |
| Infrastructure | `WorldV2CanaryRunner` | Single-owner validation, arming, one orchestrator call, result/exit mapping |
| Infrastructure | `ProductWorldV2CanarySourceProbe` | Normal product read ports plus read-only catalog inspection |
| Infrastructure | `UpstashManagementCapacityProvider` | GET-only Upstash Management API adapter |
| Infrastructure | `WorldV2CanaryApplication` | Dedicated process boundary and deterministic exit status |

All runner beans require the `world-v2-canary` profile and the explicit enable
property. The regular server profile has no runner bean and makes no provider
call.

## Execution contract

1. Validate activation material and parse exactly one owner UUID.
2. Compare the SHA-256 owner hash.
3. Inspect source, ownership, references, and catalog.
4. Fetch fresh provider accounting and apply the capacity contract.
5. Return `VALIDATION_PASS` in the default read-only mode.
6. In armed `EXECUTE`, call `WorldStorageMigrationOrchestrator.migrate` once.

The result preserves typed failure states for owner, source, state, references,
catalog, capacity, provider authentication, arming, partial orchestration, and
unexpected orchestration failures. No outer retry exists. Accepted exit code 0
states are validation pass, migrated, and already-migrated-valid; all other
states return non-zero.

## Provider handling

The adapter authenticates with runtime-only Basic credentials, first confirms
the configured database is visible, then reads the stats endpoint. Only
sanitized storage accounting is retained. Provider authentication failures are
mapped to `VALIDATION_FAILED_PROVIDER_AUTH`; malformed/unavailable accounting is
fail-closed. No provider mutation is possible through this adapter.

## Verification boundary

The implementation was verified with focused unit tests and a Spring context
test using fake local source/capacity ports and the concrete product
orchestrator. No public Redis, PostgreSQL, Render, Upstash, Firebase, or
production credentials were used by the tests.

## Operational status

Implementation is ready for independent review. Public execution remains
explicitly unauthorized until a separate operational authorization is granted.
