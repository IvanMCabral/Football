# PB1.2.3H7.6 — Real Redis stale-writer matrix

| Scenario | Redis-backed evidence | Expected result |
|---|---:|---|
| stale career update after reset | yes | rejected, no root mutation |
| stale world context | yes | rejected, owner data preserved |
| tokenless career/state/runtime writer | yes | rejected before mutation |
| stale state context | yes | rejected, no child key |
| stale runtime context | yes | rejected, no child key |
| mapping compensation token A after token B | yes | A cannot delete B |
| owner B during owner A cleanup | yes | B remains writable |
| owner/index cardinality boundary | yes | limit enforced without cross-owner mutation |
| initial career creation collision | yes | fail closed |
| league-team persistence failure | local behavior test | error propagates |

The integration class uses an ephemeral Redis instance and exact generated keys;
it does not access Upstash. Test-only `Mono.never()` and impossible-driver
failures are not counted as real Redis scenarios. The final local review records
the exact test count produced by the current suite.
