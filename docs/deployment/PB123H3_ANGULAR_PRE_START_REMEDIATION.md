# PB1.2.3H3 remediation

Implemented on the frontend only:

- short-lived, keyed replay for round fixtures;
- short-lived replay for career status with command invalidation;
- eager status warm-up on squad and game-detail screens;
- completed squad status replay survives formation-modal subscriber changes;
- navigation state carries the already validated career snapshot;
- live bootstrap does not reread lineup before the first start;
- one guarded round POST and one shared SSE per round;
- trace reset on a new user attempt;
- complete T0-T16 trace, including first SSE.

The backend remains authoritative for career, lineup and round validation. No
permanent mutable cache was introduced. No gameplay or simulation code changed.

## Validation

Frontend tests: 1,058 executed, 0 failures, 2 skipped. Development and
production builds passed. Production artifact inspection found 57 files, zero
source maps and zero test-harness references. `npm audit --omit=dev` reported
zero critical, high, moderate and low vulnerabilities.

The public evidence set contains only three after observations, therefore the
H3 N=10 acceptance gate is intentionally not claimed as complete.
