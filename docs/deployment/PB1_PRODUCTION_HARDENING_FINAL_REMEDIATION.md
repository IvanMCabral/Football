# PB1.1 Production Hardening Final Remediation

Date: 2026-08-01

Historical input audit: `docs/deployment/PB1_PRODUCTION_HARDENING_INDEPENDENT_AUDIT.md`

The independent audit verdict remains preserved as historical evidence:

`PB1.1 HARDENING REJECTED`

This remediation closes the P0 findings found by that audit and the P1 items directly tied to Internet exposure for the public beta.

## P0 remediation

| Finding | Remediation | Evidence |
| --- | --- | --- |
| `EditorController` registered in `prod` | Restricted to `dev`, `local`, and `test`; removed fixed UUID behavior; non-production calls use authenticated principal. | Production mapping context verifies `/api/v1/editor/**` is absent. |
| League team mutator accepts client `userId` | Restricted to non-production profiles; authenticated principal is authoritative; legacy `userId` is rejected when mismatched. | Production mapping context verifies add/remove mutators are absent in `prod`. |
| Static lineup editor data depended on `/editor/**` | Moved formation/subdivision read endpoints to `/api/v1/lineup-editor/**`; frontend consumers migrated. | Frontend tests and builds green; production mapping no longer needs `EditorController`. |
| Credential defaults versioned | Removed reusable DB/Redis defaults from active test resources and scripts; tests require environment variables or isolated test credentials. | Secret hygiene report lists corrected files without exposing previous values. |

## P1 remediation directly related to Internet exposure

| Area | Remediation |
| --- | --- |
| JWT startup validation | Production startup rejects missing, blank, short, known-insecure, and invalid expiration settings. |
| CORS startup validation | Production startup rejects wildcard, partial wildcard, empty/null origins, missing scheme, paths, and malformed values. |
| Error responses | Controllers and global exception handling no longer return raw exception messages in production; logs retain detailed exceptions and clients receive request IDs. |
| Rate limiting | Minimal configurable beta limiter added for login, register, and refresh token. |
| Password policy | Register/login now reject blank/oversized passwords; registration enforces minimum length. |
| Redis managed-provider readiness | Added optional username, password support, SSL property support, timeout-aware configuration, and fail-fast production validation. |
| Health/readiness | Custom health now checks PostgreSQL and Redis; Redis uses an ephemeral write/read/delete probe and readiness returns 503 when Redis is unavailable. |
| Graceful shutdown | `server.shutdown=graceful` and shutdown timeout configured; operational contract documented as bounded beta behavior. |
| Production debug frontend | Production build uses a replacement route entrypoint with no test-harness import; `dist` inspection confirms no debug route/chunk/text. |
| Security headers/correlation IDs | Added request ID propagation and baseline response headers; CDN/proxy headers documented for PB1.2. |

## Validation

Backend:

- `mvn -q -DskipTests test-compile`: PASS
- `mvn -q test`: PASS
- Full suite run 1: PASS
- Full suite run 2: PASS
- Result: 2553 tests, 0 failures, 0 errors, 4 skipped

Frontend:

- Encoding guard: PASS
- `npm run build -- --configuration development`: PASS
- `npm run build`: PASS
- Production artifact inspection: PASS, no `test-harness`, `TestHarnessPageComponent`, or `debug/test-harness` in `dist/demo`
- `npm test -- --watch=false --browsers=ChromeHeadless`: PASS
- Result: 1029 SUCCESS, 0 failures, 2 skipped

## Scope intentionally not implemented in PB1.1

The following remain PB1.2 infrastructure gates and are documented separately:

- Docker image and runtime image hardening.
- Cloud Run/Firebase deploy configuration.
- CI/CD.
- Managed Redis restore drill and loss/reconnect drill.
- Provider-level backup automation.
- CDN/proxy HSTS/CSP policy rollout.

No gameplay, simulation, sport rules, Docker, Cloud Run, Firebase, CI/CD, deploy, or push was performed.
