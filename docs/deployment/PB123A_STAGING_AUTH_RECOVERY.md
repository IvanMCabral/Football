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
- Frontend suite passes with 1031 successes and 2 skipped tests.
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
