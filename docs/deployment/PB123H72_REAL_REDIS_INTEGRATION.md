# PB1.2.3H7.2 — Real Redis integration evidence

`RedisCareerDataCleanupRepositoryRealIntegrationTest` runs against the
ephemeral password-protected Redis process started by the existing test
environment post-processor. It does not use Upstash, Docker, Render or a
remote account.

Coverage includes:

- owners A and B with separate careers;
- root, owner index, immutable owner mapping, world, user projections,
  runtime, detail and baseline keys;
- more than 205 owner projections and batch reconciliation;
- owner A cleanup preserving owner B keys and mapping;
- corrupt A index containing B's career ID;
- fail-closed rejection before any foreign SCAN/deletion;
- DBSIZE before/after and `keysActuallyDeleted` reconciliation.

The test currently passes in five scenarios with zero failures and zero errors.
Mockito tests remain for short-count expiry and unexplained-shortfall
simulation because those race windows are deliberately deterministic there.
