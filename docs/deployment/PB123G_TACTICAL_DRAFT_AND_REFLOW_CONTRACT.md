# PB1.2.3G — Tactical draft and reflow contract

## Contract

The formation selector in the visual editor is a local preview. It never calls
`manual-select` or `confirm` by itself. Closing, cancelling, reloading, or
logging out before confirmation therefore preserves the last confirmed lineup.
The footer action `Guardar y confirmar` performs one manual-select followed by
one confirm. An in-flight save is guarded so double clicks cannot create a
second write. Failed saves leave the confirmed state untouched.

## Deterministic reflow

Reflow uses a maximum-weight player/slot matching over the current XI. It
scores, in order: goalkeeper eligibility, role-family compatibility, line
compatibility, preserving the existing slot, and stable player/position order.
The objective first maximises the number of assigned players and then the
compatibility score. The goalkeeper is restricted to the protected goalkeeper
slot. Players not assignable to the target XI are moved to the bench with
placement coordinates cleared; no player is duplicated or silently promoted.

The matching is deterministic and has no randomness or index-only assignment.
It handles poly-players by their compatible family, keeps XI/bench disjoint,
and returns a controlled incomplete draft when the squad cannot fill eleven
slots.

## Observable states

The modal distinguishes saved, unsaved, saving, and error states. The draft
metrics are preview-only; the confirmed backend lineup changes only after the
explicit confirmation action.
