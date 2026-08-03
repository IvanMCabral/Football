# PB1.2.3 Cloud staging architecture

## Active PB1.2.3A topology

```text
Browser
  |
  | HTTPS, Firebase Hosting custom domain
  v
Angular SPA (Firebase Hosting)
  |
  | absolute API origin, Authorization header, exact CORS
  v
Render Free Web Service (Spring Boot WebFlux, Docker, prod profile)
  |                         |
  | TLS/JDBC + R2DBC        | TLS/ACL, bounded timeouts
  v                         v
Neon PostgreSQL             Upstash Redis
```

The API should use the Render origin `https://<service>.onrender.com/api/v1` until a real custom domain exists. Firebase Hosting currently rewrites SPA paths to `index.html`; no API rewrite is present in `front-ciber/project/firebase.json`. Keeping API traffic absolute avoids relying on Firebase proxy buffering for SSE. Cloud Run is retained only as a historical paid/billing alternative.

## Existing frontend contract

The API base is currently defined in:

- `front-ciber/project/src/app/environments/environment.ts`
- `front-ciber/project/src/app/environments/environment.prod.ts`

Both currently contain `apiUrl: '/api/v1'`, with production debug routes disabled and SSE enabled. A later provisioning change must add an explicit staging replacement (for example `environment.staging.ts`) and set `apiUrl` to the HTTPS API origin. Do not put a secret in Angular configuration; the URL is public configuration. SSE must use the same API origin as normal HTTP calls.

## Render Free configuration proposal

- Render region: choose the closest available region after comparing Neon/Upstash latency; Render's documented regions do not include South America.
- Plan: `free`, one instance, 512 MB RAM and 0.1 CPU. No persistent disk and no autoscaling.
- Idle behavior: service sleeps after 15 minutes and wakes in about one minute; browser retry/backoff is required.
- Container port: Render-provided `PORT`, `SERVER_ADDRESS=0.0.0.0`, existing non-root image and liveness endpoint.
- Health: liveness may be public; readiness must check PostgreSQL and Redis and return 503 when either dependency is unavailable.
- Shutdown: Render Blueprint uses a 30-second SIGTERM delay; drain SSE and reject new mutating commands during shutdown.
- Memory gate: current Docker smoke used 768 MB, so 512 MB compatibility must be measured before accepting Render Free.

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

Use Render logs/metrics and request IDs already emitted by the backend. Track readiness, 5xx, latency, restarts, CPU, memory, R2DBC pool saturation, Redis errors, SSE connection count and backup result. Free quota exhaustion must result in suspension/disabled builds, never a paid upgrade.
