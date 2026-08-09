# PB1.2.3H7.9D — Reset timeout root cause

The H7.9C observation remains: the first disposable reset returned controlled
`503 CAREER_CLEANUP_TIMEOUT`, and an immediate retry returned `204`.

H7.9D N=5 instrumentation was not executed because Gate 1 requires exact
Render identity before creating a new account or exercising remote lifecycle
operations. No timeout was hidden, extended or reclassified as fixed.

The existing stage metrics remain available in
`application.observability.RuntimeOperationMetrics`; no code change was made
in H7.9D.
