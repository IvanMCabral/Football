# PB1.2.3H7.9D — Redis growth final

Upstash Manager was inspected read-only. The database is `Manager`, Free Tier,
AWS `sa-east-1`, with the visible quota `256 MB`.

| Checkpoint | Storage shown | DBSIZE | Interpretation |
|---|---:|---:|---|
| R0, before the fresh smoke | 124 MB | 6380 | baseline |
| R1, after world/career/lineup | 124 MB | 6388 | +8 keys |
| R2, after one finished round and SSE | 127 MB | 6391 | +3 keys / +11 from R0 |
| R3, after the primary reset | 127 MB | 6381 | -10 from R2 / +1 from R0 |

R0–R3 were read-only provider observations; no global scan, mutation,
deletion, plan change or billing operation was performed. The later N=5 reset
sample also returned 204 for every account. Provider usage is subject to
display lag, so DBSIZE is the immediate signal used for the lifecycle gate.
