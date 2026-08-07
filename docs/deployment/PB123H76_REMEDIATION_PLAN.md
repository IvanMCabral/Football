# PB1.2.3H7.6 — Remediation plan

## Scope

This local remediation closes the three P0 findings carried forward from the
H7.5 independent audit: productive career writes could mint or reuse a
generation implicitly, state/runtime writers had owner-only fallbacks, and a
world command could report success after snapshot persistence failed.

No gameplay rule, probability, fixture, calendar, dataset, frontend, public
provider, or remote database was changed.

## Implemented controls

- Initial career creation is explicit (`createInitialCareer`). Existing career
  updates require an exact `CareerWriteContext`.
- Career session saves capture ownership and generation inside the coordinator
  before writing.
- State, runtime, baseline, detailed-match, world and command persistence fail
  closed without lifecycle context.
- Match engine/session creation and round persistence require career ID and
  generation; legacy no-context entry points return a controlled error.
- Late callbacks carry the generation and are rejected by the coordinated
  ownership check after reset.
- League/world command failures propagate instead of being converted to an
  apparently successful completion.
- Interface default methods no longer delegate context-aware writes to
  tokenless methods.

## Operational boundary

The lifecycle coordinator is an in-process coordinator. H7.6 retains the
declared single-instance contract for staging. No distributed lock or
multi-instance guarantee is claimed.

## Validation boundary

Ephemeral local Redis integration is the source of Redis behavior evidence.
Mockito and static inspections are supplementary only. Render, Upstash, Neon,
Firebase and public data were not accessed in this local task.
