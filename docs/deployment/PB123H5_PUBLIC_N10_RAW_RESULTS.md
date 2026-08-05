# PB1.2.3H5 — Public N=10 raw results

## Release under test

- Frontend commit: `267053e`
- Firebase URL: `https://manager-4f952.web.app`
- Backend URL: `https://manager-staging-api.onrender.com`
- Production artifact: 55 files, 0 source maps, no test-harness references
- Public and local SHA-256 hashes matched for `index.html`, `main-JRKOTGLN.js`,
  `polyfills-RV3JTMEC.js` and `styles-MSEW3VNW.css`.

## Warm-up evidence

The backend returned liveness `200` and readiness `200` with database and Redis
`UP` before the browser run. A fresh public account was created through the
registration UI. The browser then opened `/career/setup` and retried the normal
world-load flow.

## Raw sample table

No valid N=10 start sample exists. The run stopped before a league, career,
lineup or round could be created because the public UI reported the backend
response `Error al inicializar el mundo: El recurso solicitado no existe.`

| sampleId | release | classification | stage reached | handler→POST | POST→response | handler→live | handler→first SSE | statusWait | fixturesWait | POSTs | SSE | polling | HTTP errors | final state |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| none | `267053e` | warm setup attempt | world initialization | n/a | n/a | n/a | n/a | n/a | n/a | 0 | 0 | 0 | 1 setup failure | rejected before career |

The row is intentionally not counted as a match-start sample. No timing value,
round identity or SSE event is inferred from it.

## Percentiles

Nearest-rank percentiles are not calculated because there are zero valid match
start rows. N=10, T16 10/10, and all latency gates remain unverified.

## Functional coverage

Registration reached the authenticated dashboard. Career creation, lineup,
round start, first SSE, minute progression, finished match, reload recovery and
summary were not reachable in this run. No duplicate round or synthetic result
was created.
