# PB1.2.3H5 — Before/after

## Before (H4)

H4 had one final-release public trace: `click→POST 1312 ms`,
`POST→response 670 ms`, `response→live 28 ms`, `live→first SSE 100 ms`,
`click→live 2010 ms` and `click→first SSE 2313 ms`. It also recorded zero
status wait, zero fixture wait, one POST, one SSE, zero polling and zero HTTP
errors. The H4 verdict was `REJECTED` because one row could not satisfy N=10 or
separate browser dispatch from handler work.

## After (H5 release)

The frontend now emits distinct pointer, handler, guard, payload, observable,
subscription, dispatch, response, navigation, route, render and SSE parse
markers. The canonical application metric is handler-enter to subscription;
physical pointer-to-handler delay is separate. Local instrumentation has a
20-sequence regression test with a 100 ms maximum assertion.

The exact release was built, pushed as commit `267053e`, deployed to Firebase,
and verified byte-for-byte against the local principal assets. The public
attempt could not create a world: normal UI initialization returned “El recurso
solicitado no existe”. Consequently there are zero valid public match-start
rows after the change, so no latency improvement or regression is claimed.
