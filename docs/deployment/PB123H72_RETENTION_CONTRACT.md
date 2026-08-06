# PB1.2.3H7.2 — Retention contract

| Family | Default | Role | Contract |
|---|---:|---|---|
| Career root | 30 days | durable working state | inactivity cache; reset is canonical |
| Owner index | 31 days | cleanup discovery | outlives 30-day children |
| Career owner mapping | 31 days | ownership proof | immutable per career ID; outlives children |
| World snapshot | 30 days | reconstructible | rebuilt from the canonical local database source |
| Match detail | 30 days | bounded history | available during the career retention window |
| Match baseline | 7 days | replay/debug support | not the durable historical detail contract |

The two 31-day ownership structures prevent them expiring before the
30-day world/detail families they discover. Values are configurable through
`REDIS_WORLD_TTL` and `REDIS_MATCH_DETAIL_TTL`; production startup rejects
missing/invalid/out-of-range values (world below one hour, detail below one
day, or either above 90 days).

Match detail is intentionally bounded staging history, not indefinite archive.
The UI/API must treat data older than the retention contract as expired rather
than silently claiming it can be reconstructed from the basic match result.
