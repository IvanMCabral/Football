# PB1.2.3H7.9D reset P50 closure

## Current status

`RESET PERFORMANCE P1 NOT CLOSED`. The local fast path is implemented and
validated, and the public runtime now exposes sanitized forensic telemetry.
The previously reported registration 422 was reproduced as a probe-contract
failure, not as a production registration failure. A valid public flow now
registers and completes the bootstrap, but the public N=10 latency gate
remains open: all ten resets returned 204 while client p50 remains above the
1,500 ms target.

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
- runtime commit tested: `9c69eace`; Render's public response does not expose
  the live SHA, so the live SHA remains `SHA_NOT_EXPOSED`;
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
- public reset N=3 after the final runtime commit: 3/3 HTTP 204, 0 HTTP 500,
  0 HTTP 503, all `MODERN_MANIFEST`, version `1`, scan count `1`;
- public reset N=10 after the final runtime commit: 10/10 HTTP 204, 0 HTTP
  500, 0 HTTP 503, `MODERN_MANIFEST` count 10, legacy count 0;
- reset client timings (ms): 2674, 2701, 2669, 2659, 2705, 2652, 2718,
  2660, 2647, 2651; p50 2669 ms, p95 2718 ms;
- reset server timings (ms): 2466, 2450, 2457, 2452, 2446, 2445, 2480,
  2455, 2444, 2444; p50 2455 ms, p95 2480 ms;
- reset cleanup timings (ms): 2116, 2100, 2108, 2103, 2097, 2096, 2130,
  2106, 2095, 2096; p50 2106 ms, p95 2130 ms;
- representative public telemetry: discovery 175 ms, manifest read 175 ms,
  projection scan 174 ms, child unlink 174 ms, metadata 175 ms, root unlink
  174 ms, tombstone 174 ms, game cleanup 0 ms;
- all measured layers are comparable provider round trips; projection scan is
  not dominant and was retained for the documented compatibility fallback;
- the exact classification is `L_PROVIDER_GENERAL_LATENCY`: latency is
  distributed across the sequential cleanup graph and no single stage
  dominates;
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
- [final reset fast-path forensics](evidence/pb123h79d/reset-fast-path-forensics-20260809.json)
