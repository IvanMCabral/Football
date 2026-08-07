# PB1.2.3H7.7 — Remediation plan

H7.7 closes the two H7.6 P0s caused by re-capturing the current lifecycle
generation immediately before a callback write. The operational contract remains
single-instance; distributed locking is explicitly out of scope.

## Scope closed

- Persist `lifecycleGeneration` in `RuntimeMatch` and `MatchState` Redis JSON.
- Reuse the generation captured when the runtime/state job was created.
- Reject missing or stale generation before any child write.
- Remove late generation capture from advance, substitution and recovery paths.
- Keep public responses free of lifecycle fencing metadata through response DTOs.
- Require a `CareerWriteContext` for active-career world mutations.

## Validation

The complete backend suite passed after the changes. No gameplay, simulation,
probabilities, fixtures, calendar or datasets were modified. Remote providers
were not accessed.
