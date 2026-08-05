# PB1.2.3H5 — Click marker semantics

## Scope

This document records the timing instrumentation deployed in frontend commit
`267053e` and published at `https://manager-4f952.web.app`. It does not change
gameplay, simulation, probabilities, persistence or match rules.

## Why H4 reported 1312 ms

H4 had one public row with `click→POST = 1312 ms`, but its start marker was
created before the physical browser click could be observed. That interval
therefore mixed the trace origin with browser/automation dispatch and could not
be presented as Angular handler work. H4 also proved `statusWaitMs=0`,
`fixturesWaitMs=0`, one POST and one SSE. The H5 instrumentation separates the
physical pointer dispatch from the actual handler and subscription boundaries.

## Marker contract

| Marker | Location | Real event | Before/after | May include bridge delay |
|---|---|---|---|---|
| `POINTER_EVENT_RECEIVED` | dashboard/game-detail pointer handler | browser `pointerdown` received | before Angular click handler | yes, before marker only |
| `CLICK_HANDLER_ENTER` | dashboard/game-detail start method | Angular click handler entered | first handler marker | no |
| `START_GUARD_COMPLETE` | start method | duplicate-click guard and synchronous preconditions complete | after guard | no |
| `PAYLOAD_BUILD_START` | start method | payload preparation begins | after guard | no |
| `PAYLOAD_BUILD_END` | start method | payload/snapshot preparation ends | before service call | no |
| `HTTP_OBSERVABLE_CREATED` | start method | HttpClient observable exists | before subscription | no |
| `HTTP_SUBSCRIBE_START` | start method | RxJS subscription is invoked | canonical POST boundary | no |
| `FETCH_XHR_DISPATCH` | start method | dispatch marker immediately before subscribe call | dispatch boundary | no |
| `POST_RESPONSE` | start method | HTTP success response received | after network response | no |
| `NAVIGATION_REQUESTED` | start method | router navigation requested | after response | no |
| `ROUTE_ACTIVATION` | round-live bootstrap | live route component initialization | after navigation | no |
| `LIVE_COMPONENT_CREATED` | round-live bootstrap | live component bootstrap begins | after route activation | no |
| `LIVE_RENDERED` | round-live bootstrap | first scheduled live render marker | after VM state is ready | no |
| `SSE_CONNECT_REQUESTED` | match engine stream | stream response opened | before first parsed event | no |
| `FIRST_SSE_PARSED` | match engine stream | first `data:` JSON event parsed | after SSE payload parse | no |

All timestamps use the same monotonic `performance.now()` fallback used by the
trace. `CLICK_HANDLER_ENTER → HTTP_SUBSCRIBE_START` is the canonical handler
metric. `POINTER_EVENT_RECEIVED → CLICK_HANDLER_ENTER` is reported separately
as browser/tooling dispatch and is never silently added to application time.

## Handler inventory

The dashboard and game-detail handlers perform only the guard, synchronous
snapshot/payload preparation, observable creation and subscription before the
POST. No status wait, fixture wait, lineup wait, router wait, timer, debounce,
animation completion or nested subscription precedes `HTTP_SUBSCRIBE_START`.

## Local controlled check

The focused Angular test executes 20 synchronous marker sequences and asserts
20 samples with a maximum handler-to-subscribe duration of 100 ms. This is an
instrumentation-level regression check, not a substitute for public browser
samples; the test does not claim a public percentile.
