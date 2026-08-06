# PB1.2.3H7.3 - real Redis integration evidence

The ephemeral Spring integration suite uses the production
`ReactiveRedisTemplate` and serializers. It covers owner A/B isolation,
205-plus projections, DBSIZE accounting, maximum batch 100, corrupt ownership,
immutable mappings, world TTL, index cardinality 255/256/257, concurrent saves,
root and ownership touch renewal.

Mockito focal tests cover deterministic provider behaviours that a normal Redis
server cannot produce reliably: `deleted > requested`, unexplained shortfall,
EXISTS failure and the root-last error path. The adapter remains production
code in both suites; tests do not contact Upstash, Render, Neon or Firebase.

The current real integration class contains eight passing scenarios. Exact
counts from the final validation are:

- real Redis integration class: 8 passing scenarios;
- focused H7.3/registry/HTTP/game group: 82 tests, 0 failures, 0 errors;
- full backend suite: 264 reports, 2617 tests, 0 failures, 0 errors, 4 skipped;
- `ShotCoordinateAttachmentTest`: 3/3 standard runs passed.
