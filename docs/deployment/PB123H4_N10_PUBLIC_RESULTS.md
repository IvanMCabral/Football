# PB1.2.3H4 - Public warm results

## Release identity

- frontend commit: `51cd2a5` (`perf: make dashboard round start authoritative`)
- hosting URL: `https://manager-4f952.web.app`
- backend URL: `https://manager-staging-api.onrender.com`
- public index SHA-256: `7985B87C61E0D5E4AC6657D632CDBEC0D2CC6F3E257C4FC4EA4EEC1BB7376422`
- public `main-YHN3NU74.js` SHA-256: `85311273FB83922487E7DB662A91597892A9F0324730372BF946034243BEB08F`
- public `polyfills-RV3JTMEC.js` SHA-256: `ADA640BCE793942B50BD47A7B63D72F69C8964FBC785E25043EEDB19DA4A32D`
- public `styles-MSEW3VNW.css` SHA-256: `B1E13137A63B0C3F7F32D87A272CF9BE053A48F9094698D202D0B75A08ECD1F7`

All four public hashes matched the local production artifact.

## Warm sample captured

This is one observed warm row, not an N=10 certification:

| sample | round | click->POST | POST->response | response->live | live->first SSE | click->live | click->first SSE | statusWait | fixturesWait | start POSTs | SSE | polling | HTTP errors |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | `ab74147c-827d-413d-af62-72ff0bb29b69` | 1312 ms | 670 ms | 28 ms | 100 ms | 2010 ms | 2313 ms | 0 ms | 0 ms | 1 | 1 | 0 | 0 |

Trace metadata: `statusSnapshotAvailableAtClick=true`, `statusSnapshotAgeMs=0`, `statusHttpTriggeredByClick=false`, `fixtureSnapshotAvailableAtClick=true`, `startPayloadReadyMs=0`.

## N=10 gate

Required warm sample count: 10. Observed final-release count: 1. The run is therefore rejected for certification. No cold-start sample was mixed into the row above.

## Public availability during the run

Liveness returned 200. Readiness transiently returned 503 while Redis or the database was reconnecting and later returned 200 with both dependencies UP. This is recorded separately from the warm UI row and is not counted as a warm match-start sample.

## Baseline comparison

H3 had three click-to-live observations: 1483, 1706 and 1811 ms, with status waits of 1364, 1635 and 1759 ms. H4 removes that explicit status wait; the single final-release trace reports zero status wait.

