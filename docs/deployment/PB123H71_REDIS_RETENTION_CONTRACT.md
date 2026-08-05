# PB1.2.3H7.1 - Redis retention contract

## World snapshots

`world:{userId}` is reconstructible through
`WorldSnapshotService.reloadFromDatabase` and the canonical PostgreSQL world
view. The production default is the configurable `app.redis.world-ttl` /
`REDIS_WORLD_TTL` value of 30 days. The TTL is applied on save only; reads do
not renew it. A missing or incomplete snapshot follows the existing controlled
rebuild path before a career is used. Career reset remains the definitive
owner cleanup regardless of TTL.

## Match details

`career:{careerId}:match-detail:{matchId}` is used by the detail, history,
statistics and compare views after a finished match. The production default
is configurable `app.redis.match-detail-ttl` /
`REDIS_MATCH_DETAIL_TTL`, 30 days. The existing seven-day baseline TTL remains
independent and is not used as an automatic detail-retention decision. Detail
save refreshes retention; reads do not.

An expired detail is a controlled not-found result and does not alter stored
career results or simulation output. Career reset removes detail and baseline
families using the owner index. Provider backup/restore is not claimed here.

## Safety limits

Properties have safe defaults, are profile-compatible, contain no secrets and
are covered by configuration/build validation. No global migration or cleanup
job is introduced in H7.1. A future orphan reconciler must use the same exact
owner proof and bounded batches.
