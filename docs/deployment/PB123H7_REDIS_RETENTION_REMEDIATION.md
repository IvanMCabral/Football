# PB1.2.3H7 - Redis retention remediation

## Current classification

| Family/template | Owner | TTL/retention | Classification | Cleanup owner |
|---|---|---|---|---|
| `career:{userId}` | user | 30 days, refreshed on use | DURABLE projection | career reset |
| `world:{userId}` | user | provider/application dependent; must be bounded | RECONSTRUCTIBLE from canonical world data, but large | career reset |
| `user:{userId}:*` | user | adapter-specific, generally cache-style | RECONSTRUCTIBLE | career reset |
| `career:{careerId}:match-detail:*` | career | no uniform TTL in the legacy adapter | RECONSTRUCTIBLE, retain only comparison window | career reset / retention job |
| `career:{careerId}:match-baseline:*` | career | 7 days (`BaselineStateRedisAdapter.BASELINE_TTL`) | RECONSTRUCTIBLE | career reset / TTL |
| `runtime:match:{userId}:*` | user/match | short runtime TTL | EPHEMERAL | runtime expiry / reset |
| `match:state:{userId}:*` | user/match | short runtime TTL | EPHEMERAL | runtime expiry / reset |
| `match:commands:{userId}:*` | user/match | short queue TTL | EPHEMERAL | consumer/expiry/reset |

The classification is conservative: a world snapshot can be rebuilt from the
canonical catalog, but it is not treated as disposable until the deployed
rebuild path is verified. No family is called fully restorable until provider
backup/export and restore drills exist.

## Required bounded policy

1. Career reset must run the owner-scoped cleanup wired through
   `CareerDataCleanupRepository`.
2. Harness accounts must call reset in `finally` and record the owner id before
   creating a replacement fixture.
3. Match details need an explicit comparison-retention ceiling; seven days is
   the current baseline reference, not a claim that all detail writes already
   set a TTL.
4. Finished-round runtime/state/commands must not be renewed indefinitely.
5. A scheduled orphan scan may inspect only owners absent from canonical
   ownership tables and must require a quarantine/review record before delete.
6. A per-career storage estimator should reject an audit fixture over budget
   before publishing more match detail.

## Not yet certified

Provider backup/export, restore drill, failover/reconnect behavior under quota
pressure, and a first-event SSE recovery drill remain PB1.2 follow-up work.
