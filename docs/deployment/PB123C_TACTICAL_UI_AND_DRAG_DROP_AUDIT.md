# PB1.2.3C — Tactical UI and drag/drop audit

## Defect investigated

The production bundle previously read Angular CDK's private `_dragRef` field
from a `CdkDrag` event source. When the CDK internals did not expose that field,
marker drag handling threw a `TypeError` and the visible position could diverge
from the persisted lineup.

## Correction

Commit `96ea9ba` removes all `_dragRef` and `_pickupPositionInElement` reads. The
editor now consumes only the public `CdkDrag` surface (`element`, `data`, and
`reset`). Drop cleanup uses the public element's transform. Pointer offsets use a
stable marker-centre fallback because the offset is not a public CDK property.
No empty catch, compatibility shim, or private reflection was added.

## Automated coverage

`squad-editor-modal-drag.utils.spec.ts` verifies:

1. public element adaptation;
2. safe handling of missing drag sources;
3. public data/reset behaviour;
4. transform cleanup after a drop.

The existing squad-editor suite continues to cover formation remapping, lineup
mutation, goalkeeper protection, bench moves, rating previews and persistence.
The complete frontend suite passed with 1,042 successful tests, 0 failures and 2
skipped tests.

## Visual boundary

The available browser bridge could not attach to the user's real Chrome session;
therefore desktop (1920×1080), laptop (1366×768) and mobile (390×844) screenshots
were not newly captured. The public Firebase page itself returned HTTP 200 and
the SPA route redirected unauthenticated users to `/login` as expected.

The remaining visual gate is to repeat, in a connected real browser, the matrix:

- starter A → position B;
- A/B swap;
- starter → bench and bench → starter;
- formation change followed by drag;
- navigation/reload followed by drag;
- persistence comparison between rendered slots and `/career/lineup/current`.

No application `_dragRef` access remains in `src/`.
