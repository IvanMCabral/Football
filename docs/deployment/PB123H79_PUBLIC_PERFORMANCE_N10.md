# PB1.2.3H7.9 — Public performance evidence

## Captured warm samples

The public browser emitted the existing match-start trace. These are individual
observations, not an N=10 certification:

| Observation | click→live | POST→response | response→first SSE |
|---|---:|---:|---:|
| round 1 first start | 1,806 ms | 1,806 ms | 1,651 ms |
| round 1 reload/start recovery | 816 ms | 816 ms | 462 ms |
| round 2 start | 418 ms | 418 ms | 700 ms |

The warm observations show the normal path within the stated click→live and
first-SSE targets, but they do not replace the required N=10 raw dataset.

## Cold start

One readiness request returned HTTP 503 while the Render Free instance was
waking. The following three warm readiness samples returned HTTP 200 with
`{"status":"UP","database":"UP","redis":"UP"}`. This is classified as
`FREE_TIER_COLD_START`, not as a gameplay or data-integrity failure.

## Certification status

`N=10 NOT CERTIFIED`. No percentile was invented from the three available
observations. The remaining required surfaces (dashboard, catalog, squad,
lineup, fixtures, standings, summary and next-round transition) require a
dedicated complete run before performance can be approved.
