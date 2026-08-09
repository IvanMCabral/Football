# PB1.2.3H7.9D reset P50 closure

## Current status

`RESET PERFORMANCE P1 NOT CLOSED` until a fresh public N=10 proves the
latency gate. The local fast path is implemented and validated; public
deployment evidence is intentionally recorded separately after the runtime
has been rebuilt.

## Design

New careers receive an explicit cleanup-manifest version. Lifecycle-owned
detail, baseline, runtime, state, and command writers register their exact key
inside the fenced career coordinator. The registration script verifies the
owner mapping, generation, and reset tombstone and enforces a hard cardinality
limit of 1,024 members.

Modern explicit-career reset uses the exact manifest, one protected user
projection fallback scan, bounded `UNLINK` batches, metadata cleanup, and root
last. Careers without the explicit marker continue the legacy twelve-family
fallback. An empty manifest is never treated as modern without its version
marker.

## Safety invariants

- owner B isolation: preserved;
- stale generation rejection: preserved;
- root-last deletion: preserved;
- tombstone/retry semantics: preserved;
- batch size: maximum 100;
- manifest registration: idempotent, owner/generation fenced;
- manifest storage: bounded and TTL-limited;
- no global scan, `KEYS`, or manual provider cleanup.

## Validation completed locally

- `mvn -q -DskipTests test-compile`: PASS;
- focused cleanup unit and integration tests: PASS;
- modern writer registration against ephemeral real Redis: PASS;
- modern manifest cleanup leaves only the protected projection fallback scan:
  PASS;
- latency model at 25/50/75 ms command delay: PASS;
- bounded storage budget test: PASS;
- local real Redis cleanup profile remains within the existing gate.

The previous public baseline was client p50 1764.5 ms / p95 2028 ms and
server p50 1535 ms / p95 1706 ms. That baseline remains open until a fresh
deployment creates modern careers and a new public N=10 is measured.

## Reports

- [key discovery inventory](PB123H79D_RESET_KEY_DISCOVERY_INVENTORY.md)
- [provider-side diagnosis](PB123H79D_PROVIDER_SIDE_DIAGNOSIS.md)
- [N=10 provider evidence](evidence/pb123h79d/provider-diagnosis-n10.json)
