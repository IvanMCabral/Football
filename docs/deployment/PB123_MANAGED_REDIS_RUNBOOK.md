# PB1.2.3 Managed Redis runbook

## Provider decision

Use Upstash Redis for staging only after confirming the selected region and plan support the application's command rate. Free currently means 256 MB, 10 GB monthly bandwidth and 500K commands/month. Pay-as-you-go starts at $0.20 per 100K commands. The free tier is suitable for a low-volume staging environment, not an SLA-backed public beta.

## Provisioning contract

- Enable TLS and use the provider endpoint/port.
- Use the provider token as `REDIS_PASSWORD`; set `REDIS_USERNAME` only when ACL metadata is provided.
- Set `REDIS_SSL=true`, explicit connect/command timeouts and reconnect policy.
- Do not enable eviction for durable families. If a provider plan forces an eviction policy, it is not acceptable for those families.
- Prefix all staging keys to prevent accidental cross-environment access.
- Keep health probes on a short-lived unique key and never use a production key for readiness.

## Key classification

| Family | Class | TTL | Loss tolerance | Restore requirement |
| --- | --- | --- | --- | --- |
| `career:{userId}` and career/session save | DURABLE, NOT RECONSTRUCTIBLE | none until product policy exists | none for committed career | restore drill mandatory; migrate primary durability to PostgreSQL before public beta |
| `standing:{userId}:*` | DURABLE, RECONSTRUCTIBLE PARTIAL | season lifetime | low | restore or rebuild from complete SQL fixtures/results and compare |
| `career:{careerId}:match-detail:{matchId}` | DURABLE, RECONSTRUCTIBLE PARTIAL | retention policy to be chosen | low for audit/detail | provider backup plus SQL comparison |
| `match:state:{userId}:{matchId}` | EPHEMERAL, NOT RECONSTRUCTIBLE | match lifetime | active match may be lost | loss/reconnect drill; readiness 503 on outage |
| `runtime:match:{userId}:{matchId}` | EPHEMERAL, NOT RECONSTRUCTIBLE | match lifetime | active session may be lost | no false promise; test restart and reconnect |
| baseline/comparison | CACHE, RECONSTRUCTIBLE | short | high | no restore required |
| command/live-session queues | EPHEMERAL, NOT RECONSTRUCTIBLE | command window | command may be rejected | drain on shutdown; do not report success if publisher is ignored |
| world snapshot | DURABLE, RECONSTRUCTIBLE PARTIAL | career lifetime | low | SQL reload or provider backup, verified per career |

## Backup and restore drill

Use the provider Backup/Export operation and, where enabled, a daily backup retained for the provider's documented one- or three-day window. Restore into a separate database, verify key count, representative values, TTLs, career login, standings and detailed match. Test a full loss by disabling the source endpoint and confirm readiness turns 503, no false cache miss is returned, and the application recovers only after the restored endpoint is configured.

If the selected free plan does not expose an adequate retained backup, staging may proceed only as disposable staging with the limitation recorded. Public beta requires a paid persistent plan or SQL durability migration. Upstash documents that free inactive databases can be archived after inactivity and restored from a provider backup; this still requires an explicit drill and does not create an SLA.
