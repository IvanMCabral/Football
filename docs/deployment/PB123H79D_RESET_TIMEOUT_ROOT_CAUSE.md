# PB1.2.3H7.9D — Reset timeout root cause

Five fresh disposable accounts completed the complete public setup flow and
each reset returned HTTP `204`:

| Sample | Duration |
|---:|---:|
| 1 | 8160 ms |
| 2 | 8185 ms |
| 3 | 8207 ms |
| 4 | 8121 ms |
| 5 | 8143 ms |

Median is `8160 ms`, p95 is `8207 ms`, and maximum is `8207 ms`. No sample
returned `503`; the result is stable but fails the H7.9D target of p50 <=
1500 ms and p95 <= 4000 ms. The public API does not expose the internal stage
metrics, so this run does not invent stage attribution. The issue remains a
P1 operational performance finding for the existing cleanup path, not a
correctness failure. No timeout was increased and no code was changed.
