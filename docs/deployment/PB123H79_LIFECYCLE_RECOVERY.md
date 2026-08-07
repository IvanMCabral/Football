# PB1.2.3H7.9 — Lifecycle and recovery

## Defect reproduced

After season 1 round 1 completed, starting round 2 through the public UI reused
the career UUID as the `RoundEngine` registry key. The backend returned the
completed engine and the UI kept the new fixtures at `0' / Por Iniciar`.

## Root cause and remediation

The frontend now creates one browser-persisted UUID per career/round, sends it
as the round-engine identity, reuses it across a reload, and removes it when
the round finishes. The career UUID remains the owner/career path value for
pause, resume and ownership operations. This prevents completed round engines
from colliding with later rounds without changing simulation or probabilities.

The injury modal exposed a second race: when a match finished before the pause
response arrived, an editable dialog could open with an old minute. The modal
service now fails closed on pause failure/timeout, rejects an `alreadyFinished`
pause response and rejects terminal live snapshots. Focused tests cover both
paths.

## Public retest

- Round 2 started at `/round/2/live` after deployment and advanced to minute 17,
  then finished at minute 90.
- Round 3 started at `/round/3/live`, advanced to minute 31 while the modal
  was exercised, and finished at minute 90 with a non-empty timeline.
- Round 4 was reloaded while live; the route recovered the same round and
  resumed to minute 90, finishing 1–0 with a non-empty timeline.
- A live formation modal paused the whole round and resumed after close.
- The stale injury dialog was closed after the original reproduction; a new
  terminal dialog was not opened.

## Remaining gates

The C1/C2 stale-callback drill, owner-B isolation, full recovery matrix and
two complete seasons were not re-certified in this run. The single round-4
reload/recovery path passed; no public approval is claimed for the remaining
gates.
