# PB1.2.3H7.9F.2 — Public VALIDATE_ONLY provider accounting

## Read-only provider gate

The existing Upstash Management API credential authenticated successfully:

- database list: HTTP 200;
- exact database stats: HTTP 200;
- database: `Manager`;
- quota authority: 268,435,456 bytes.

The admission sample was taken immediately before the only real runner launch.

| Metric | Bytes |
|---|---:|
| T0 `current_storage` | 264,967,931 |
| Quota | 268,435,456 |
| Headroom | 3,467,525 |
| Required headroom | 2,436,344 |
| Retained cushion | 262,144 |
| Remaining certified cushion | 769,037 |
| Maximum admitted `current_storage` | 265,736,968 |

Capacity admission was `PASS` with a narrow but positive provider margin.

## T1 and Redis accounting

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| Upstash `current_storage` | 264,967,931 | 264,967,931 | 0 |
| Redis `DBSIZE` | 9,555 | 9,555 | 0 |
| Owner memory usage | 2,483,493 | not re-measured | n/a |
| Owner PTTL | -1 | -1 | 0 |
| Catalog exists | 0 | 0 | 0 |

`PING` returned `PONG`. All Redis commands used by the evidence collection were
exact-key or database-size reads; no `SCAN`, write, delete, migration, cleanup
or broad namespace read was issued.

The provider margin remains subject to normal Free-tier accounting drift. That
uncertainty did not cause this failure: T0 passed and the runner failed before
calling its capacity provider because the canonical PostgreSQL reconstruction
was unavailable in the dedicated local process.
