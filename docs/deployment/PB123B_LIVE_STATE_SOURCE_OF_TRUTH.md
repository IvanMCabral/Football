# PB1.2.3B - Live state source of truth

## Canonical sources

| State | Canonical source | Recovery behavior |
|---|---|---|
| Career status, fixtures, standings, squad | Persisted `CareerSave` through the career repository | Recovered after login/refresh from persisted career data |
| Active round and active match | Registered `RoundEngine` and its latest state | Available while the process owns the live session |
| SSE replay/latest snapshot | The same round engine latest state | Replays the current authoritative snapshot; it does not invent a score |
| Completed match state | Persisted match/session state and detailed-match data | `/api/v1/match-engine/{matchId}/state` returns the terminal snapshot after engine cleanup |
| Compare/baseline data | Redis `BaselineState` | Compare-only; never a replacement for live state |
| LiveSession internals | Process memory | Not durable across an instance restart; a restart drill is still required |

## Invariants

1. `minute` is monotonic within a match and is capped at the terminal minute.
2. Home and away scores are monotonic and the terminal score is retained after
   cleanup.
3. The public DTO does not currently expose a dedicated sequence number; the
   observed monotonic key is the ordered minute/event stream. Adding an explicit
   sequence is a PB1.2 follow-up rather than inferring one from a stale DTO.
4. An active engine is the first read source; persistence is the fallback only after
   the engine has stopped.
5. Missing or unauthorized matches return a controlled error. They are never rendered
   as a fabricated `0-0`.
6. Round cleanup unregisters the actual round identifier resolved from match results,
   not a user identifier.
7. Repeated start calls are idempotent and do not create duplicate simulations.

## Incident prevention

The former frontend/backend contract mismatch (missing match-state route) and the
round cleanup identifier mismatch are both covered by focused tests. The public
full-season run then read every round through the state endpoint and read the final
state after completion, including minute 90, final status, score, and events.

## Durability boundary

Career and detailed-match persistence support refresh and re-login recovery. A
process-level restart can still lose an in-memory `LiveSession` before its terminal
state is persisted; this is an explicit PB1.2 follow-up, not silently treated as a
cache miss. Redis remains part of the runtime durability path and must retain its
managed-provider backup/restore drill before stronger recovery guarantees are made.
