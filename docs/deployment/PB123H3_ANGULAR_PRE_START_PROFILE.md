# PB1.2.3H3 Angular pre-start profile

## Scope

This report profiles the public Angular path from the user action to the live
route. Gameplay, simulation, fixtures and backend contracts were not changed.
The final frontend revision is `d1bdedc` on branch
`feat/v25d99.20.3.1-runtime-fixes`; Firebase Hosting was deployed from that
revision at `https://manager-4f952.web.app`.

## Baseline

The H2 warm run measured ten public starts with external click-to-live p50
4,050 ms and p95 5,673 ms. POST-to-response was 302/1,007 ms p50/p95 and
response-to-first-SSE was 305/515 ms. Raw data is in
`evidence/pb123h3/before-warm-metrics.json` and the source H2 report.

## H3 observations

Three public observations were obtained while the frontend remediation was
being promoted: click-to-live 1,483 ms, 1,706 ms and 1,811 ms. Their median is
1,706 ms and nearest-rank p95 is 1,811 ms. These samples are real, but they are
not a qualifying N=10 set for the final release. The final `d1bdedc` change
adds the missing T16 first-SSE marker only; it does not change the request
path, so the three behavior samples remain representative but are not relabeled
as N=10.

## Stage interpretation

The trace separates status, fixtures, lineup, POST, route activation, render
and stream stages. The observed residual wait before POST is dominated by the
career-status subscription when the screen has not completed its eager warm
request. H3 now starts that request at screen creation, keeps the completed
snapshot while the modal is open, shares a five-second service snapshot, and
invalidates it after round/season commands.

The trace lifecycle was also corrected: abandoned attempts reset at the next
explicit click, T3 is marked before the status read, and T16 is marked on the
first parsed SSE event.

## Request inventory

The logical inventory and limitations are recorded in
`evidence/pb123h3/request-inventory.json`. The connected browser did not expose
resource timing or payload sizes, so those values are explicitly not claimed.
