# PB1.2.3H7.7 — Real Redis evidence

All scenarios below ran against the repository's ephemeral real Redis test
fixture, not a Mockito substitute:

- 17 tests in `RedisCareerDataCleanupRepositoryRealIntegrationTest`;
- productive `AdvanceMatchUseCase` stale G1 callback after G2 reset;
- productive `MatchSimulationUseCase.advanceMatch` stale G1 callback after G2 reset;
- valid G2 runtime and match-state writes;
- Redis serialization round-trip for both entities;
- tokenless writer rejection;
- owner-B preservation and generation fencing;
- DB size unchanged after rejected stale callbacks;
- exact runtime/state key preservation.

Additional focused coverage: 3 serialization tests and 2 active-world command
tests. No Upstash, Neon, Render, Firebase or public data was accessed.

The fixture measured DB size before and after stale callbacks and asserted no
key-count mutation. This is local evidence only and is not an extrapolation to
the public Redis instance.
