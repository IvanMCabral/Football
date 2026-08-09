# PB1.2.3H7.9D reset P50 closure

## Current status

`RESET PERFORMANCE P1 NOT CLOSED`. The local fast path is implemented and
validated. The previously reported registration 422 was reproduced as a
probe-contract failure, not as a production registration failure. A valid
public flow now registers and completes the bootstrap, but the public N=10
latency gate remains open: all ten resets returned 204 while the measured
client p50/p95 were above the target.

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

## Public rollout evidence

- Render service: `manager-staging-api`;
- live commit: not exposed by the public service; the tested backend branch
  revision was `0aa54436` (the documentation-only evidence commit followed
  afterward);
- Render deployment: live after the manual rollout;
- liveness: 3/3 HTTP 200;
- readiness: 3/3 HTTP 200 (`database=UP`, `redis=UP`);
- registration with a valid JSON request: HTTP 200;
- authenticated bootstrap: 3/3 disposable accounts completed
  register -> me -> reload-world -> leagues -> teams -> career/start -> squad
  -> auto-select -> lineup/current -> confirm, followed by a successful
  lifecycle reset;
- the historical 422 (`LINEUP_VALIDATION_ERROR`, request id
  `9e150668-7068-4ec7-8db3-0bf7fb8829ff`) occurred at
  `POST /api/v1/career/start` after the probe selected `league.id` and
  `team.id`, fields that are not present in the public catalog response. The
  public contract uses `realLeagueId`, `worldTeamId`, and requires the
  authenticated `userId` query parameter for catalog reads;
- public reset N=10: 10/10 HTTP 204, 0 HTTP 500, 0 HTTP 503;
- reset client timings (ms): 2972, 2974, 3021, 3022, 3023, 3030, 3033,
  3035, 3053, 3073; p50 3026.5 ms, p95 3073 ms;
- reset server timings (ms): 2761, 2761, 2762, 2762, 2762, 2764, 2765,
  2768, 2768, 2866; p50 2763 ms, p95 2866 ms;
- reset cleanup timings (ms): 2416, 2416, 2417, 2417, 2417, 2418, 2420,
  2423, 2423, 2521; p50 2417.5 ms, p95 2521 ms;
- the public reset response exposes timing headers only; it does not expose
  `fastPathUsed`, `legacyFallback`, scan count, or manifest cardinality, so
  those fields are not claimed as independently measured public evidence;
- the latency gate is not met (`client p50 <= 1500 ms` and `p95 <= 3000 ms`).
- public remote services were not otherwise modified.

The previous public baseline was client p50 1764.5 ms / p95 2028 ms and
server p50 1535 ms / p95 1706 ms. That baseline remains open until a fresh
deployment creates modern careers and a new public N=10 is measured.

## Reports

- [key discovery inventory](PB123H79D_RESET_KEY_DISCOVERY_INVENTORY.md)
- [provider-side diagnosis](PB123H79D_PROVIDER_SIDE_DIAGNOSIS.md)
- [N=10 provider evidence](evidence/pb123h79d/provider-diagnosis-n10.json)
- [corrected public bootstrap and reset evidence](evidence/pb123h79d/public-bootstrap-n10-20260809.json)
