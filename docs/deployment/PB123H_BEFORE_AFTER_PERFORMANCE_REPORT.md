# PB1.2.3H — Before/after performance report

## Method

Ten warm authenticated navigations were measured before and after the H changes. Each sample is elapsed time from navigation until the requested page heading was visible. Percentiles use nearest-rank: p50 is the fifth sorted sample and p95 is the tenth for N=10. These are navigation measurements, not fabricated backend request timings.

## Results (milliseconds)

| Surface | Before p50 | Before p95/max | After p50 | After p95/max | Interpretation |
|---|---:|---:|---:|---:|---|
| Dashboard | 502 | 1,618 / 1,618 | 533 | 1,224 / 1,224 | tail improved; median effectively stable |
| Squad | 557 | 1,227 / 1,227 | 530 | 901 / 901 | median and tail improved |
| Matches | 576 | 1,150 / 1,150 | 549 | 954 / 954 | median and tail improved |

Raw samples are in `evidence/pb123h/before-warm-metrics.json` and `after-warm-metrics.json`.

## Measurement limits

The connected browser did not expose request-level resource timing. The following H6 rows are therefore `not measured in this run`: catalog request payload/hit/miss, create career, status, current lineup, fixtures, standings, auto-select, confirm lineup, start-round backend stages, first consultable state as an isolated request, first SSE packet and summary. A cold Render wake-up was not deliberately induced; Render's public Free warning is documented separately from the warm samples.

## Conclusion

The shared catalog cache and start guard correlate with lower warm navigation tails, and the public live route/reload smoke passed. The available evidence supports `COMPLETED WITH ISSUES`, not an unconditional performance pass.
