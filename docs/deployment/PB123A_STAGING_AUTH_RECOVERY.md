# PB1.2.3A staging auth recovery

## Root causes

- The production Angular build used `/api/v1` as a relative API base. Firebase Hosting rewrote `/api/v1/auth/**` to `index.html`, so the browser received HTML instead of reaching Render.
- Authentication validation was executed synchronously before the reactive pipeline. Generic `IllegalArgumentException` handling could therefore classify an auth failure as `LINEUP_VALIDATION_ERROR`.

## Remediation

- Production and staging Angular builds now use the explicit Render origin:
  `https://manager-staging-api.onrender.com/api/v1`.
- SSE uses the same absolute API base.
- Authentication errors now have dedicated public codes for validation, duplicate email and invalid credentials.
- Synchronous auth validation is deferred into the reactive request pipeline.

## Evidence

- Production artifact inspection confirms the Render API origin is present and no relative API base or localhost URL is present.
- Frontend production and staging builds pass.
- Frontend suite passes with 1037 successes and 2 skipped tests.
- Backend auth integration and error-classification tests pass.

## Public staging evidence

- Frontend: `https://manager-4f952.web.app`.
- Backend: `https://manager-staging-api.onrender.com`.
- Render is serving the authentication fix from the validated branch and its readiness endpoint reports both database and Redis `UP`.
- Direct public smoke with a fresh non-sensitive test identity: register `200` with tokens, duplicate register `409 AUTH_EMAIL_EXISTS`, invalid password `422 AUTH_VALIDATION_ERROR`, login `200`, `/auth/me` `200`, liveness `200`, readiness `200`.
- Browser smoke from the Firebase-hosted SPA: registration completes and navigates to `/dashboard`; a subsequent login also completes and navigates to `/dashboard`.
- CORS preflight and actual requests allow only `https://manager-4f952.web.app` with credentials enabled.

## Additional runtime correction

The production-jar lifecycle smoke exposed a separate packaged-runtime issue: the optional three-league importer runs on a worker thread, whose context class loader is not guaranteed to be Spring Boot's executable-jar loader. Dataset resources were therefore reported as missing even though they were packaged. `ThreeLeagueDatasetImporter` now resolves resources through its application class loader explicitly. The smoke harness also waits for the asynchronous importer to materialize the league/team prerequisite after readiness instead of racing it.

Focused lifecycle coverage and the complete backend suite pass after this correction.

## Final validation run

- Backend: `mvn -q -DskipTests test-compile` and `mvn -q test`; 2579 tests, 0 failures, 0 errors, 4 skipped.
- Frontend development and production builds pass.
- Frontend encoding guard and Karma suite pass; 1031 tests successful, 0 failures, 2 skipped.
- `git diff --check` is clean. The only remaining root working-tree entry is the pre-existing untracked historical audit `docs/deployment/PB123A_ZERO_COST_STAGING_DEFINITIVE_INDEPENDENT_AUDIT.md`; it was not modified or included.

## Public registration incident and final verification (2026-08-03)

The original public reproduction was classified as **C + D**: the request reached
Render while the free instance was starting and the pooled PostgreSQL connection
was stale; the backend eventually returned an error while the Angular component
kept the button in `Registrando...`. There was no browser console error and no
evidence of a frontend CORS rewrite after the absolute API base was deployed.

Observed evidence (sanitized):

- Firebase request URL: `https://manager-staging-api.onrender.com/api/v1/auth/register`.
- Render startup took approximately 120 seconds in the service log; readiness
  returned 200 once the instance was ready.
- The first post-deploy request returned a controlled 500 in the browser. Render
  logged `PostgresConnectionClosedException` while validating an R2DBC pooled
  connection (`Cannot exchange messages because the connection is closed`).
- A manual retry against the warm instance completed registration and navigated to
  `/dashboard`; no duplicate automatic retry is performed by the client.

The frontend now forces a view refresh for timer, timeout/finalize and error
callbacks. This is required by the Angular production runtime so the slow-server
notice and the re-enabled button are observable even when the request finishes
outside a change-detection turn. The request still has a bounded 120-second
timeout and never retries automatically.

The production R2DBC pool now evicts idle/lifetime-expired connections in the
background, validates remotely, and bounds acquire/create times. This prevents a
managed PostgreSQL provider from handing the registration path a connection that
was closed while idle.

Final public verification after the fix:

- Firebase was rebuilt from frontend commit `d3fe51b` and redeployed successfully
  to `https://manager-4f952.web.app`.
- New registration: dashboard navigation succeeded on the warm public backend.
- Login smoke: the same public session remained usable after registration.
- Error smoke: a backend 500 cleared the loading state and showed only the safe
  public message; the button was enabled again.
- A subsequent registration against the updated backend reached the dashboard
  after the controlled startup notice (approximately 25 seconds), proving that
  the notice is transitional rather than an indefinite loading state.
- Direct public API validation returned `200` for a new identity in 2.88 seconds
  and `409` for the immediate duplicate in 0.59 seconds.
- The duplicate browser flow showed `El usuario ya existe...` and re-enabled the
  `Registrarse` button; no second POST was issued automatically.
- Frontend: 1037 SUCCESS, 0 failures, 2 skipped; encoding guard passed; focused
  registration lifecycle tests (6) passed; development, staging (built to a
  temporary output on C: because D: was full) and production builds passed.
- Backend focused authentication/profile mapping tests passed after the pool
  configuration change. The last complete backend suite before this configuration
  change was 2579 tests, 0 failures, 0 errors, 4 skipped.
- The complete backend suite was rerun after the pool configuration change:
  2579 tests, 0 failures, 0 errors, 4 skipped.
- Backend remediation commit: `93abfb83` (R2DBC production pool resilience).
- Frontend remediation commit: `d3fe51b` (change-detection-safe registration
  lifecycle), deployed to Firebase from the same branch.
- Render Events confirms the latest live service revision is `cf53e893`
  (`Document final public registration smoke`), which contains the backend pool
  remediation; the later root commit `7617fc58` is documentation-only and was
  observed as a subsequent auto-deploy start.
