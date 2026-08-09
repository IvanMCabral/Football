# PB1.2.3H7.9D — Runtime identity

## Gate result

**PASS — RUNTIME_EQUIVALENT**

Render was inspected read-only through the existing authenticated Chrome
profile. The service is `manager-staging-api`, on branch
`feat/v25d99.20.3.1-runtime-fixes`, Free plan, live status `live`, with
autoscaling disabled and manual scaling set to one instance.

The live deployment exposes commit
`8c6fdf24b1fa77a960b685f4f626d5cdd0335c8e`. The productive runtime commit is
`8d9e91ed`; the intervening root commits contain documentation/evidence only.
The deployment is therefore classified `RUNTIME_EQUIVALENT`, not an exact
runtime-head match. Render did not expose a region or deployment timestamp in
the inspected surface.

The single-instance contract is **PASS**: autoscaling is off and manual
instances is `1`. No provider settings, plans, credentials or billing were
changed.
