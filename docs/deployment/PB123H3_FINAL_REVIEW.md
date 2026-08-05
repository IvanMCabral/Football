# PB1.2.3H3 final review

## Verdict: REJECTED

The remediation materially reduced the measured public click-to-live path from
4,050 ms p50 / 5,673 ms p95 to 1,706 ms p50 / 1,811 ms p95 in the three real
H3 observations. However, the requested final evidence gate requires N=10
warm samples on the exact deployed release (`b54b748`), isolated click-to-POST and
click-to-first-SSE timings, and complete per-click request inventory. Only
three after observations were available, and the connected browser did not
expose resource timing or payload-size data.

## Closed in code

- status and fixture reads are shared and short-lived;
- squad and game-detail status requests warm before the click;
- modal subscriber changes do not discard the completed status snapshot;
- start attempts reset stale traces;
- live route uses navigation state and does not reread confirmed lineup;
- duplicate POST/SSE and polling were not observed;
- T16 first-SSE instrumentation is present in the final release.

## Remaining gates

- repeat the public warm run N=10 on `b54b748`;
- record raw click-to-POST and click-to-first-SSE samples;
- capture a complete request inventory per click;
- separate a cold sample from warm percentiles.

The remaining item is evidence completeness, not a claimed gameplay or backend
defect. The result is therefore REJECTED under the H3 acceptance rules and is
not presented as an unconditional performance approval.
