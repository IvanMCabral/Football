# PB1.2.3H7.9E — Before/after comparison

| Measurement | Before instrumentation | After instrumentation |
| --- | ---: | ---: |
| Public sample | N=10 | Formal N=20 not executed |
| HTTP 200 | 6/10 | Not measured (readiness Redis DOWN) |
| HTTP 500 | 4/10 | Not a formal gate; partial diagnostics only |
| Client p50 | 11,143 ms | Not measured |
| Client p95/max | 28,096 ms | Not measured |
| Server-stage attribution | unavailable | headers available on observed responses |

The “after” column is intentionally not presented as an improvement claim.
Partial post-instrumentation samples are retained separately and were captured
during deploy/restart transitions; they cannot replace the required healthy
provider N=20.

The current acceptance attempt stopped at the health gate: liveness was 5/5
HTTP 200, while three readiness probes returned HTTP 503 with database UP and
redis DOWN. Read-only Upstash inspection then confirmed storage at 256 MB / 256
MB and DBSIZE 9,638. No public accounts were created and no reload-world
benchmark was started.
