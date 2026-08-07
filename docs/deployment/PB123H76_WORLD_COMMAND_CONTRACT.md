# PB1.2.3H7.6 — World command contract

`LeagueTeamCommandService` performs the Redis league mutation and then updates
the owner world snapshot. If the snapshot is missing, or its persistence fails,
the returned `Mono<Void>` fails. The service no longer uses `onErrorResume` to
turn a failed world write into success.

The command does not accept an arbitrary owner identity from a client path; the
caller supplies the authenticated owner context. A failed snapshot update is
therefore observable to HTTP handling and cannot silently create a partial
success claim. This local contract does not change gameplay or league rules.

The regression test `LeagueTeamCommandServiceTest` verifies that a world writer
failure propagates and that the save operation was attempted. Production
ownership and authentication remain governed by the existing controller
security contract.
