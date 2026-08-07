# MANAGER — PB1.2.3H7.9 definitive public certification

**Verdict: REJECTED — certification incomplete**

This evidence is sanitized and covers only the public observations actually
performed. It does not replace the required two-season certification.

## Runtime identity

- Runtime source HEAD: `b6c413f3f8e0a6037b2f0362c6cc0a7893bf3af4`
- Current root HEAD (documentation-only commits): `bda1898f`
- Frontend deployed revision: `8f36ca7`
- Render live SHA: `b6c413f3f8e0a6037b2f0362c6cc0a7893bf3af4` (`EXACT_HEAD`)
- Render service: `manager-staging-api`, Free, one instance (`SINGLE_INSTANCE`)
- Firebase: `https://manager-4f952.web.app`
- Backend: `https://manager-staging-api.onrender.com`

## Public functional evidence

- Bootstrap register/login/dashboard/world/career/squad/lineup: PASS.
- Pre-match editor: PASS; 4-4-2 and 4-3-3 were inspected, including 11/11,
  chemistry/rating changes and pixel controls.
- Round 1: FINISHED at minute 90, 3–1, non-empty timeline.
- Round 2: FINISHED at minute 90, 0–0, non-empty timeline.
- Round 3: FINISHED at minute 90, 0–0, with a non-empty timeline. The live
  formation modal paused the round at a live minute and the close path resumed
  it before completion.
- Round 4: FINISHED at minute 90, Levante UD 1–0 Real Madrid, non-empty
  timeline. A live reload recovered the same round and it completed after the
  user-facing resume control.
- Console errors from the app: none observed after deployment. Extension-only
  warnings were excluded.

## Defects found and fixed

1. Fresh-world empty-team loading state: fixed in frontend commit `ab64ae7`,
   with focused and full tests, then deployed.
2. Stale injury modal could open after match completion: fixed in frontend
   commit `bc6703a`, with focused tests, then deployed.
3. Subsequent rounds reused the career UUID as the RoundEngine key and stayed
   at `Por Iniciar`: fixed in frontend commit `8f36ca7`, with focused tests,
   deployed and publicly retested through rounds 2 and 3.

No gameplay, probabilities, RNG, fixtures, datasets, database rows or Redis
values were modified.

## Mandatory gates not certified

- full season 1 and standings recomputation;
- season 2 transition and full season 2;
- season 3 stress transition;
- C1/C2 stale callback and owner-B drill;
- full responsive matrix;
- performance N=10 and complete cold/warm split;
- reset cleanup convergence and post-reset Redis measurement;
- full controls/routes inventory and external-tester run.

Because these required gates remain unexecuted, this report cannot be
`APPROVED` or `APPROVED WITH ISSUES`. See the linked evidence files for exact
scope and observations.

## Local regression validation

- Backend: `mvn -q -DskipTests test-compile` PASS; `mvn -q test` PASS — 2632
  tests, 0 failures, 0 errors, 4 skipped.
- Frontend: development build PASS; production build PASS; `npm test
  -- --watch=false --browsers=ChromeHeadless` PASS — 1069 SUCCESS, 0 failures,
  2 skipped; encoding guard PASS (396 files).
- Production artifact inspection: no `test-harness`/`Test Harness` route or
  identifier found in the production JavaScript, HTML or CSS output.
- `npm audit --omit=dev`: 0 production vulnerabilities (0 low, 0 moderate,
  0 high, 0 critical).

## Gate classification

- P0: 0 observed in the executed scope.
- P1: 0 observed in the executed scope after the three frontend remediations;
  the certification remains rejected because mandatory multi-season,
  lifecycle, responsive and N=10 evidence is still missing, not because a
  new P0/P1 was introduced.
- P2/P3: certification evidence gaps listed above.
