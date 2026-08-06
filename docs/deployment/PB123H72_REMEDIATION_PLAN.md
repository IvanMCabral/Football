# PB1.2.3H7.2 — Remediation baseline and scope

## Baseline

- Branch: `feat/v25d99.20.3.1-runtime-fixes`
- Initial HEAD: `afddac28`
- Frontend: unchanged
- Remote Redis/PostgreSQL/Render/Upstash: not accessed or modified
- Historical H7.1 audit: preserved unchanged

The entry audit rejected H7.1 because the owner career Set was treated as
proof of ownership, short UNLINK counts were not classified, the index had no
bounded cardinality, and no adapter test used a real ephemeral Redis.

## H7.2 scope

This remediation adds an immutable Redis mapping
`career-owner:{careerId} -> owner UUID`, validates every career ID before any
career-scoped SCAN, bounds the owner index at 256 entries, and keeps the
mapping/index retention longer than the 30-day reconstructible data families.
It also classifies cleanup states, verifies short counts with bounded EXISTS
checks, adds real Redis integration coverage, validates retention settings in
prod, makes `jdk.random` explicit in Surefire, and expands owner-concurrency
tests.

No gameplay, simulation, fixtures, datasets, frontend, remote data or deploy
configuration is changed.
