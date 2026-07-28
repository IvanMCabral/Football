# MANAGER — Full Stack Regression Final Report

Date: 2026-07-28

## Verdict

BLOCKED.

The code-level regression suite is green after the frontend text/encoding fixes, but the requested real end-to-end runtime validation could not be completed because the local PostgreSQL service rejects the configured credentials during backend startup. The browser connector also failed before it could attach to the in-app browser, so no trustworthy visual browser evidence could be captured from this environment.

## Repository baseline

- Backend repository: `D:\ProyectosOpenCode\MANAGER`
- Backend baseline commit before this validation: `f7ed2ddd Fix remaining refactor report encoding`
- Frontend repository: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`
- Frontend baseline commit before this validation: `3412cc7 fix visual smoke polish`

## Corrections applied

Frontend-only corrections were applied to remove visible mojibake and make text assertions match the UTF-8 product strings:

- `src/app/features/match-detail/pages/v24-match-detail-page.component.ts`
  - `Posesión`
  - `sesión viva activa`
- `src/app/features/games/components/formation-modal/formation-modal.component.ts`
  - modal warning and helper texts use UTF-8 accents and symbols.
  - responsive CSS contract exported for stable test verification.
- `src/app/features/games/components/formation-modal/formation-modal.component.spec.ts`
  - expectations updated to public UTF-8 text and exported responsive CSS.
- `src/app/features/games/components/substitution-modal/substitution-modal.component.ts`
  - recommendation, goalkeeper, position tweak, and server-error copy normalized to UTF-8.
- `src/app/features/games/components/substitution-modal/substitution-modal.component.spec.ts`
  - expectations updated to public UTF-8 text.
- `src/app/features/games/components/partido-modal/partido-modal.component.ts`
  - visible formation-position copy normalized to UTF-8.

No backend production code was changed in this validation pass.

## Frontend validation

Commands executed from `D:\ProyectosOpenCode\MANAGER\front-ciber\project`:

| Validation | Result |
| --- | --- |
| `npm ci` | Passed |
| `npm run build -- --configuration development` | Passed |
| `npm run build -- --configuration production` | Passed |
| `npm test -- --watch=false --browsers=ChromeHeadless` | Passed: 1016 success, 0 failures, 2 skipped |

Production build output:

- initial raw bundle: 420.67 kB
- estimated transfer: 118.11 kB
- output folder: `dist/demo`

Known non-regression note:

- `npm ci` reported existing dependency audit findings: 55 vulnerabilities. No dependency update was performed because the task was regression validation and bug correction, not dependency remediation.

## Backend validation

Commands executed from `D:\ProyectosOpenCode\MANAGER`:

| Validation | Result |
| --- | --- |
| `mvn -q -DskipTests test-compile` | Passed |
| `mvn -q test` | Passed: 3750 tests, 0 failures, 0 errors, 8 skipped |
| `mvn -q -Dtest="*MatchEngine*,*DetailedMatch*,*Lineup*,*TestHarness*,*Lifecycle*,*Discipline*,*League*,*Controller*,*Reactive*,*Stream*,*Simulation*" test` | Passed |

The focused test pass covered the requested areas:

- detailed match engine;
- lineup;
- harness;
- lifecycle;
- discipline;
- league simulation;
- WebFlux/reactive controllers and stream endpoints;
- persistence adapters used by match detail and baseline flows.

## Runtime startup and HTTP smoke

Local services detected:

- Redis listening on port 6379.
- PostgreSQL listening on port 5432.
- Frontend dev server started and responded on `http://localhost:4200/` with HTTP 200.

Backend startup attempts:

1. `mvn spring-boot:run -Dspring-boot.run.profiles=local,career-mutations`
2. `mvn spring-boot:run -Dspring-boot.run.profiles=test,career-mutations`
3. `mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.profiles=test,career-mutations`
4. Local `.env` loaded into the same PowerShell process before starting `local,career-mutations`.

All backend runtime attempts failed before binding port 8080 with:

```text
Error creating bean with name 'flywayInitializer'
Unable to obtain connection from database:
FATAL: la autenticación password falló para el usuario 'postgres'
SQL State: 28P01
```

Result:

- `GET http://localhost:8080/api/v1/health` could not be executed successfully because backend never reached listening state.
- API smoke, authenticated flows, WebSocket/SSE/polling validation, and persistence compatibility through the running app remain blocked by local PostgreSQL authentication.

## Browser/UI validation

The browser validation skill was loaded and the Browser connector was attempted against `http://localhost:4200/`.

Browser connector failure:

```text
failed to write kernel assets: El sistema no puede encontrar la ruta especificada. (os error 3)
```

The troubleshooting call failed with the same connector error. Because the backend also could not start, no reliable in-app end-to-end browser flow could be completed.

## Contract and compatibility assessment from automated tests

Within the completed automated coverage:

- frontend builds and unit tests confirm the Angular code compiles and the affected modal/detail copy is consistent;
- backend complete suite confirms application/domain/adapters compile and behavior remains green;
- focused backend suite exercises match engine, detailed simulation, lineup, test harness, lifecycle, discipline, league simulation, reactive controllers, streaming, and persistence adapters;
- Redis/Postgres-backed behavior is covered by existing backend integration/E2E tests under the test setup.

What is not proven by this environment:

- manual runtime API smoke against `localhost:8080`;
- visual browser journey through the live application;
- browser-observed SSE/WebSocket/polling behavior;
- real local Postgres credential compatibility outside the test harness.

## Temporary artifacts

Temporary runtime and test log files created during validation were removed:

- `.runtime-backend.pid`
- `backend-runtime-smoke*.log`
- `front-ciber/project/.runtime-frontend.pid`
- `front-ciber/project/frontend-runtime-smoke*.log`
- `front-ciber/project/frontend-test-current.log`

## Final status

The code regression portion is green. The full requested end-to-end closure cannot be honestly marked complete until the local PostgreSQL credential mismatch is resolved and the browser connector can attach successfully.

Verdict: BLOCKED.
