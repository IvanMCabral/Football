# Dependency Baseline

Date: 2026-07-31

This document records the MVP 1 dependency baseline only. No versions were updated during the freeze.

## Runtime/tooling

| Area | Baseline |
| --- | --- |
| Java | OpenJDK `21.0.8` |
| Maven | Apache Maven `3.9.11` |
| Spring Boot | `3.2.1` |
| Node.js | `v24.9.0` |
| npm | `11.6.0` |
| Angular core | `^21.1.1` |
| Angular CLI | `^21.0.2` |
| PostgreSQL | Main database: `football_manager` |
| Redis | Authenticated local Redis runtime |

## Validation

- Backend compile and suite passed with Java/Maven baseline above.
- Frontend dev/prod builds and tests passed with Node/npm baseline above.
- PostgreSQL and Redis are part of the validated local runtime stack.
