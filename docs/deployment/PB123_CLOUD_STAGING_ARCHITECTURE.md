# PB1.2.3 Cloud staging architecture

## Target topology

```text
Browser
  |
  | HTTPS, Firebase Hosting custom domain
  v
Angular SPA (Firebase Hosting)
  |
  | absolute API origin, Authorization header, exact CORS
  v
Cloud Run service (Spring Boot WebFlux, Docker, prod profile)
  |                         |
  | TLS/JDBC + R2DBC        | TLS/ACL, bounded timeouts
  v                         v
Neon PostgreSQL             Upstash Redis
```

The API should use a dedicated origin such as `https://api-staging.<owned-domain>`, not a committed provider URL. Firebase Hosting currently rewrites SPA paths to `index.html`; no API rewrite is present in `front-ciber/project/firebase.json`. Keeping API traffic absolute avoids relying on Firebase proxy buffering for SSE. A same-origin rewrite may be evaluated only after a real five-minute SSE test; it is not the staging default.

## Existing frontend contract

The API base is currently defined in:

- `front-ciber/project/src/app/environments/environment.ts`
- `front-ciber/project/src/app/environments/environment.prod.ts`

Both currently contain `apiUrl: '/api/v1'`, with production debug routes disabled and SSE enabled. A later provisioning change must add an explicit staging replacement (for example `environment.staging.ts`) and set `apiUrl` to the HTTPS API origin. Do not put a secret in Angular configuration; the URL is public configuration. SSE must use the same API origin as normal HTTP calls.

## Cloud Run configuration proposal

- Region: start with `southamerica-east1` if Neon/Upstash latency is acceptable; otherwise choose the nearest common region and record the measured RTT.
- `min-instances=0` for disposable staging; `max-instances=1` initially; raise only after load and LiveSession isolation evidence.
- Initial concurrency target: 20, not the platform maximum, because SSE and live sessions hold connections.
- Request timeout: configure up to 3600 seconds only after heartbeat/reconnect tests. A Cloud Run timeout closes the stream with 504; a normal match must never depend on an uninterrupted connection.
- Container port: provider `PORT`, `SERVER_ADDRESS=0.0.0.0`, existing non-root image and liveness endpoint.
- Health: liveness may be public; readiness must check PostgreSQL and Redis and return 503 when either dependency is unavailable.
- Shutdown: preserve `server.shutdown=graceful`; drain SSE and reject new mutating commands during the platform termination window.

## Security and routing

- Firebase supplies TLS for the SPA; Cloud Run supplies TLS for the API; the custom domain/DNS provider supplies certificates and DNS only.
- `APP_CORS_ALLOWED_ORIGINS` contains the two exact Firebase origins (and the custom staging origin if used), comma-separated, with no wildcard.
- API responses carrying auth or match state use `Cache-Control: no-store`; SSE uses `no-cache` and must not be buffered by an intermediary.
- Secrets are injected by Cloud Run Secret Manager references or provider secret variables. GitHub Actions uses OIDC/workload identity where available; no service-account JSON belongs in Git.
- Public production/staging mapping must be verified by an ApplicationContext test: editor/debug/seed/test-harness mappings absent, public auth and gameplay mappings present.

## Staging variable inventory

All values are injected by the selected provider; examples below are placeholders, not credentials.

| Variable | Required | Secret | Staging source / rule |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | yes | no | Cloud Run environment; `prod` |
| `PORT`, `SERVER_ADDRESS` | yes/recommended | no | Cloud Run port contract; `0.0.0.0` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | yes | no/role identifier | Neon connection details |
| `DB_PASSWORD` | yes | yes | Cloud Run secret reference |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME` | yes/optional ACL | no/identifier | Upstash endpoint and ACL metadata |
| `REDIS_PASSWORD` | yes | yes | Cloud Run secret reference |
| `REDIS_SSL` | yes | no | `true` for managed TLS |
| `JWT_SECRET` | yes | yes | generated high-entropy value, never Angular/Git |
| `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION` | yes/recommended | no | positive, tested safety window |
| `APP_CORS_ALLOWED_ORIGINS` | yes | no | exact Firebase/custom HTTPS origins, no wildcard |
| `SHUTDOWN_TIMEOUT` | optional | no | align with Cloud Run termination window |
| `APP_RATE_LIMIT_ENABLED`, `APP_AUTH_RATE_LIMIT_MAX_REQUESTS`, `APP_AUTH_RATE_LIMIT_WINDOW` | recommended | no | enabled in `prod`, lower values for staging |
| `JAVA_TOOL_OPTIONS`, `JAVA_OPTS` | optional | no | provider runtime tuning; do not put secrets here |

Fail closed when any required secret or dependency endpoint is absent. Verify presence by health/readiness and startup logs that never echo values.

## Future CI/CD and rollback contract

The later staging workflow must check out a pinned commit, run backend compile/tests and frontend build/tests, build the existing Dockerfile, run Trivy and SBOM generation, push an immutable `staging-<git-sha>` digest, deploy a Cloud Run revision, wait for liveness/readiness, run API/browser smoke, and publish evidence. Firebase deploy uses the same source SHA and an inspected `dist/demo/browser` artifact. No `latest` tag is used for rollback. A failed smoke shifts traffic back to the prior Cloud Run digest and prior Firebase release; schema changes require the PostgreSQL backup gate first.

## Failure and recovery model

PostgreSQL is the system of record for new durable data. Redis remains runtime-critical until PB1.2.4 moves career and match durability fully to SQL. Readiness is false when Redis is unavailable; this is intentional and must be visible in monitoring.

Cloud Run scale-to-zero may drop an idle connection, but it must not lose committed database state. A browser reconnects SSE using the existing round/match identity. An active LiveSession that exists only in Redis is not promised to survive total Redis loss; the release gate therefore requires a measured loss/reconnect drill before public users.

## Observability

Use Cloud Run stdout/stderr logs and request IDs already emitted by the backend. Track readiness, 5xx, latency, container restarts, CPU, memory, R2DBC pool saturation, Redis errors, SSE connection count and backup result. Configure budget alerts because Google billing alerts notify but do not cap charges.
