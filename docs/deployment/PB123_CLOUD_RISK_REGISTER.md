# PB1.2.3 Cloud staging risk register

| Risk | Probability | Impact | Mitigation | Gate |
| --- | --- | --- | --- | --- |
| Cloud Run cold start | Medium | Medium | Keep staging scale-to-zero but measure first request; use min instance for beta if needed | P1 |
| Firebase/Cloud Run SSE buffering or timeout | Medium | High | Absolute API origin, heartbeat, five-minute and restart/reconnect drill | P0 |
| Redis loss of durable career state | Medium | Critical | Provider backup/restore plus SQL durability migration before public beta | P0 |
| Free-tier sleep/archive/quota disable | High | Medium | Monitor quota, synthetic uptime, paid plan decision and budget alert | P1 |
| Neon connection limit or cold compute | Medium | High | Separate pooled R2DBC/JDBC endpoints, small pool, load smoke | P0 |
| CORS or origin mismatch | Medium | High | Exact HTTPS origins, preflight/auth test, no wildcard | P0 |
| Unexpected billing/egress | Medium | High | Budget alerts, max instances, spend caps, immutable retention cleanup | P0 |
| Secret leakage or weak rotation | Low | Critical | Secret manager/OIDC, no JSON keys in Git, rotation drill | P0 |
| Flyway migration incompatibility | Low | High | Pre-release dump, staging migration, idempotence and restore validation | P0 |
| Rollback leaves schema ahead | Medium | High | Backward-compatible migrations and database backup before release | P1 |
| Region distance from Argentina | Medium | Medium | Measure RTT from browser/backend to Neon/Upstash; choose common closest region | P1 |
| Scale-to-zero during active match | Medium | High | reconnectable SSE, persisted commands/state, lifecycle drill | P0 |
| Provider changes free plan | Medium | Medium | Keep Docker/SQL portability, review provider terms monthly, maintain alternative | P1 |
