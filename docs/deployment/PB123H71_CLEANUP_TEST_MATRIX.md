# PB1.2.3H7.1 - Cleanup test matrix

| Case | Evidence | Status |
|---|---|---|
| Owner A does not remove owner B | `RoundEngineRegistryOwnershipTest`, `MatchSessionRegistryOwnershipTest` | PASS |
| Career A does not remove career B | exact career-id patterns and adapter test | PASS |
| Similar UUID prefix does not collide | complete UUID patterns | PASS |
| Existing root removes all families | `CareerSessionServiceCleanupTest` | PASS |
| Missing root with owner index removes details/baselines | `RedisCareerDataCleanupRepositoryTest` | PASS |
| Missing root and index is explicit empty | adapter test | PASS |
| Duplicate SCAN discovery | adapter test | PASS |
| More than 100 keys | adapter test, max batch 100 | PASS |
| Redis reports fewer deletions | adapter result test | PASS |
| Redis deletion failure preserves root | service failure test | PASS |
| Mid-batch failure does not complete reset | propagated reactive error | PASS |
| Repeated reset | owner cleanup is idempotent | PASS |
| Active owner round stops | scoped registry operation | PASS |
| Other owner round remains | registry isolation test | PASS |
| Other owner session remains | session isolation test | PASS |
| No active career | safe empty result | PASS |
| Harness lifecycle | `createCustom` delegates to reset | PASS |
| Quota/error propagation | cleanup exception contract | PASS |
| World TTL/rebuild | property and existing rebuild path | PASS |
| Match-detail retention | configurable property and adapter path | PASS |
| Discovered/unique/requested/deleted | result contract assertions | PASS |
| Batch count/max batch | result contract assertions | PASS |
| Sanitized logging | short owner hash only | PASS |
| Normal career flow | existing suite | PASS |

The local Redis adapter tests use mocks only; no Upstash or real user data is
used.
