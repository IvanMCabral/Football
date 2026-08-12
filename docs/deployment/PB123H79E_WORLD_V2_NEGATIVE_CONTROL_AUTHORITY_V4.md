# World V2 negative-control authority V4

V4 separates execution mode from the invariant itself and refuses inflated
physicality claims.

| Mode | Count |
|---|---:|
| REDIS_PHYSICAL | 26 |
| JVM_PROCESS_PHYSICAL | 0 |
| BUILD_DISCOVERY_PHYSICAL | 0 |
| PRE_WRITE_GUARD | 1 |
| SOURCE_PROVEN | 6 |
| Unique controls | 33 |
| Duplicate invariants | 0 |
| Material proxies | 0 |
| False mode labels | 0 |
| False passes | 0 |

The physical mode requires an ephemeral Redis instance, persisted malformed
state, pre/post checksum evidence, a fresh product entry point and an observed
product rejection. `MISSING_TTL` remains a truthful `PRE_WRITE_GUARD`: the
repository rejects a zero TTL before writing Redis.

The self-destruction tests reject fake physical evidence, comparator-only
mutations, missing product signals, duplicate invariants, missing mode and
missing evidence.
