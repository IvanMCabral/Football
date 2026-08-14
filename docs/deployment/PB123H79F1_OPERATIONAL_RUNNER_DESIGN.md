# PB1.2.3H7.9F.1 — World V2 one-shot runner design

## Scope

This document describes the non-public operational path introduced for the
single-owner World V2 canary. It is an administrative process boundary only;
it is not an HTTP endpoint, scheduled task, actuator operation, public route,
or normal application startup behavior.

## Activation contract

The process is active only when both conditions are true:

- profile `world-v2-canary` is active;
- `world.v2.canary.enabled=true` is set explicitly.

The dedicated entry point refuses to start without those conditions. The
normal `FootballManagerApplication` entry point is unchanged.

Required runtime material is supplied through environment/properties and is
validated before any source/provider call: one raw owner UUID, its expected
SHA-256 hash, source checksum, semantic-plan checksum, canonical fingerprint,
and a maximum admitted provider storage value. The raw owner is never logged.

## Read-only default and arming

The default mode is `VALIDATE_ONLY`. It performs the complete source and fresh
capacity admission checks and invokes the orchestrator zero times. Mutation is
available only in `EXECUTE` mode plus the exact confirmation value
`PB123H79F_ONE_OWNER_EXECUTE`; the confirmation is an operational guard, not a
credential. There is no fallback, owner discovery, bulk mode, pagination, loop,
or retry.

## Preconditions

The runner requires `LEGACY` state, the certified source checksum, exact owner
binding, no career/references, a compatible canonical fingerprint, and a fresh
Upstash Management API storage sample. The capacity contract checks quota,
required headroom, retained cushion, and the configured maximum threshold before
the product `WorldStorageMigrationOrchestrator` is called.

## Security boundaries

The provider adapter is infrastructure-only and uses runtime `UPSTASH_EMAIL`
and `UPSTASH_API_KEY` for HTTP Basic authentication. Credentials and raw
payloads are never logged or persisted. No Redis command is issued by the
runner itself. Process exit handling is isolated in the dedicated application
boundary; domain/application code does not call `System.exit`.

## Certified inputs retained from H7.9F

- owner hash: `6d963e62a2a6095b976ca78156a7ef0a`
- source SHA: `2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f`
- semantic plan SHA: `298b32c98e0052269896f3f9caa9a89e1f70e3fa15019ee061d7247c751ebc7a`
- canonical fingerprint: `1e654bec389796d232aba91685ac87d9ef1de08bcf3f5a7da563fdedbfb27000`
- quota: `268435456` bytes
- required headroom: `2436344` bytes
- retained cushion: `262144` bytes
- maximum admitted storage: `265736968` bytes

These values are authority inputs, not a current-storage snapshot. Current
storage is sampled immediately before orchestration.

## Explicit non-goals

This change does not execute the public canary, deploy Render, mutate public
Redis/PostgreSQL, create a catalog, alter gameplay, or change migration
semantics.
