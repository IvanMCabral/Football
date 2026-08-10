# PB1.2.3H7.9E — Request graph

```text
DashboardController.reloadWorldSnapshot
  -> WorldSnapshotService.reloadFromDatabase
     -> WorldSnapshotCreator.create
        -> LoadBaseDataService.load
           -> LeagueTeamSyncService.loadLeagueTeamsMap
           -> LeagueLoaderService.loadLeagues
           -> TeamPlayerLoaderService.loadTeamsAndPlayers
        -> RedisWorldRepository.saveInitial
           -> Redis SET + CareerOwnershipTouchService.initializeWorld
  -> WorldStatusQueryService.getWorldStatus(userId, materializedSnapshot)
  -> HTTP 200 + sanitized timing headers
```

The timing overloads use canonical SQL reads for the immutable catalog and bulk
league relation query. The normal non-timing service contract remains intact.
The controller passes the already materialized snapshot to world-status
assembly, avoiding a second multi-megabyte Redis snapshot read on the same
request.

The graph is observable through aggregate timing headers only. It deliberately
does not expose Redis keys, credentials, UUIDs, payloads, or stack traces.
