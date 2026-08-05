# PB1.2.3H3 before and after

Percentiles use nearest rank. All values are milliseconds.

| Metric | Before H2 warm N=10 | H3 observed set | Acceptance status |
|---|---:|---:|---|
| Click -> POST sent | Not instrumented | 1,234-1,989 in traces | Not enough N=10 |
| POST -> response | 302 p50 / 1,007 p95 | 207-249 observed | Partial |
| Response -> live | Included in external path | 232-1,093 component path | Partial |
| Live -> first SSE | 305 p50 / 515 p95 | Not emitted in captured traces | Missing |
| Click -> live | 4,050 p50 / 5,673 p95 | 1,706 p50 / 1,811 p95 (N=3) | Partial |

Raw values are in `evidence/pb123h3/`. The external baseline is preserved from
H2 and is not mixed with the H3 partial set.

The reduction is significant and the public path has no observed duplicate
start, duplicate stream, polling loop or transient 404/500. A final N=10 warm
run with first-SSE timings is still required before an unconditional pass.
