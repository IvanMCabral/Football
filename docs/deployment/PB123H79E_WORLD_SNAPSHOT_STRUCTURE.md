# PB1.2.3H7.9E — World snapshot structure

`WorldSnapshot` contains the owner user identifier, leagues, world teams, and
world players. A representative public success serialized approximately
2,483,461 bytes and contained 3 leagues, 70 teams, and 1,680 players. Exact
canonical-versus-owner payload ratios were not exported; the evidence keeps
only counts and byte totals.

Canonical league, team, and player data is sourced from PostgreSQL during the
instrumented path. The owner-specific snapshot is persisted by
`RedisWorldRepository` and ownership is initialized by
`CareerOwnershipTouchService`.

The snapshot is not a gameplay model change. This document records the data
shape needed to attribute reload latency and storage growth.
