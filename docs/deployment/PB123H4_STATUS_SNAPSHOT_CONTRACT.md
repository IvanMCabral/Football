# PB1.2.3H4 - Career status snapshot contract

## Ownership

`CareerService` owns a process-local UX snapshot for the authenticated account. Completed values are stored in a `Map<careerId, { value, receivedAt }>` so two careers cannot overwrite or leak one another. The snapshot is never used as authorization or as proof that a round may start.

## Lifetimes

- status request cache: 5 seconds;
- fixture completed-response cache: 30 seconds;
- status snapshot: retained as a last-known UX hint until selective replacement or logout;
- an expired or missing snapshot never blocks the start command.

The request cache uses `shareReplay({ bufferSize: 1, refCount: false })`, so a temporary subscriber change does not dispose the completed value.

## Invalidation

- `advanceToNextRound` invalidates the in-flight/request cache and patches the matching career snapshot from the command response when available;
- rejected commands trigger a refresh after the error path and record the rejection reason;
- unrelated career keys remain untouched;
- logout dispatches `manager:logout` and clears status and fixture caches;
- a new authenticated account cannot reuse the previous account's cache.

## Start behavior

The frontend sends the command with the career identifier already available to the screen. The backend validates existence, ownership, phase, round, lineup and idempotency. A 409/422 or equivalent validation error re-enables the UI and refreshes status outside the critical path.

