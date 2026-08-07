# PB1.2.3H7.9 — Redis growth and cleanup

All provider observations were read-only. No `FLUSHDB`, `FLUSHALL`, `KEYS`,
manual delete or cleanup command was executed.

| Point | Storage | DBSIZE | Evidence |
|---|---:|---:|---|
| H7.8 retained baseline | 116 MB / 256 MB | 6150 | previous authenticated Upstash evidence |
| H7.9 before public run | 115 MB / 256 MB | 6227 | Upstash dashboard/CLI read-only observation |
| after public bootstrap and rounds 1–3 | 121 MB / 256 MB | not re-read successfully from CLI | Upstash Usage read-only observation |
| after reset | not executed | not executed | no cleanup permitted in this run |

The account was intentionally not deleted while the certification was in
progress. Growth remains below the free-tier limit, but the required
season-by-season and reset convergence measurements are incomplete. The
cleanup behavior must be certified through the public lifecycle before any
retention conclusion is made.
