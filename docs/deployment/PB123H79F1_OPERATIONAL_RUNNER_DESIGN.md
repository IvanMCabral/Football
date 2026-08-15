# PB1.2.3H7.9F.1 — World V2 one-shot runner design

## Scope and activation

This is a non-public process boundary for the previously certified, single-owner
World V2 canary. It is not an HTTP endpoint, scheduler, actuator operation,
generic migration tool, or normal application behavior. The operational beans
exist only with profile `world-v2-canary` and
`world.v2.canary.enabled=true`; the dedicated entry point also enforces both
conditions. A normal `FootballManagerApplication` context contains no runner,
source probe, Management API capacity provider, or certified-authority bean.

## Immutable certified authority

`WorldV2CanaryCertifiedAuthority` is immutable production code. Environment and
configuration cannot replace its owner hash, source SHA, semantic-plan SHA,
canonical fingerprint, quota ceiling, headroom floor, cushion floor, or storage
ceiling. The raw owner UUID remains runtime-only and is admitted only when its
SHA-256 equals the certified owner hash. Optional operator echo properties may
confirm authority but cannot define or replace it.

The semantic plan SHA is retained certified authority. Runtime proves the source
SHA, owner binding, source state, reference absence, catalog compatibility, and
catalog fingerprint; it does not claim to derive a new plan digest.

## Capacity contract

The provider adapter supplies only fresh `current_storage`. Safety constants are
not accepted from that provider response. Runtime configuration can tighten but
never weaken the certified contract:

- quota is capped at `268435456` bytes;
- required headroom is at least `2436344` bytes;
- retained cushion is at least `262144` bytes;
- admitted current storage is at most `265736968` bytes and at most the
  threshold derived from the effective quota, headroom, and cushion.

Invalid or impossible arithmetic fails closed.

## Execution boundary

The default is read-only `VALIDATE_ONLY`. Mutation requires raw exact mode
`EXECUTE` and raw exact confirmation `PB123H79F_ONE_OWNER_EXECUTE`; neither is
trimmed, case-folded, or otherwise normalized. An atomic boundary guard consumes
the runner on its first subscription, regardless of success or failure. Every
later call or subscription returns `RUNNER_ALREADY_INVOKED` before source,
capacity, or orchestration.

The reactive order is strictly source proof, fresh capacity sample, and then
immediate orchestration. The product migration CAS remains the write-time guard
against a source change after the preflight. There is no retry, loop, bulk mode,
or accepted already-migrated state in this runner.

## Exit and security contract

Exit code zero is limited to `VALIDATION_PASS` and `MIGRATED`. Every rejection,
provider error, capacity error, repeated invocation, partial result, source
change, or orchestration failure is non-zero. Logs contain only sanitized
results and the owner hash; raw owner and provider credentials are excluded.
Provider access remains GET-only.

No deploy, public canary, provider mutation, public Redis/PostgreSQL mutation,
cleanup, billing operation, or gameplay change is part of this design.
