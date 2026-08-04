# PB1.2.3B - Public season evidence matrix

## Run identity

- Frontend: `https://manager-4f952.web.app`
- Backend: `https://manager-staging-api.onrender.com`
- Team: Valencia CF
- Season shape: four teams, six rounds, two matches per round
- Account: ephemeral audit account; credentials intentionally omitted

## Evidence matrix

| Round | Formation | Duplicate start | SSE | Substitution | State read | User result | Next state |
|---:|---|---|---:|---:|---:|---|---|
| 1 | 4-4-2 | idempotent | 8 events | 200 | 200 | 0-0 | WAITING_USER / round 2 |
| 2 | 4-3-3 | idempotent | 8 events | 200 | 200 | 2-2 | WAITING_USER / round 3 |
| 3 | 4-4-2 | idempotent | 8 events | 200 | 200 | 0-1 | WAITING_USER / round 4 |
| 4 | 4-3-3 | idempotent | 9 events | 200 | 200 | 0-0 | WAITING_USER / round 5 |
| 5 | 4-4-2 | idempotent | 9 events | 200 | 200 | 0-1 | WAITING_USER / round 6 |
| 6 | 4-3-3 | idempotent | 9 events | 200 | 200 | 0-0 | FINISHED |

## Post-season checks

- Career status after re-login: `FINISHED`, round 6 of 6.
- Valencia CF: 10 points, 2 wins, 4 draws, 0 losses, 4 GF, 2 GC, second place.
- Standings: four clubs returned.
- Fixtures, squad, world status, user stats, detailed match, events, ratings, and
  statistics were all readable after completion.
- Terminal match-state read returned HTTP 200 with minute 90 and `FINISHED`.
- CORS accepted the deployed frontend origin and returned credentials support.

## Evidence limits

The public run proves API/state consistency and recovery through refresh and login.
It does not certify instance-restart durability or a real Chrome screenshot review:
the local Computer Use bridge failed before tab enumeration with OS error 3. A
previous browser observation recorded a real `_dragRef` marker-drag error that remains
open for UI follow-up; MetaMask warnings were extension-generated noise.

