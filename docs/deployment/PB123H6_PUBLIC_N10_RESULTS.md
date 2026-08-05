# PB1.2.3H6 — Public warm N=10 results

## Result

The strict H5 N=10 gate was not completed. Four independent career rounds
produced real client traces with one POST and one first SSE each. Six further
new-account attempts failed at world setup after readiness degraded to Redis
`DOWN`. The four rows are retained as raw evidence and are explicitly not
counted as a certification run.

| sample | career | click→live | POST→response | response→first SSE | POSTs | SSE | polling |
|---|---|---:|---:|---:|---:|---:|---:|
| H6-01 | `0bfb2253…` | 3004 ms | 3004 ms | 1888 ms | 1 | 1 | 0 |
| H6-02 | `880cecbb…` | 1002 ms | 1001 ms | 928 ms | 1 | 1 | 0 |
| H6-03 | `06cc9c24…` | 897 ms | 897 ms | 743 ms | 1 | 1 | 0 |
| H6-04 | `bc0342b8…` | 486 ms | 486 ms | 1373 ms | 1 | 1 | 0 |

The complete unsummarized rows are in
`evidence/pb123h6/public-n10-raw.json`. Handler-specific values were left
unset where the currently emitted full trace reported `null`; no value was
derived from the aggregate click-to-live metric.

No percentile is reported: fewer than ten rows and the per-row readiness
precondition were not satisfied. First SSE was observed in 4/4 retained rows;
the required 10/10 remains unverified.
