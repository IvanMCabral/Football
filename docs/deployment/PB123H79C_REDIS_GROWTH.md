# PB1.2.3H7.9C — Redis growth and round-trip evidence

## Read-only baseline retained from H7.9B

| Point | Storage | DBSIZE | State |
|---|---:|---:|---|
| R0 H7.9 | 115 MB / 256 MB | 6227 | observed in Upstash dashboard |
| R1 season 1 | not available | not available | not measured |
| R2 season 2 | not available | not available | not measured |
| R3 reset | not available | not available | not measured |

The H7.9C public smoke created one disposable career and retried its reset
once; the reset then returned `204`. No provider-side deletion, FLUSHDB,
FLUSHALL, key scan or manual Redis mutation was performed.

## Round-trip change

The previous existing-career save validated generation and mapping with
separate Redis requests, rotated a mapping token, wrote the root and refreshed
the index in further requests. H7.9C keeps the same fencing semantics in one
atomic script. This is a bounded reduction in network round trips, not a claim
that Redis storage growth is solved.

Redis R1/R2/R3 provider readings remain unmeasured and must not be inferred
from DBSIZE or application responses.
