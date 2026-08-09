# PB1.2.3H7.9D — Lifecycle final smoke

The fresh public lifecycle completed as follows:

- register, world reload, league/team discovery, career start, squad,
  auto-select and confirm returned success;
- one round started with HTTP `200`, SSE returned HTTP `200` and
  `text/event-stream`, and data events were observed through match completion;
- the finished match reached minute 90 with a persisted score and standings;
- the primary reset returned `204`, and the subsequent status was
  `NO_CAREER`;
- C2 was created for the same owner with a different career identity and a new
  round/SSE stream;
- resetting C1 did not change Owner B's career or lineup (`200`, unchanged);
- five additional disposable careers completed reset with `204` (see the
  reset report).

The fresh run therefore passes lifecycle ownership and C1 → reset → C2
convergence. Numeric SSE monotonicity was observed during progression, but a
machine-readable minute sequence was not captured by the browser bridge.

The final health probe was warm and stable: liveness `3/3 200`, readiness
`3/3 200` with database and Redis `UP` (five additional consecutive readiness
checks also returned `200`).
