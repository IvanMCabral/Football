# PB1.2.3H6 — Final review

## Verdict: `REJECTED`

The original production world-initialization 404 was corrected in frontend
revision `0830966` and the corrected public flow passed three independent
new-account smokes. Four real match-start/SSE traces were also captured.

The release cannot be approved because the public backend later reported
readiness `503` with PostgreSQL `UP` and Redis `DOWN`. Authenticated world
reloads returned 500, six additional account setups failed, and the mandatory
warm N=10 with ten readiness-qualified rows was not obtained.

## Gate summary

| Gate | Result |
|---|---|
| Historical 404 contract | PASS — production uses the protected dashboard route |
| New-account smoke | PASS — 3/3 before Redis outage |
| Redis readiness | FAIL — 503 / Redis DOWN |
| Free-tier storage | FAIL — provider dashboard reports 257 MB / 256 MB |
| Warm N=10 | FAIL — 4 observed rows, 0 strict certification rows |
| First SSE | 4/4 observed; 10/10 required |
| Duplicate POST/SSE | 0/0 in retained rows |
| Polling | 0 in retained rows |
| Release alignment | PASS — frontend `0830966`, public hashes recorded |
| Backend suite | test-compile passed; full suite blocked by local `initdb` test runtime |

The historical H5 rejection remains unchanged. Full evidence is under
`docs/deployment/evidence/pb123h6/`.

The storage-limit finding supersedes earlier hypotheses about credentials,
TLS or Render configuration. Those changes were not made. Key-level memory
inspection and cleanup remain pending because the provider connection details
are not available in this workspace; no data was deleted.
