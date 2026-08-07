# PB1.2.3H7.7 — Persistence failure contract

Required lifecycle state (career root, runtime, match state, commands and
ownership metadata) propagates errors and never reports a successful write when
Redis rejected it. `LeagueTeamCommandService` also propagates a failure from
the context-aware world writer.

```
SILENT_REQUIRED_PERSISTENCE_FAILURE_PATHS_REMAINING = 0
```

Detailed-match and baseline artifacts in the test-harness/replay and batch
telemetry paths remain explicitly `OPTIONAL_TELEMETRY`: a failure is logged and
does not alter the already-computed fixture result. They are not used as the
source of runtime state or lifecycle ownership. This classification preserves
the existing replay contract while keeping required state fail-closed.

No fire-and-forget publisher was introduced. The remaining bounded batch
handling is outside request-path WebFlux and is documented as optional detail
telemetry rather than required lifecycle persistence.
