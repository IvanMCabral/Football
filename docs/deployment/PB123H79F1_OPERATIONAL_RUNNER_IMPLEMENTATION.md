# PB1.2.3H7.9F.1 — Operational runner implementation

## Components

| Component | Responsibility |
|---|---|
| `WorldV2CanaryCertifiedAuthority` | Immutable H7.9F identity and capacity authority |
| `WorldV2CanaryRunner` | Exact one-owner validation, one-shot arming, ordered preflight, result/exit mapping |
| `WorldV2CanarySourceProbe` | Read-only product source proof |
| `WorldV2CanaryCapacityProvider` | Fresh current-storage sample only |
| `ProductWorldV2CanarySourceProbe` | Product-port implementation of source/reference/catalog inspection |
| `UpstashManagementCapacityProvider` | GET-only Management API adapter with runtime-only credentials |
| `WorldStorageMigrationOrchestrator` | Existing migration and write-time CAS boundary |
| `WorldV2CanaryApplication` | Dedicated process boundary |

## Implemented remediation

- P1-01: authority moved from replaceable properties into an immutable value
  object. Runtime echoes are optional confirmations and mismatch fails before
  probes.
- P1-02: capacity safety is calculated from immutable ceilings/floors. Provider
  data can no longer self-report quota or safety margins.
- P1-03: `EXECUTE` and its confirmation use raw exact equality.
- P1-04: `AtomicBoolean.compareAndSet(false, true)` executes inside the cold
  publisher boundary, so calls and re-subscriptions share one attempt.
- P2-01: concurrent `zipWith` was replaced by sequential source validation,
  fresh capacity sampling, and immediate orchestration.
- P2-02: `ALREADY_MIGRATED_VALID` is not a runner success; if unexpectedly
  returned, it becomes `ORCHESTRATOR_FAILED` with non-zero exit.
- P2-03: a real normal Spring Boot application context proves the entire canary
  operational path absent, complemented by the four-case activation matrix.

## One-shot and result semantics

The first invocation consumes the runner before configuration validation. The
guard is never reset after validation, provider, capacity, partial, or
orchestrator failure. The maximum orchestrator invocation count per runner
instance is one. Accepted results are only read-only validation pass or migrated.

## Runtime versus retained proof

Runtime revalidates owner hash, LEGACY state, source SHA, reference absence,
career absence, catalog compatibility, fingerprint, and fresh current storage.
The semantic plan SHA remains the immutable certified plan authority; the probe
does not fabricate a runtime plan digest. The existing migration executor CAS
continues to reject source changes between preflight and commit.

## Operational status

This is local remediation for independent re-review. Public canary execution is
not authorized and no production/provider operation was performed.
