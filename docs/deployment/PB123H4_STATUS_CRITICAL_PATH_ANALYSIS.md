# PB1.2.3H4 - Status critical path analysis

## Scope

Release audited: frontend commit `51cd2a5` on branch `feat/v25d99.20.3.1-runtime-fixes`.
The backend remains authoritative for career phase, round, ownership, lineup and idempotency.

## Observable chain

Before H4, the dashboard and squad flows waited for a `careerStatus` subscription before sending the round command. A route or modal transition could recreate the subscription and restart the wait. H4 now warms status outside the click handler, reads a synchronous snapshot, and sends `POST /api/v1/career/{careerId}/next-round` immediately. A status request is only allowed after a rejected command or a real cache miss.

| Consumer | Moment | Uses snapshot | Forces HTTP | Invalidates | Blocks POST |
| --- | --- | ---: | ---: | --- | ---: |
| Dashboard | initial render | warm shared status | no, shared cache | no | no |
| Dashboard | Jugar Fecha N click | yes | no | no | no |
| Game detail | initial render | warm shared status | no, shared cache | no | no |
| Game detail | start click | yes, keyed by career | no | no | no |
| Squad | initial render | shared status and fixture warmup | no, shared cache | no | no |
| Squad | Confirmar y jugar | yes | only bounded fallback after lineup command | selective | no |
| Round live | route activation | optional status side-channel | no | no | no |
| Rejected command | after response | refreshed status | yes | `start-command-rejected` | outside critical path |
| Logout | account boundary | none | next screen may warm | clears all | not applicable |

## Critical path contract

1. Click is accepted and the duplicate-click guard is engaged.
2. The latest keyed snapshot is read synchronously when present.
3. Local validation uses identifiers already rendered by the screen.
4. The start command is sent immediately.
5. Backend validation is authoritative.
6. Navigation uses the command response; it never invents a round.
7. Status refresh happens after command success/rejection and is not awaited by the start command.

Fixtures use a 30-second completed-response snapshot keyed by round. The live route can render from navigation state and the fixture snapshot instead of rereading before the command.

## Remaining quantitative observation

One public warm trace was captured after the final deployment. It showed `statusWaitMs=0`, `fixturesWaitMs=0`, `statusHttpTriggeredByClick=false`, one start command and one first SSE. Its click-to-POST was 1312 ms, so the N=10 gate is not passed and the result is not promoted to a performance claim.

