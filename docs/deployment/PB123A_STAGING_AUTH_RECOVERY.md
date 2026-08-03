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

The public deployment must be refreshed from this validated artifact before accepting browser registration traffic.
