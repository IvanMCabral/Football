# PB1.2.3G — Frontend dependency security remediation

## Baseline

The pre-remediation production audit reported seven HIGH Angular advisories
with no CRITICAL findings. The complete and production-only JSON reports are
preserved under `docs/deployment/evidence/pb123g_remediation/`.

## Remediation

Angular packages were aligned within the existing major: framework, compiler
and CLI/build tooling are on the 21.2 line; CDK and Material use the latest
compatible 21.2 patch available at remediation time. `zone.js` was updated to
0.16.2. No major upgrade, override, or `npm audit fix --force` was used.

## Result

`npm ci` completed successfully and `npm audit --omit=dev` reports 0 critical,
0 high, 0 moderate, and 0 low vulnerabilities. The full audit still reports
17 development-tool findings (1 low, 9 moderate, 7 high); they do not enter
the production dependency graph. The production-only final JSON is preserved
as `frontend-audit-prod-final.json`.
The exact before/after audit JSON files and package inventory are retained for
independent review.
